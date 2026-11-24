package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.stringsFor
import com.example.ui.components.QrCodeDialog
import com.example.ui.screens.ActivitySettingsScreen
import com.example.ui.screens.SendScreen
import com.example.ui.screens.TvSetupScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class BeamTab {
    SEND,
    TV_SETUP,
    ACTIVITY
}

@Composable
fun TVBeamApp(
    viewModel: BeamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = remember(uiState.settings.language) { stringsFor(uiState.settings.language) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableStateOf(BeamTab.SEND) }
    var showQrDialog by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(strings) {
        viewModel.notices.collect { notice ->
            val msg = when (notice) {
                is UiNotice.SentToTv -> strings.sentSignalToTv(notice.label)
                is UiNotice.DlnaResult -> {
                    if (notice.ok && notice.tvName != null) strings.dlnaCastStarted(notice.tvName)
                    else strings.dlnaCastFailed
                }
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = currentTab != BeamTab.SEND) {
        currentTab = BeamTab.SEND
    }

    // Zero-permission Photo & Video Picker (Google Play compliant)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedUris(uris)
        }
    }

    // Zero-permission SAF Document Picker for any file (MKV, APK, PDF, ZIP, MP3)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedUris(uris)
        }
    }

    // Runtime permission for Foreground Service Notification (API 33+)
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.toggleServer()
    }

    // Runtime permission for LocalOnlyHotspot
    val hotspotPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startInstantHotspot()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(strings.permissionNeededHotspot)
            }
        }
    }

    val handleToggleServer: () -> Unit = {
        if (!uiState.serverState.isRunning &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleServer()
        }
    }

    val handleToggleInstantHotspot: () -> Unit = {
        if (uiState.serverState.localHotspot.isActive) {
            viewModel.stopInstantHotspot()
        } else {
            val neededPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.NEARBY_WIFI_DEVICES
            } else {
                Manifest.permission.ACCESS_FINE_LOCATION
            }
            if (ContextCompat.checkSelfPermission(context, neededPerm) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startInstantHotspot()
            } else {
                hotspotPermLauncher.launch(neededPerm)
            }
        }
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ObsidianBg, Color(0xFF0A1222), ObsidianBg)
                    )
                )
        ) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    BeamTopHeader(
                        language = uiState.settings.language,
                        onToggleLanguage = { viewModel.toggleLanguage() }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = ObsidianSurface,
                            contentColor = TextPrimary,
                            windowInsets = WindowInsets.navigationBars,
                            tonalElevation = 0.dp
                        ) {
                            BeamTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                val (label, icon) = tabInfo(tab, selected, strings)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF001F26),
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = ElectricCyan,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = ObsidianSurface,
                            contentColor = TextPrimary,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(Modifier.size(12.dp))
                            BeamTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                val (label, icon) = tabInfo(tab, selected, strings)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Color(0xFF001F26),
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = ElectricCyan,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                        ) {
                            when (currentTab) {
                                BeamTab.SEND -> SendScreen(
                                    uiState = uiState,
                                    onToggleServer = handleToggleServer,
                                    onCopyUrl = { url ->
                                        clipboard.setText(AnnotatedString(url))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(strings.urlCopied)
                                        }
                                    },
                                    onShowQr = { showQrDialog = true },
                                    onPreviewInBrowser = { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                    },
                                    onOpenHotspotSettings = { viewModel.openPhoneHotspotSettings() },
                                    onToggleInstantHotspot = handleToggleInstantHotspot,
                                    onScanRadar = { viewModel.scanRadar() },
                                    onSelectTv = { id -> viewModel.selectTv(id) },
                                    onSendSingleFile = { item -> viewModel.sendSingleFile(item) },
                                    onSendAllNow = { viewModel.sendAllFiles() },
                                    onPickVideosPhotos = {
                                        mediaPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    onPickAnyFiles = {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    onRemoveFile = { id -> viewModel.removeFile(id) },
                                    onClearAllFiles = { viewModel.clearAllFiles() }
                                )

                                BeamTab.TV_SETUP -> TvSetupScreen(
                                    uiState = uiState,
                                    onRefreshNetwork = { viewModel.refreshNetwork() },
                                    onOpenHotspotSettings = { viewModel.openPhoneHotspotSettings() }
                                )

                                BeamTab.ACTIVITY -> ActivitySettingsScreen(
                                    uiState = uiState,
                                    onSaveSettings = { name, port, pinOn, pin ->
                                        viewModel.updateTransferSettings(name, port, pinOn, pin)
                                    },
                                    onShowSnackbar = { msg ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showQrDialog) {
                val url = uiState.serverState.serverUrl
                if (url != null) {
                    QrCodeDialog(
                        url = url,
                        pinEnabled = uiState.serverState.pinEnabled,
                        pinCode = uiState.serverState.pinCode,
                        onDismiss = { showQrDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun BeamTopHeader(
    language: AppLanguage,
    onToggleLanguage: () -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        color = ObsidianBg.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(ElectricCyan, Color(0xFF007B94)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color(0xFF001F26),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = EmeraldSuccess.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = strings.offlineBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = strings.noTvAppBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ObsidianSurfaceHigh,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                TextButton(
                    onClick = onToggleLanguage,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("language_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) "EN" else "বাংলা",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

private fun tabInfo(
    tab: BeamTab,
    selected: Boolean,
    strings: com.example.localization.AppStrings
) = when (tab) {
    BeamTab.SEND -> strings.tabSend to if (selected) Icons.Filled.Send else Icons.Outlined.Send
    BeamTab.TV_SETUP -> strings.tabTvSetup to if (selected) Icons.Filled.CastConnected else Icons.Outlined.CastConnected
    BeamTab.ACTIVITY -> strings.tabActivity to if (selected) Icons.Filled.History else Icons.Outlined.History
}
