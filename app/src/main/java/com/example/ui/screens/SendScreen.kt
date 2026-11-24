package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.model.SharedFileItem
import com.example.ui.BeamUiState
import com.example.ui.components.HeroConnectionCard
import com.example.ui.components.QueuedFileCard
import com.example.ui.components.ShareItRadarCard
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun SendScreen(
    uiState: BeamUiState,
    onToggleServer: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onShowQr: () -> Unit,
    onPreviewInBrowser: (String) -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onToggleInstantHotspot: () -> Unit,
    onScanRadar: () -> Unit,
    onSelectTv: (String) -> Unit,
    onSendSingleFile: (SharedFileItem) -> Unit,
    onSendAllNow: () -> Unit,
    onPickVideosPhotos: () -> Unit,
    onPickAnyFiles: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearAllFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val hotspot = uiState.serverState.localHotspot

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "hero") {
            HeroConnectionCard(
                serverState = uiState.serverState,
                onToggleServer = onToggleServer,
                onCopyUrl = onCopyUrl,
                onShowQr = onShowQr,
                onPreviewInBrowser = onPreviewInBrowser
            )
        }

        // SHAREit-Style Radar & Connected TV Discovery
        item(key = "shareit_radar") {
            ShareItRadarCard(
                discoveredTvs = uiState.serverState.discoveredTvs,
                selectedTvId = uiState.serverState.selectedTvId,
                isScanning = uiState.serverState.isRadarScanning,
                isServerRunning = uiState.serverState.isRunning,
                queuedCount = uiState.files.size,
                onScanRadar = onScanRadar,
                onSelectTv = onSelectTv,
                onSendAllNow = onSendAllNow
            )
        }

        // Hotspot Quick Control Card
        item(key = "hotspot_controls") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ObsidianSurface,
                border = BorderStroke(
                    1.dp,
                    if (hotspot.isActive) EmeraldSuccess.copy(alpha = 0.55f) else ObsidianBorder
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.hotspotQuickTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (hotspot.isActive) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = EmeraldSuccess.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = strings.instantHotspotActive,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldSuccess,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenHotspotSettings,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("open_hotspot_settings_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsInputAntenna,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = strings.openHotspotSettings,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = onToggleInstantHotspot,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("toggle_instant_hotspot_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (hotspot.isActive) CoralError.copy(alpha = 0.16f)
                                else ElectricCyan.copy(alpha = 0.15f),
                                contentColor = if (hotspot.isActive) CoralError else ElectricCyan
                            )
                        ) {
                            Icon(
                                imageVector = if (hotspot.isActive) Icons.Default.WifiTetheringOff else Icons.Default.WifiTethering,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hotspot.isActive) strings.stopInstantHotspot else strings.startInstantHotspot,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (hotspot.isActive && hotspot.ssid.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianBg)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.wifiSsidLabel, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                Text(
                                    text = hotspot.ssid,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                    color = TextPrimary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.wifiPassLabel, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                Text(
                                    text = hotspot.passphrase.ifBlank { "Open" },
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                    color = WarmAmber
                                )
                            }
                        }
                    }

                    hotspot.errorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmAmber
                        )
                    }
                }
            }
        }

        // Big Dual File Pickers
        item(key = "file_pickers") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PickerActionCard(
                    title = strings.pickVideosPhotos,
                    subtitle = strings.pickVideosPhotosSub,
                    icon = Icons.Default.VideoLibrary,
                    accent = ElectricCyan,
                    isPrimary = true,
                    onClick = onPickVideosPhotos,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_media_button")
                )
                PickerActionCard(
                    title = strings.pickAnyFiles,
                    subtitle = strings.pickAnyFilesSub,
                    icon = Icons.Default.FolderOpen,
                    accent = WarmAmber,
                    isPrimary = false,
                    onClick = onPickAnyFiles,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_files_button")
                )
            }
        }

        // Queue Header
        item(key = "queue_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${strings.queuedFilesTitle} (${uiState.files.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    if (uiState.isResolvingFiles) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ElectricCyan
                        )
                    }
                }

                if (uiState.files.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAllFiles,
                        modifier = Modifier.testTag("clear_all_files_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.clearAll, color = CoralError)
                    }
                }
            }
        }

        if (uiState.files.isEmpty()) {
            item(key = "empty_queue") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianSurface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianSurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = strings.emptyQueueTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = strings.emptyQueueSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(uiState.files, key = { it.id }) { fileItem ->
                QueuedFileCard(
                    item = fileItem,
                    onSendNow = { onSendSingleFile(fileItem) },
                    onRemove = { onRemoveFile(fileItem.id) }
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PickerActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(114.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) ElectricCyan.copy(alpha = 0.14f) else ObsidianSurface,
            contentColor = TextPrimary
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
