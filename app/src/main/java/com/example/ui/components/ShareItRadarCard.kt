package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.model.DiscoveredTvDevice
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
fun ShareItRadarCard(
    discoveredTvs: List<DiscoveredTvDevice>,
    selectedTvId: String?,
    isScanning: Boolean,
    isServerRunning: Boolean,
    queuedCount: Int,
    onScanRadar: () -> Unit,
    onSelectTv: (String) -> Unit,
    onSendAllNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isScanning) 1600 else 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface,
        border = BorderStroke(
            1.dp,
            if (discoveredTvs.isNotEmpty()) EmeraldSuccess.copy(alpha = 0.5f) else ObsidianBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.radarTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isScanning -> strings.radarScanning
                                discoveredTvs.isNotEmpty() -> strings.radarFoundCount(discoveredTvs.size)
                                else -> strings.tvAutoSaveHint
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (discoveredTvs.isNotEmpty()) EmeraldSuccess else TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onScanRadar,
                    enabled = !isScanning,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("scan_radar_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.15f),
                        contentColor = ElectricCyan
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ElectricCyan
                        )
                    } else {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(strings.radarScanBtn, maxLines = 1)
                }
            }

            // Animated SHAREit-Style Sonar Radar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = minOf(size.width, size.height) * 0.44f

                    // Static concentric rings
                    listOf(0.35f, 0.68f, 1.0f).forEach { factor ->
                        drawCircle(
                            color = ElectricCyan.copy(alpha = 0.16f),
                            radius = maxRadius * factor,
                            center = center,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    // Expanding sonar pulse ring when server or scan is active
                    if (isScanning || isServerRunning) {
                        val pulseRadius = maxRadius * (0.22f + 0.78f * rippleProgress)
                        val pulseAlpha = (1f - rippleProgress) * 0.45f
                        drawCircle(
                            color = ElectricCyan.copy(alpha = pulseAlpha),
                            radius = pulseRadius,
                            center = center,
                            style = Stroke(width = 3f)
                        )

                        // Rotating radar sweep line
                        rotate(degrees = sweepAngle, pivot = center) {
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(ElectricCyan.copy(alpha = 0.65f), Color.Transparent),
                                    start = center,
                                    end = Offset(center.x + maxRadius, center.y)
                                ),
                                start = center,
                                end = Offset(center.x + maxRadius, center.y),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Center Mobile Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Orbiting Discovered TV Node Pills inside radar corners
                if (discoveredTvs.isNotEmpty()) {
                    val firstTv = discoveredTvs.first()
                    RadarNodeBubble(
                        device = firstTv,
                        selected = firstTv.id == selectedTvId,
                        onClick = { onSelectTv(firstTv.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                    if (discoveredTvs.size > 1) {
                        val secondTv = discoveredTvs[1]
                        RadarNodeBubble(
                            device = secondTv,
                            selected = secondTv.id == selectedTvId,
                            onClick = { onSelectTv(secondTv.id) },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        )
                    }
                }
            }

            // Discovered TV Selector List
            if (discoveredTvs.isEmpty()) {
                Text(
                    text = strings.radarEmptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredTvs.forEach { dev ->
                        val isSelected = dev.id == selectedTvId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectTv(dev.id) }
                                .testTag("radar_tv_${dev.ipAddress}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.12f) else ObsidianSurfaceHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ElectricCyan else ObsidianBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (dev.isBrowserConnected) EmeraldSuccess.copy(alpha = 0.18f)
                                            else ElectricCyan.copy(alpha = 0.16f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (dev.supportsDlnaPush) Icons.Default.Cast else Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = if (dev.isBrowserConnected) EmeraldSuccess else ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dev.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = dev.subtitle,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (dev.isBrowserConnected || dev.supportsDlnaPush) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = (if (dev.isBrowserConnected) EmeraldSuccess else WarmAmber).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (dev.isBrowserConnected) strings.browserConnectedBadge else strings.dlnaReadyBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (dev.isBrowserConnected) EmeraldSuccess else WarmAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Big SHAREit-style Send All CTA when files are queued
            if (queuedCount > 0) {
                Button(
                    onClick = onSendAllNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("send_all_now_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF001F26)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${strings.sendAllNow} ($queuedCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarNodeBubble(
    device: DiscoveredTvDevice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) EmeraldSuccess.copy(alpha = 0.22f) else ObsidianSurfaceHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (selected) EmeraldSuccess else ElectricCyan.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (device.isBrowserConnected) EmeraldSuccess else ElectricCyan)
            )
            Text(
                text = device.name,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
