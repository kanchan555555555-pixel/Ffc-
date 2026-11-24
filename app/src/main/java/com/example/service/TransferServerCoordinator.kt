package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.UserSettings
import com.example.model.DiscoveredTvDevice
import com.example.model.FileCategory
import com.example.model.ServerState
import com.example.model.SharedFileItem
import com.example.model.TransferRecord
import com.example.network.HttpFileServer
import com.example.network.NetworkDiscoveryHelper
import com.example.network.TvRadarDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TransferServerCoordinator private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val discoveryHelper = NetworkDiscoveryHelper(appContext)
    val httpServer = HttpFileServer(appContext)
    val radarDiscovery = TvRadarDiscovery(appContext)

    private val _files = MutableStateFlow<List<SharedFileItem>>(emptyList())
    val files: StateFlow<List<SharedFileItem>> = _files.asStateFlow()

    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferRecord>>(emptyList())
    val transferHistory: StateFlow<List<TransferRecord>> = _transferHistory.asStateFlow()

    private var networkScannedTvs: List<DiscoveredTvDevice> = emptyList()
    private var browserConnectedTvs: List<DiscoveredTvDevice> = emptyList()

    var onBytesCommitted: ((Long) -> Unit)? = null

    init {
        refreshNetworkInterfaces()

        scope.launch {
            discoveryHelper.localHotspotState.collect { hotspot ->
                _serverState.update { it.copy(localHotspot = hotspot) }
                delay(400L)
                refreshNetworkInterfaces()
                if (hotspot.isActive) {
                    scanRadarForTvs()
                }
            }
        }

        scope.launch {
            httpServer.telemetry.collect { telemetry ->
                _serverState.update {
                    it.copy(
                        activeClients = telemetry.activeClients,
                        currentSpeedBytesPerSec = telemetry.currentSpeedBytesPerSec,
                        totalBytesSent = telemetry.totalBytesSent
                    )
                }
            }
        }

        scope.launch {
            httpServer.connectedBrowsers.collect { browsers ->
                browserConnectedTvs = browsers
                mergeDiscoveredTvs()
            }
        }

        scope.launch {
            httpServer.fileProgressEvents.collect { ev ->
                _files.update { list ->
                    list.map { item ->
                        if (item.id == ev.fileId) {
                            item.copy(
                                bytesTransferred = if (ev.bytesTransferredDelta > 0L) {
                                    item.bytesTransferred + ev.bytesTransferredDelta
                                } else item.bytesTransferred,
                                downloadCount = item.downloadCount + ev.completedIncrement,
                                activeConnections = (item.activeConnections + ev.activeDelta).coerceAtLeast(0)
                            )
                        } else item
                    }
                }
                if (ev.bytesTransferredDelta > 0L) {
                    onBytesCommitted?.invoke(ev.bytesTransferredDelta)
                }
            }
        }

        scope.launch {
            httpServer.transferRecords.collect { record ->
                _transferHistory.update { list ->
                    (listOf(record) + list).take(50)
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(3000L)
                if (_serverState.value.isRunning) {
                    refreshNetworkInterfaces()
                }
            }
        }
    }

    private fun mergeDiscoveredTvs() {
        val merged = LinkedHashMap<String, DiscoveredTvDevice>()
        // Connected TV browsers have highest priority
        browserConnectedTvs.forEach { dev ->
            merged[dev.ipAddress] = dev
        }
        networkScannedTvs.forEach { dev ->
            val existing = merged[dev.ipAddress]
            if (existing != null) {
                merged[dev.ipAddress] = existing.copy(
                    avTransportControlUrl = dev.avTransportControlUrl,
                    supportsDlnaPush = dev.supportsDlnaPush,
                    name = if (dev.name.isNotBlank() && !dev.name.startsWith("TV Device")) dev.name else existing.name
                )
            } else {
                merged[dev.ipAddress] = dev
            }
        }
        val list = merged.values.toList()
        val currentSelected = _serverState.value.selectedTvId
        val nextSelected = if (list.any { it.id == currentSelected }) {
            currentSelected
        } else {
            list.firstOrNull()?.id
        }
        _serverState.update {
            it.copy(
                discoveredTvs = list,
                selectedTvId = nextSelected
            )
        }
    }

    fun scanRadarForTvs() {
        if (_serverState.value.isRadarScanning) return
        refreshNetworkInterfaces()
        scope.launch {
            _serverState.update { it.copy(isRadarScanning = true) }
            try {
                val localIps = _serverState.value.interfaces.map { it.ipAddress }
                networkScannedTvs = radarDiscovery.scanForTvs(localIps)
                mergeDiscoveredTvs()
            } finally {
                _serverState.update { it.copy(isRadarScanning = false) }
            }
        }
    }

    fun selectTv(tvId: String) {
        _serverState.update { it.copy(selectedTvId = tvId) }
    }

    fun pushFilesToTv(fileIds: List<String>, onDlnaFeedback: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (fileIds.isEmpty()) return
        if (!_serverState.value.isRunning) {
            startServer()
        }
        httpServer.queuePushToTv(fileIds)
        _files.update { list ->
            list.map { item ->
                if (item.id in fileIds) item.copy(pushQueuedCount = item.pushQueuedCount + 1) else item
            }
        }

        // Also push via DLNA AVTransport if a DLNA-capable TV is selected & first item is media
        val selectedTv = _serverState.value.discoveredTvs.firstOrNull {
            it.id == _serverState.value.selectedTvId
        }
        val ctrlUrl = selectedTv?.avTransportControlUrl
        val serverUrl = _serverState.value.serverUrl
        val firstMedia = _files.value.firstOrNull {
            it.id in fileIds &&
                (it.category == FileCategory.VIDEO || it.category == FileCategory.PHOTO || it.category == FileCategory.AUDIO)
        }

        if (ctrlUrl != null && serverUrl != null && firstMedia != null) {
            scope.launch {
                val pinQuery = if (_serverState.value.pinEnabled) "?pin=${_serverState.value.pinCode}" else ""
                val mediaUrl = "$serverUrl/stream/${firstMedia.id}$pinQuery"
                val ok = radarDiscovery.pushMediaToDlnaTv(
                    controlUrl = ctrlUrl,
                    mediaUrl = mediaUrl,
                    title = firstMedia.name,
                    mimeType = firstMedia.mimeType
                )
                onDlnaFeedback(ok, selectedTv.name)
            }
        }
    }

    fun applySettings(settings: UserSettings) {
        val portChanged = _serverState.value.port != settings.port
        httpServer.deviceName = settings.deviceName
        httpServer.pinEnabled = settings.pinEnabled
        httpServer.pinCode = settings.pinCode
        _serverState.update {
            it.copy(
                port = settings.port,
                deviceName = settings.deviceName,
                pinEnabled = settings.pinEnabled,
                pinCode = settings.pinCode
            )
        }
        if (portChanged && _serverState.value.isRunning) {
            stopServer()
            startServer()
        }
    }

    fun refreshNetworkInterfaces() {
        val list = discoveryHelper.scanNetworkInterfaces()
        val bestIp = list.firstOrNull()?.ipAddress
        _serverState.update {
            it.copy(
                interfaces = list,
                primaryIp = bestIp
            )
        }
    }

    fun addFiles(newItems: List<SharedFileItem>) {
        if (newItems.isEmpty()) return
        _files.update { existing ->
            val existingUris = existing.map { it.uri }.toHashSet()
            val filtered = newItems.filter { it.uri !in existingUris }
            val updated = filtered + existing
            httpServer.updateSharedFiles(updated)
            updated
        }
        if (!_serverState.value.isRunning) {
            startServer()
        }
    }

    fun removeFile(fileId: String) {
        _files.update { existing ->
            val updated = existing.filterNot { it.id == fileId }
            httpServer.updateSharedFiles(updated)
            updated
        }
    }

    fun clearAllFiles() {
        _files.value = emptyList()
        httpServer.updateSharedFiles(emptyList())
    }

    fun startServer() {
        refreshNetworkInterfaces()
        val port = _serverState.value.port
        httpServer.updateSharedFiles(_files.value)
        httpServer.start(port) { ok, err ->
            scope.launch {
                if (ok) {
                    _serverState.update { it.copy(isRunning = true, errorMessage = null) }
                    discoveryHelper.registerNsdService(_serverState.value.deviceName, port)
                    startForegroundServiceIntent()
                    scanRadarForTvs()
                } else {
                    _serverState.update { it.copy(isRunning = false, errorMessage = err) }
                }
            }
        }
    }

    fun stopServer() {
        httpServer.stop()
        discoveryHelper.unregisterNsdService()
        _serverState.update {
            it.copy(isRunning = false, activeClients = 0, currentSpeedBytesPerSec = 0L)
        }
        stopForegroundServiceIntent()
    }

    private fun startForegroundServiceIntent() {
        try {
            val intent = Intent(appContext, TransferServerService::class.java).apply {
                action = TransferServerService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopForegroundServiceIntent() {
        try {
            val intent = Intent(appContext, TransferServerService::class.java).apply {
                action = TransferServerService.ACTION_STOP
            }
            appContext.startService(intent)
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        private var instance: TransferServerCoordinator? = null

        fun getInstance(context: Context): TransferServerCoordinator {
            return instance ?: synchronized(this) {
                instance ?: TransferServerCoordinator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
