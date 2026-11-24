package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileMetadataResolver
import com.example.data.SettingsDataStore
import com.example.data.UserSettings
import com.example.localization.AppLanguage
import com.example.model.ServerState
import com.example.model.SharedFileItem
import com.example.model.TransferRecord
import com.example.service.TransferServerCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BeamUiState(
    val settings: UserSettings = UserSettings(),
    val serverState: ServerState = ServerState(),
    val files: List<SharedFileItem> = emptyList(),
    val transferHistory: List<TransferRecord> = emptyList(),
    val isResolvingFiles: Boolean = false
)

sealed interface UiNotice {
    data class SentToTv(val label: String) : UiNotice
    data class DlnaResult(val ok: Boolean, val tvName: String?) : UiNotice
}

class BeamViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore(application)
    private val metadataResolver = FileMetadataResolver(application)
    private val coordinator = TransferServerCoordinator.getInstance(application)

    private val isResolving = MutableStateFlow(false)

    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<UiNotice> = _notices.asSharedFlow()

    val uiState: StateFlow<BeamUiState> = combine(
        settingsStore.settingsFlow,
        coordinator.serverState,
        coordinator.files,
        coordinator.transferHistory,
        isResolving
    ) { settings, server, files, history, resolving ->
        BeamUiState(
            settings = settings,
            serverState = server,
            files = files,
            transferHistory = history,
            isResolvingFiles = resolving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BeamUiState()
    )

    init {
        coordinator.onBytesCommitted = { bytes ->
            viewModelScope.launch(Dispatchers.IO) {
                settingsStore.addLifetimeBytes(bytes)
            }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                coordinator.applySettings(settings)
            }
        }
        coordinator.refreshNetworkInterfaces()
    }

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isResolving.value = true
            try {
                val resolved = uris.map { uri ->
                    async(Dispatchers.IO) { metadataResolver.resolve(uri) }
                }.awaitAll().filterNotNull()
                coordinator.addFiles(resolved)
            } finally {
                isResolving.value = false
            }
        }
    }

    fun removeFile(fileId: String) {
        coordinator.removeFile(fileId)
    }

    fun clearAllFiles() {
        coordinator.clearAllFiles()
    }

    fun scanRadar() {
        coordinator.scanRadarForTvs()
    }

    fun selectTv(tvId: String) {
        coordinator.selectTv(tvId)
    }

    fun sendSingleFile(item: SharedFileItem) {
        coordinator.pushFilesToTv(listOf(item.id)) { ok, tvName ->
            _notices.tryEmit(UiNotice.DlnaResult(ok, tvName))
        }
        _notices.tryEmit(UiNotice.SentToTv(item.name))
    }

    fun sendAllFiles() {
        val allIds = uiState.value.files.map { it.id }
        if (allIds.isEmpty()) return
        coordinator.pushFilesToTv(allIds) { ok, tvName ->
            _notices.tryEmit(UiNotice.DlnaResult(ok, tvName))
        }
        _notices.tryEmit(UiNotice.SentToTv("${allIds.size} files"))
    }

    fun toggleServer() {
        if (coordinator.serverState.value.isRunning) {
            coordinator.stopServer()
        } else {
            coordinator.startServer()
        }
    }

    fun ensureServerStarted() {
        if (!coordinator.serverState.value.isRunning) {
            coordinator.startServer()
        }
    }

    fun startInstantHotspot() {
        coordinator.discoveryHelper.startLocalOnlyHotspot { started ->
            if (started) {
                coordinator.refreshNetworkInterfaces()
                ensureServerStarted()
            }
        }
    }

    fun stopInstantHotspot() {
        coordinator.discoveryHelper.stopLocalOnlyHotspot()
        coordinator.refreshNetworkInterfaces()
    }

    fun openPhoneHotspotSettings() {
        coordinator.discoveryHelper.openSystemHotspotSettings()
    }

    fun refreshNetwork() {
        coordinator.refreshNetworkInterfaces()
    }

    fun toggleLanguage() {
        val current = uiState.value.settings.language
        val next = if (current == AppLanguage.BENGALI) AppLanguage.ENGLISH else AppLanguage.BENGALI
        viewModelScope.launch {
            settingsStore.setLanguage(next)
        }
    }

    fun updateTransferSettings(
        deviceName: String,
        port: Int,
        pinEnabled: Boolean,
        pinCode: String
    ) {
        viewModelScope.launch {
            settingsStore.updateTransferSettings(deviceName, port, pinEnabled, pinCode)
        }
    }
}
