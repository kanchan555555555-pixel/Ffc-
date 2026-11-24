package com.example.network

import android.content.Context
import android.graphics.Bitmap
import com.example.data.FormatUtils
import com.example.model.DiscoveredTvDevice
import com.example.model.SharedFileItem
import com.example.model.TransferRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class ServerTelemetry(
    val activeClients: Int = 0,
    val currentSpeedBytesPerSec: Long = 0L,
    val totalBytesSent: Long = 0L
)

data class FileTransferProgress(
    val fileId: String,
    val bytesTransferredDelta: Long,
    val completedIncrement: Int,
    val activeDelta: Int
)

class HttpFileServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var scope: CoroutineScope? = null
    private var speedJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    var deviceName: String = "TV Beam Mobile"

    @Volatile
    var pinEnabled: Boolean = false

    @Volatile
    var pinCode: String = "4829"

    private val filesMap = ConcurrentHashMap<String, SharedFileItem>()
    private val orderedIds = CopyOnWriteArrayList<String>()

    // SHAREit-style instant push queue for connected TV browsers
    private val pushSequence = AtomicLong(0L)
    private val pushedFileIds = CopyOnWriteArrayList<String>()

    // Connected TV browsers polling /api/files
    private val browserClients = ConcurrentHashMap<String, DiscoveredTvDevice>()

    private val activeConnections = AtomicInteger(0)
    private val totalBytesCounter = AtomicLong(0L)
    private val windowBytesCounter = AtomicLong(0L)

    private val _telemetry = MutableStateFlow(ServerTelemetry())
    val telemetry: StateFlow<ServerTelemetry> = _telemetry.asStateFlow()

    private val _connectedBrowsers = MutableStateFlow<List<DiscoveredTvDevice>>(emptyList())
    val connectedBrowsers: StateFlow<List<DiscoveredTvDevice>> = _connectedBrowsers.asStateFlow()

    private val _fileProgressEvents = MutableSharedFlow<FileTransferProgress>(extraBufferCapacity = 64)
    val fileProgressEvents: SharedFlow<FileTransferProgress> = _fileProgressEvents.asSharedFlow()

    private val _transferRecords = MutableSharedFlow<TransferRecord>(extraBufferCapacity = 32)
    val transferRecords: SharedFlow<TransferRecord> = _transferRecords.asSharedFlow()

    fun updateSharedFiles(items: List<SharedFileItem>) {
        val incomingIds = items.map { it.id }.toSet()
        filesMap.keys.retainAll(incomingIds)
        orderedIds.clear()
        for (item in items) {
            filesMap[item.id] = item
            orderedIds.add(item.id)
        }
    }

    fun queuePushToTv(fileIds: List<String>) {
        if (fileIds.isEmpty()) return
        pushedFileIds.clear()
        pushedFileIds.addAll(fileIds)
        pushSequence.incrementAndGet()
    }

    fun start(port: Int, onStarted: (Boolean, String?) -> Unit) {
        if (isRunning) {
            onStarted(true, null)
            return
        }
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        executor = Executors.newCachedThreadPool()

        newScope.launch {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.receiveBufferSize = 256 * 1024
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 32)
                serverSocket = socket
                isRunning = true

                startSpeedMonitor(newScope)
                onStarted(true, null)

                while (isActive && isRunning) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    executor?.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                isRunning = false
                onStarted(false, e.localizedMessage ?: "Failed to bind port $port")
            }
        }
    }

    private fun startSpeedMonitor(targetScope: CoroutineScope) {
        speedJob?.cancel()
        speedJob = targetScope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive && isRunning) {
                delay(600L)
                val now = System.currentTimeMillis()
                val elapsedMs = (now - lastTime).coerceAtLeast(1L)
                val deltaBytes = windowBytesCounter.getAndSet(0L)
                val speed = (deltaBytes * 1000L) / elapsedMs
                _telemetry.value = ServerTelemetry(
                    activeClients = activeConnections.get(),
                    currentSpeedBytesPerSec = speed,
                    totalBytesSent = totalBytesCounter.get()
                )

                // Expire browser clients not seen in > 8 seconds
                val stale = browserClients.entries
                    .filter { now - it.value.lastSeenMs > 8000L }
                    .map { it.key }
                if (stale.isNotEmpty()) {
                    stale.forEach { browserClients.remove(it) }
                    _connectedBrowsers.value = browserClients.values.toList()
                }
                lastTime = now
            }
        }
    }

    fun stop() {
        isRunning = false
        speedJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {
        }
        executor = null
        scope?.cancel()
        scope = null
        activeConnections.set(0)
        windowBytesCounter.set(0L)
        browserClients.clear()
        _connectedBrowsers.value = emptyList()
        _telemetry.value = _telemetry.value.copy(activeClients = 0, currentSpeedBytesPerSec = 0L)
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "TV"
        try {
            socket.tcpNoDelay = true
            socket.sendBufferSize = 512 * 1024
            socket.soTimeout = 30_000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullTarget = parts[1]

            val headers = HashMap<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                sendSimpleResponse(socket, "405 Method Not Allowed", "text/plain", "Method Not Allowed")
                return
            }

            val pathOnly = fullTarget.substringBefore('?')
            val queryParams = parseQuery(fullTarget.substringAfter('?', ""))
            val providedPin = queryParams["pin"] ?: ""

            when {
                pathOnly == "/" || pathOnly == "/index.html" -> {
                    registerBrowserClient(clientIp, headers["user-agent"])
                    val html = TvWebPortalHtml.render(deviceName, pinEnabled)
                    sendSimpleResponse(socket, "200 OK", "text/html; charset=utf-8", html, method == "HEAD")
                }

                pathOnly == "/api/auth" -> {
                    val ok = !pinEnabled || providedPin == pinCode
                    sendSimpleResponse(
                        socket,
                        "200 OK",
                        "application/json; charset=utf-8",
                        """{"ok":$ok}""",
                        method == "HEAD"
                    )
                }

                pathOnly == "/api/files" -> {
                    if (pinEnabled && providedPin != pinCode) {
                        sendSimpleResponse(socket, "401 Unauthorized", "application/json", """{"error":"pin_required"}""")
                        return
                    }
                    registerBrowserClient(clientIp, headers["user-agent"])
                    val json = buildFilesJson()
                    sendSimpleResponse(socket, "200 OK", "application/json; charset=utf-8", json, method == "HEAD")
                }

                pathOnly.startsWith("/thumb/") -> {
                    if (pinEnabled && providedPin != pinCode) {
                        sendSimpleResponse(socket, "401 Unauthorized", "text/plain", "Unauthorized")
                        return
                    }
                    val id = pathOnly.removePrefix("/thumb/")
                    val item = filesMap[id]
                    val thumb = item?.thumbnail
                    if (thumb == null) {
                        sendSimpleResponse(socket, "404 Not Found", "text/plain", "No thumb")
                    } else {
                        val baos = ByteArrayOutputStream()
                        thumb.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                        val bytes = baos.toByteArray()
                        val out = BufferedOutputStream(socket.getOutputStream())
                        val header = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: image/jpeg\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Cache-Control: max-age=3600\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        out.write(header.toByteArray(Charsets.UTF_8))
                        if (method != "HEAD") out.write(bytes)
                        out.flush()
                    }
                }

                pathOnly.startsWith("/download/") || pathOnly.startsWith("/stream/") -> {
                    if (pinEnabled && providedPin != pinCode) {
                        sendSimpleResponse(socket, "401 Unauthorized", "text/plain", "PIN required")
                        return
                    }
                    val isDownload = pathOnly.startsWith("/download/")
                    val id = pathOnly.removePrefix(if (isDownload) "/download/" else "/stream/")
                    val item = filesMap[id]
                    if (item == null) {
                        sendSimpleResponse(socket, "404 Not Found", "text/plain", "File not found")
                        return
                    }
                    streamFileToTv(
                        socket = socket,
                        item = item,
                        clientIp = clientIp,
                        rangeHeader = headers["range"],
                        asAttachment = isDownload,
                        headOnly = method == "HEAD"
                    )
                }

                else -> {
                    sendSimpleResponse(socket, "404 Not Found", "text/plain", "Not Found")
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun registerBrowserClient(clientIp: String, userAgent: String?) {
        val ua = userAgent ?: ""
        val label = when {
            ua.contains("Tizen", ignoreCase = true) -> "Samsung TV Browser"
            ua.contains("Web0S", ignoreCase = true) || ua.contains("webOS", ignoreCase = true) -> "LG TV Browser"
            ua.contains("BRAVIA", ignoreCase = true) -> "Sony Bravia Browser"
            ua.contains("AFT", ignoreCase = true) -> "Fire TV Silk"
            ua.contains("Android TV", ignoreCase = true) || ua.contains("TV", ignoreCase = true) -> "Android TV Browser"
            else -> "TV / Receiver Browser"
        }
        val dev = DiscoveredTvDevice(
            id = "browser_$clientIp",
            name = label,
            ipAddress = clientIp,
            subtitle = "Auto-Save Ready • $clientIp",
            isBrowserConnected = true,
            lastSeenMs = System.currentTimeMillis()
        )
        val previous = browserClients.put(clientIp, dev)
        if (previous == null || previous.name != dev.name) {
            _connectedBrowsers.value = browserClients.values.toList()
        }
    }

    private fun streamFileToTv(
        socket: Socket,
        item: SharedFileItem,
        clientIp: String,
        rangeHeader: String?,
        asAttachment: Boolean,
        headOnly: Boolean
    ) {
        val totalSize = item.sizeBytes
        var startByte = 0L
        var endByte = if (totalSize > 0L) totalSize - 1L else -1L
        var isRangeRequest = false

        if (!rangeHeader.isNullOrBlank() && totalSize > 0L && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
            val dash = rangeSpec.indexOf('-')
            if (dash >= 0) {
                val startStr = rangeSpec.substring(0, dash).trim()
                val endStr = rangeSpec.substring(dash + 1).trim()
                val parsedStart = startStr.toLongOrNull()
                val parsedEnd = endStr.toLongOrNull()
                if (parsedStart != null) {
                    startByte = parsedStart.coerceIn(0L, totalSize - 1L)
                    endByte = (parsedEnd ?: (totalSize - 1L)).coerceIn(startByte, totalSize - 1L)
                    isRangeRequest = true
                } else if (parsedEnd != null) {
                    val suffixLen = parsedEnd.coerceAtMost(totalSize)
                    startByte = (totalSize - suffixLen).coerceAtLeast(0L)
                    endByte = totalSize - 1L
                    isRangeRequest = true
                }
            }
        }

        val contentLength = if (totalSize > 0L) (endByte - startByte + 1L) else -1L
        val status = if (isRangeRequest) "206 Partial Content" else "200 OK"
        val encodedName = URLEncoder.encode(item.name, "UTF-8").replace("+", "%20")
        val asciiFallback = item.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val contentType = if (asAttachment) "application/octet-stream" else item.mimeType

        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (contentLength >= 0L) {
                append("Content-Length: $contentLength\r\n")
            }
            if (isRangeRequest && totalSize > 0L) {
                append("Content-Range: bytes $startByte-$endByte/$totalSize\r\n")
            }
            if (asAttachment) {
                append("Content-Disposition: attachment; filename=\"$asciiFallback\"; filename*=UTF-8''$encodedName\r\n")
            } else {
                append("Content-Disposition: inline; filename=\"$asciiFallback\"\r\n")
            }
            append("Connection: close\r\n\r\n")
        }

        val out = BufferedOutputStream(socket.getOutputStream(), 256 * 1024)
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.flush()

        if (headOnly) return

        activeConnections.incrementAndGet()
        _fileProgressEvents.tryEmit(FileTransferProgress(item.id, 0L, 0, +1))
        val transferStartMs = System.currentTimeMillis()
        var bytesWrittenThisRequest = 0L

        try {
            val rawInput = context.contentResolver.openInputStream(item.uri)
                ?: return
            BufferedInputStream(rawInput, 256 * 1024).use { input ->
                if (startByte > 0L) {
                    skipFully(input, startByte)
                }
                val buffer = ByteArray(256 * 1024)
                var remaining = if (contentLength >= 0L) contentLength else Long.MAX_VALUE
                while (remaining > 0L && isRunning) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    bytesWrittenThisRequest += read
                    remaining -= read
                    totalBytesCounter.addAndGet(read.toLong())
                    windowBytesCounter.addAndGet(read.toLong())
                    _fileProgressEvents.tryEmit(
                        FileTransferProgress(item.id, read.toLong(), 0, 0)
                    )
                }
                out.flush()
            }

            val completedFull = (totalSize <= 0L && bytesWrittenThisRequest > 0L) ||
                (!isRangeRequest && bytesWrittenThisRequest >= totalSize) ||
                (isRangeRequest && endByte >= totalSize - 1L && bytesWrittenThisRequest >= contentLength)

            if (completedFull && asAttachment) {
                _fileProgressEvents.tryEmit(FileTransferProgress(item.id, 0L, 1, 0))
            }

            if (bytesWrittenThisRequest > 0L) {
                val elapsedSec = ((System.currentTimeMillis() - transferStartMs).coerceAtLeast(100L)) / 1000.0
                val avgSpeed = (bytesWrittenThisRequest / elapsedSec).toLong()
                _transferRecords.tryEmit(
                    TransferRecord(
                        id = UUID.randomUUID().toString(),
                        fileName = item.name,
                        sizeBytes = bytesWrittenThisRequest,
                        clientIp = clientIp,
                        timestampMs = System.currentTimeMillis(),
                        speedBytesPerSec = avgSpeed,
                        completed = completedFull && asAttachment
                    )
                )
            }
        } catch (_: Exception) {
        } finally {
            activeConnections.decrementAndGet()
            _fileProgressEvents.tryEmit(FileTransferProgress(item.id, 0L, 0, -1))
        }
    }

    private fun skipFully(input: BufferedInputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(64 * 1024)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val r = input.read(skipBuf, 0, minOf(skipBuf.size.toLong(), remaining).toInt())
                if (r <= 0) break
                remaining -= r
            }
        }
    }

    private fun sendSimpleResponse(
        socket: Socket,
        status: String,
        contentType: String,
        body: String,
        headOnly: Boolean = false
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val out = BufferedOutputStream(socket.getOutputStream())
        val header = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        if (!headOnly) out.write(bodyBytes)
        out.flush()
    }

    private fun buildFilesJson(): String {
        val items = orderedIds.mapNotNull { filesMap[it] }
        val seq = pushSequence.get()
        val pushed = pushedFileIds.toList()
        return buildString {
            append('{')
            append("\"pushSeq\":$seq,")
            append("\"pushedIds\":[")
            pushed.forEachIndexed { idx, pid ->
                if (idx > 0) append(',')
                append("\"${escapeJson(pid)}\"")
            }
            append("],")
            append("\"files\":[")
            items.forEachIndexed { index, item ->
                if (index > 0) append(',')
                val dur = FormatUtils.formatDuration(item.durationMs) ?: ""
                append('{')
                append("\"id\":\"${escapeJson(item.id)}\",")
                append("\"name\":\"${escapeJson(item.name)}\",")
                append("\"sizeBytes\":${item.sizeBytes},")
                append("\"sizeText\":\"${escapeJson(FormatUtils.formatBytes(item.sizeBytes))}\",")
                append("\"category\":\"${item.category.name}\",")
                append("\"duration\":\"${escapeJson(dur)}\",")
                append("\"hasThumb\":${item.thumbnail != null},")
                append("\"downloads\":${item.downloadCount}")
                append('}')
            }
            append("]}")
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
