package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.model.LocalHotspotInfo
import com.example.model.NetworkInterfaceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkDiscoveryHelper(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    private val _localHotspotState = MutableStateFlow(LocalHotspotInfo())
    val localHotspotState: StateFlow<LocalHotspotInfo> = _localHotspotState.asStateFlow()

    fun scanNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val results = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val name = iface.name.lowercase()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val isHotspot = name.contains("ap") ||
                            name.contains("swlan") ||
                            name.contains("softap") ||
                            name.contains("wlan1") ||
                            name.contains("rndis") ||
                            ip.startsWith("192.168.43.") ||
                            ip.startsWith("192.168.49.") ||
                            ip.startsWith("192.168.137.")
                        results.add(
                            NetworkInterfaceInfo(
                                interfaceName = iface.name,
                                ipAddress = ip,
                                isHotspotLikely = isHotspot
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Prioritize Hotspot interfaces first, then wlan0, then any other private IPv4
        return results.sortedWith(
            compareByDescending<NetworkInterfaceInfo> { it.isHotspotLikely }
                .thenByDescending { it.interfaceName.startsWith("wlan") }
                .thenBy { it.interfaceName }
        )
    }

    @SuppressLint("MissingPermission")
    fun startLocalOnlyHotspot(onResult: (Boolean) -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _localHotspotState.value = LocalHotspotInfo(
                isActive = false,
                errorMessage = "Instant Hotspot requires Android 8.0+. Use Phone Hotspot Settings instead."
            )
            onResult(false)
            return
        }

        if (hotspotReservation != null && _localHotspotState.value.isActive) {
            onResult(true)
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        hotspotReservation = reservation
                        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            reservation?.softApConfiguration
                        } else {
                            null
                        }
                        @Suppress("DEPRECATION")
                        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            config?.ssid ?: "TVBeam_Hotspot"
                        } else {
                            reservation?.wifiConfiguration?.SSID ?: "TVBeam_Hotspot"
                        }
                        @Suppress("DEPRECATION")
                        val pass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            config?.passphrase ?: ""
                        } else {
                            reservation?.wifiConfiguration?.preSharedKey ?: ""
                        }
                        _localHotspotState.value = LocalHotspotInfo(
                            isActive = true,
                            ssid = ssid.removeSurrounding("\""),
                            passphrase = pass.removeSurrounding("\""),
                            errorMessage = null
                        )
                        onResult(true)
                    }

                    override fun onStopped() {
                        hotspotReservation = null
                        _localHotspotState.value = LocalHotspotInfo(isActive = false)
                    }

                    override fun onFailed(reason: Int) {
                        hotspotReservation = null
                        val reasonText = when (reason) {
                            ERROR_TETHERING_DISALLOWED -> "Tethering disallowed on this device. Open Phone Hotspot Settings."
                            ERROR_INCOMPATIBLE_MODE -> "Turn off regular Wi-Fi/Hotspot first or use Phone Hotspot Settings."
                            ERROR_NO_CHANNEL -> "No Wi-Fi channel available right now."
                            else -> "Could not start Instant Hotspot (code $reason). Use Phone Hotspot Settings."
                        }
                        _localHotspotState.value = LocalHotspotInfo(
                            isActive = false,
                            errorMessage = reasonText
                        )
                        onResult(false)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (se: SecurityException) {
            _localHotspotState.value = LocalHotspotInfo(
                isActive = false,
                errorMessage = se.localizedMessage ?: "Permission required for Instant Hotspot"
            )
            onResult(false)
        } catch (e: Exception) {
            _localHotspotState.value = LocalHotspotInfo(
                isActive = false,
                errorMessage = e.localizedMessage ?: "Failed to start Instant Hotspot"
            )
            onResult(false)
        }
    }

    fun stopLocalOnlyHotspot() {
        try {
            hotspotReservation?.close()
        } catch (_: Exception) {
        }
        hotspotReservation = null
        _localHotspotState.value = LocalHotspotInfo(isActive = false)
    }

    fun openSystemHotspotSettings() {
        val candidates = listOf(
            Intent().apply {
                setClassName(
                    "com.android.settings",
                    "com.android.settings.TetherSettings"
                )
            },
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun registerNsdService(serviceName: String, port: Int) {
        unregisterNsdService()
        val manager = nsdManager ?: return
        try {
            val info = NsdServiceInfo().apply {
                this.serviceName = serviceName.take(32).ifBlank { "TVBeam" }
                this.serviceType = "_http._tcp."
                this.port = port
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegistrationListener = listener
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    fun unregisterNsdService() {
        val listener = nsdRegistrationListener ?: return
        nsdRegistrationListener = null
        try {
            nsdManager?.unregisterService(listener)
        } catch (_: Exception) {
        }
    }
}
