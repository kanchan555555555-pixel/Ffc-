package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.network.QrCodeGenerator
import com.example.ui.BeamUiState
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
fun TvSetupScreen(
    uiState: BeamUiState,
    onRefreshNetwork: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val serverState = uiState.serverState
    val url = serverState.serverUrl
    val hotspot = serverState.localHotspot

    val browserQr = remember(url) { url?.let { QrCodeGenerator.generateQrBitmap(it, 440) } }
    val wifiQr = remember(hotspot.isActive, hotspot.ssid, hotspot.passphrase) {
        if (hotspot.isActive && hotspot.ssid.isNotBlank()) {
            QrCodeGenerator.generateQrBitmap(
                QrCodeGenerator.buildWifiQrPayload(hotspot.ssid, hotspot.passphrase),
                440
            )
        } else null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ElectricCyan.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = ElectricCyan)
                        Text(
                            text = strings.tvGuideHeader,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = strings.tvGuideSub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        item(key = "step1") {
            StepGuideCard(
                stepNumber = "1",
                title = strings.step1Title,
                description = strings.step1Desc,
                icon = Icons.Default.Wifi,
                accent = ElectricCyan,
                actionContent = {
                    FilledTonalButton(
                        onClick = onOpenHotspotSettings,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("guide_hotspot_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(strings.openHotspotSettings)
                    }
                }
            )
        }

        item(key = "step2") {
            StepGuideCard(
                stepNumber = "2",
                title = strings.step2Title,
                description = strings.step2Desc,
                icon = Icons.Default.Router,
                accent = WarmAmber
            )
        }

        item(key = "step3") {
            StepGuideCard(
                stepNumber = "3",
                title = strings.step3Title,
                description = strings.step3Desc,
                icon = Icons.Default.Language,
                accent = EmeraldSuccess,
                actionContent = {
                    if (url != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianBg)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElectricCyan
                            )
                        }
                    }
                }
            )
        }

        if (browserQr != null || wifiQr != null) {
            item(key = "qr_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (browserQr != null) {
                        QrMiniCard(
                            title = strings.browserQrTitle,
                            subtitle = url ?: "",
                            bitmap = browserQr,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (wifiQr != null) {
                        QrMiniCard(
                            title = strings.wifiQrTitle,
                            subtitle = hotspot.ssid,
                            bitmap = wifiQr,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "interfaces") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.networkInterfacesTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRefreshNetwork,
                            modifier = Modifier.testTag("refresh_network_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.refreshNetwork)
                        }
                    }

                    if (serverState.interfaces.isEmpty()) {
                        Text(
                            text = strings.noHotspotIpHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmAmber
                        )
                    } else {
                        serverState.interfaces.forEach { iface ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianSurfaceHigh)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "http://${iface.ipAddress}:${serverState.port}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                        color = if (iface.isHotspotLikely) ElectricCyan else TextPrimary
                                    )
                                    Text(
                                        text = iface.interfaceName + if (iface.isHotspotLikely) " • Hotspot" else " • Wi-Fi LAN",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                }
                                if (iface.isHotspotLikely) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldSuccess)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "spacer") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StepGuideCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    actionContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            actionContent?.invoke()
        }
    }
}

@Composable
private fun QrMiniCard(
    title: String,
    subtitle: String,
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = ElectricCyan
            )
        }
    }
}
