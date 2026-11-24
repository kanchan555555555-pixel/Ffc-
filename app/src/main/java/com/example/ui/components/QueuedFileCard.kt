package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FormatUtils
import com.example.localization.LocalAppStrings
import com.example.model.FileCategory
import com.example.model.SharedFileItem
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
fun QueuedFileCard(
    item: SharedFileItem,
    onSendNow: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val thumbBitmap = remember(item.id, item.thumbnail) {
        item.thumbnail?.asImageBitmap()
    }
    val durationLabel = remember(item.durationMs) {
        FormatUtils.formatDuration(item.durationMs)
    }

    val isSending = item.activeConnections > 0
    val borderColor = when {
        isSending -> ElectricCyan.copy(alpha = 0.7f)
        item.downloadCount > 0 -> EmeraldSuccess.copy(alpha = 0.55f)
        else -> ObsidianBorder
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = iconForCategory(item.category),
                            contentDescription = item.category.name,
                            tint = accentForCategory(item.category),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (durationLabel != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(ObsidianBg.copy(alpha = 0.85f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = durationLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatBytes(item.sizeBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = TextSecondary
                        )
                        Text("•", color = TextMuted)
                        val statusText = when {
                            isSending -> strings.sendingNow
                            item.downloadCount > 0 -> strings.savedToTvCount(item.downloadCount)
                            else -> strings.readyOnTv
                        }
                        val statusColor = when {
                            isSending -> ElectricCyan
                            item.downloadCount > 0 -> EmeraldSuccess
                            else -> TextMuted
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onSendNow,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("send_file_${item.id}"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.16f),
                        contentColor = ElectricCyan
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.sendNow, style = MaterialTheme.typography.labelLarge)
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("remove_file_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.removeFile,
                        tint = TextSecondary
                    )
                }
            }

            if (isSending) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = ElectricCyan,
                    trackColor = ObsidianSurfaceHigh
                )
            }
        }
    }
}

private fun iconForCategory(category: FileCategory) = when (category) {
    FileCategory.VIDEO -> Icons.Default.Movie
    FileCategory.PHOTO -> Icons.Default.Image
    FileCategory.AUDIO -> Icons.Default.AudioFile
    FileCategory.APK -> Icons.Default.Android
    FileCategory.DOCUMENT -> Icons.Default.Description
    FileCategory.OTHER -> Icons.Default.InsertDriveFile
}

private fun accentForCategory(category: FileCategory): Color = when (category) {
    FileCategory.VIDEO -> ElectricCyan
    FileCategory.PHOTO -> WarmAmber
    FileCategory.AUDIO -> Color(0xFFA78BFA)
    FileCategory.APK -> EmeraldSuccess
    FileCategory.DOCUMENT -> Color(0xFF38BDF8)
    FileCategory.OTHER -> TextSecondary
}
