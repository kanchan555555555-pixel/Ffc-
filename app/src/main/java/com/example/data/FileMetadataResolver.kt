package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Size
import com.example.model.FileCategory
import com.example.model.SharedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class FileMetadataResolver(private val context: Context) {

    suspend fun resolve(uri: Uri): SharedFileItem? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            // Try persisting read permission for SAF document URIs when available
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Photo Picker URIs grant temporary read access without persistable flags
            } catch (_: IllegalArgumentException) {
                // Ignore if URI does not support persistable permission
            }

            var displayName = "file_${System.currentTimeMillis()}"
            var sizeBytes = -1L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let {
                                displayName = it
                            }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }

            if (sizeBytes < 0L) {
                sizeBytes = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                if (sizeBytes < 0L) sizeBytes = 0L
            }

            val mimeType = resolver.getType(uri)
                ?: guessMimeFromName(displayName)

            val category = categorize(mimeType, displayName)
            var durationMs: Long? = null
            var thumbnail: Bitmap? = null

            if (category == FileCategory.VIDEO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    thumbnail = loadVideoThumb(resolver, uri, retriever)
                } catch (_: Exception) {
                    // Fallback without thumbnail if codec unsupported
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            } else if (category == FileCategory.PHOTO) {
                thumbnail = loadPhotoThumb(resolver, uri)
            }

            SharedFileItem(
                id = UUID.randomUUID().toString().take(8),
                uri = uri,
                name = displayName,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                category = category,
                durationMs = durationMs,
                thumbnail = thumbnail
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun loadVideoThumb(
        resolver: ContentResolver,
        uri: Uri,
        retriever: MediaMetadataRetriever
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return resolver.loadThumbnail(uri, Size(240, 160), null)
            } catch (_: Exception) {
            }
        }
        return try {
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.let { scaleBitmap(it, 240) }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPhotoThumb(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return resolver.loadThumbnail(uri, Size(200, 200), null)
            } catch (_: Exception) {
            }
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val sample = (maxDim / 200).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val ratio = maxDim.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        if (ratio >= 1f) return source
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun categorize(mimeType: String, name: String): FileCategory {
        val lowerName = name.lowercase()
        return when {
            mimeType.startsWith("video/") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mp4") || lowerName.endsWith(".avi") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".webm") -> FileCategory.VIDEO
            mimeType.startsWith("image/") -> FileCategory.PHOTO
            mimeType.startsWith("audio/") || lowerName.endsWith(".mp3") ||
                lowerName.endsWith(".flac") || lowerName.endsWith(".wav") -> FileCategory.AUDIO
            mimeType == "application/vnd.android.package-archive" || lowerName.endsWith(".apk") ->
                FileCategory.APK
            mimeType.contains("pdf") || mimeType.startsWith("text/") ||
                lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                lowerName.endsWith(".doc") || lowerName.endsWith(".docx") -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    private fun guessMimeFromName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".zip") -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
