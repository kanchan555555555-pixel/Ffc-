package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class FileCategory {
    VIDEO,
    PHOTO,
    AUDIO,
    APK,
    DOCUMENT,
    OTHER
}

data class SharedFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    val durationMs: Long? = null,
    val thumbnail: Bitmap? = null,
    val addedAtMs: Long = System.currentTimeMillis(),
    val downloadCount: Int = 0,
    val bytesTransferred: Long = 0L,
    val activeConnections: Int = 0,
    val pushQueuedCount: Int = 0
) {
    val progressFraction: Float
        get() = if (sizeBytes > 0L && bytesTransferred > 0L) {
            (bytesTransferred.toFloat() / sizeBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class DiscoveredTvDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val subtitle: String,
    val avTransportControlUrl: String? = null,
    val supportsDlnaPush: Boolean = false,
    val isBrowserConnected: Boolean = false,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class NetworkInterfaceInfo(
    val interfaceName: String,
    val ipAddress: String,
    val isHotspotLikely: Boolean
)

data class LocalHotspotInfo(
    val isActive: Boolean = false,
    val ssid: String = "",
    val passphrase: String = "",
    val errorMessage: String? = null
)

data class ServerState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val primaryIp: String? = null,
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
    val localHotspot: LocalHotspotInfo = LocalHotspotInfo(),
    val activeClients: Int = 0,
    val currentSpeedBytesPerSec: Long = 0L,
    val totalBytesSent: Long = 0L,
    val pinEnabled: Boolean = false,
    val pinCode: String = "4829",
    val deviceName: String = "TV Beam Mobile",
    val isRadarScanning: Boolean = false,
    val discoveredTvs: List<DiscoveredTvDevice> = emptyList(),
    val selectedTvId: String? = null,
    val errorMessage: String? = null
) {
    val serverUrl: String?
        get() = primaryIp?.let { "http://$it:$port" }
}

data class TransferRecord(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val clientIp: String,
    val timestampMs: Long,
    val speedBytesPerSec: Long,
    val completed: Boolean
)
