package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.model.DiscoveredTvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class TvRadarDiscovery(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    suspend fun scanForTvs(localIps: List<String>): List<DiscoveredTvDevice> = withContext(Dispatchers.IO) {
        val found = ConcurrentHashMap<String, DiscoveredTvDevice>()
        val multicastLock = try {
            wifiManager.createMulticastLock("TVBeam::RadarScan").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            null
        }

        try {
            coroutineScope {
                val ssdpJob = async {
                    discoverViaSsdp().forEach { dev ->
                        found[dev.ipAddress] = dev
                    }
                }
                val nsdJob = async {
                    discoverViaNsd().forEach { dev ->
                        found.putIfAbsent(dev.ipAddress, dev)
                    }
                }
                val subnetJob = async {
                    probeHotspotSubnet(localIps).forEach { dev ->
                        found.putIfAbsent(dev.ipAddress, dev)
                    }
                }
                awaitAll(ssdpJob, nsdJob, subnetJob)
            }
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock.release()
            } catch (_: Exception) {
            }
        }

        found.values.sortedByDescending { it.supportsDlnaPush }
    }

    private suspend fun discoverViaSsdp(): List<DiscoveredTvDevice> = coroutineScope {
        val locations = LinkedHashSet<String>()
        val queries = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:dial-multiscreen-org:service:dial:1",
            "ssdp:all"
        )
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1300
                socket.broadcast = true
                val group = InetAddress.getByName("239.255.255.250")
                for (st in queries) {
                    val msg = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: 239.255.255.250:1900\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("MX: 1\r\n")
                        append("ST: $st\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(msg, msg.size, group, 1900))
                }

                val deadline = System.currentTimeMillis() + 1800L
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                        val resp = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val locLine = resp.lines()
                            .firstOrNull { it.trim().startsWith("LOCATION:", ignoreCase = true) }
                        val loc = locLine?.substringAfter(':')?.trim()
                        if (!loc.isNullOrBlank() && loc.startsWith("http")) {
                            locations.add(loc)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
        }

        locations.take(12).map { loc ->
            async { parseUpnpDeviceDescription(loc) }
        }.awaitAll().filterNotNull()
    }

    private fun parseUpnpDeviceDescription(locationUrl: String): DiscoveredTvDevice? {
        return try {
            val url = URL(locationUrl)
            val ip = url.host ?: return null
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 1200
            }
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val friendlyName = extractXmlTag(xml, "friendlyName") ?: "Smart TV ($ip)"
            val modelName = extractXmlTag(xml, "modelName") ?: ""
            val manufacturer = extractXmlTag(xml, "manufacturer") ?: ""

            // Find AVTransport control URL if TV supports DLNA MediaRenderer push
            val avTransportUrl = extractAvTransportControlUrl(xml, url)
            val isTvOrRenderer = xml.contains("MediaRenderer", ignoreCase = true) ||
                xml.contains("AVTransport", ignoreCase = true) ||
                xml.contains("dial", ignoreCase = true) ||
                friendlyName.contains("TV", ignoreCase = true) ||
                friendlyName.contains("Cast", ignoreCase = true) ||
                friendlyName.contains("Bravia", ignoreCase = true) ||
                friendlyName.contains("Samsung", ignoreCase = true) ||
                friendlyName.contains("LG", ignoreCase = true)

            if (!isTvOrRenderer) return null

            val sub = listOf(manufacturer, modelName).filter { it.isNotBlank() }.joinToString(" • ")
                .ifBlank { ip }

            DiscoveredTvDevice(
                id = "ssdp_$ip",
                name = friendlyName,
                ipAddress = ip,
                subtitle = sub,
                avTransportControlUrl = avTransportUrl,
                supportsDlnaPush = avTransportUrl != null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAvTransportControlUrl(xml: String, baseUrl: URL): String? {
        val serviceRegex = Regex("<service>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (match in serviceRegex.findAll(xml)) {
            val block = match.groupValues[1]
            if (block.contains("AVTransport", ignoreCase = true)) {
                val ctrl = extractXmlTag(block, "controlURL") ?: continue
                return try {
                    URL(baseUrl, ctrl).toString()
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = Regex("<$tag>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private suspend fun discoverViaNsd(): List<DiscoveredTvDevice> {
        val manager = nsdManager ?: return emptyList()
        val results = ConcurrentHashMap<String, DiscoveredTvDevice>()
        val types = listOf("_googlecast._tcp.", "_androidtvremote2._tcp.")
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        try {
            for (serviceType in types) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onDiscoveryStarted(serviceType: String?) {}
                    override fun onDiscoveryStopped(serviceType: String?) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        try {
                            @Suppress("DEPRECATION")
                            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(si: NsdServiceInfo?, errorCode: Int) {}
                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val ip = resolved.host?.hostAddress ?: return
                                    val name = resolved.serviceName.ifBlank { "Android TV" }
                                    results[ip] = DiscoveredTvDevice(
                                        id = "nsd_$ip",
                                        name = name,
                                        ipAddress = ip,
                                        subtitle = "Android / Google TV • $ip",
                                        supportsDlnaPush = false
                                    )
                                }
                            })
                        } catch (_: Exception) {
                        }
                    }
                }
                listeners.add(listener)
                manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }
            kotlinx.coroutines.delay(1400L)
        } catch (_: Exception) {
        } finally {
            for (l in listeners) {
                try {
                    manager.stopServiceDiscovery(l)
                } catch (_: Exception) {
                }
            }
        }
        return results.values.toList()
    }

    private suspend fun probeHotspotSubnet(localIps: List<String>): List<DiscoveredTvDevice> = coroutineScope {
        val targetPrefixes = localIps
            .mapNotNull { ip ->
                val parts = ip.split('.')
                if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" to ip else null
            }
            .distinctBy { it.first }
            .take(2)

        if (targetPrefixes.isEmpty()) return@coroutineScope emptyList()

        val commonTvPorts = listOf(8008, 8009, 7676, 5555)
        val candidates = mutableListOf<String>()
        for ((prefix, selfIp) in targetPrefixes) {
            for (host in 1..254) {
                val candidate = "$prefix.$host"
                if (candidate != selfIp) candidates.add(candidate)
            }
        }

        // Batch scan quickly with short 140ms socket timeouts
        val discovered = ConcurrentHashMap<String, DiscoveredTvDevice>()
        candidates.chunked(48).forEach { chunk ->
            chunk.map { ip ->
                async {
                    for (port in commonTvPorts) {
                        if (isTcpOpen(ip, port, 140)) {
                            val label = when (port) {
                                8008, 8009 -> "Smart / Cast TV ($ip)"
                                7676 -> "Samsung / Smart TV ($ip)"
                                5555 -> "Android TV ($ip)"
                                else -> "TV Device ($ip)"
                            }
                            discovered[ip] = DiscoveredTvDevice(
                                id = "probe_$ip",
                                name = label,
                                ipAddress = ip,
                                subtitle = "Hotspot Neighbor • $ip"
                            )
                            break
                        }
                    }
                }
            }.awaitAll()
        }
        discovered.values.toList()
    }

    private fun isTcpOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pushMediaToDlnaTv(
        controlUrl: String,
        mediaUrl: String,
        title: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val escapedUrl = escapeXml(mediaUrl)
            val escapedTitle = escapeXml(title)
            val upnpClass = when {
                mimeType.startsWith("video/") -> "object.item.videoItem"
                mimeType.startsWith("image/") -> "object.item.imageItem"
                mimeType.startsWith("audio/") -> "object.item.audioItem"
                else -> "object.item.videoItem"
            }
            val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>$escapedTitle</dc:title><upnp:class>$upnpClass</upnp:class><res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res></item></DIDL-Lite>"""

            val setUriSoap = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <InstanceID>0</InstanceID>
                      <CurrentURI>$escapedUrl</CurrentURI>
                      <CurrentURIMetaData>${escapeXml(didl)}</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            val setOk = postSoap(controlUrl, "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", setUriSoap)
            if (!setOk) return@withContext false

            val playSoap = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <InstanceID>0</InstanceID>
                      <Speed>1</Speed>
                    </u:Play>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()
            postSoap(controlUrl, "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"", playSoap)
        } catch (_: Exception) {
            false
        }
    }

    private fun postSoap(urlStr: String, soapAction: String, body: String): Boolean {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2000
            readTimeout = 2000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", soapAction)
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(body)
            it.flush()
        }
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
