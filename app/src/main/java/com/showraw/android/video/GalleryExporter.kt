package com.showraw.android.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object GalleryExporter {

    data class VideoMeta(
        val durationSecs: Long,
        val resLabel: String,
        val fps: Int,
        val sizeMb: Float,
    ) {
        val summary: String get() {
            val mm = durationSecs / 60; val ss = durationSecs % 60
            return "%02d:%02d · %s · %dfps · %.1f MB".format(mm, ss, resLabel, fps, sizeMb)
        }
    }

    suspend fun readMeta(file: File): VideoMeta = withContext(Dispatchers.IO) {
        val sizeMb = file.length() / 1_048_576f
        runCatching {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(file.absolutePath)
                val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()?.toInt()?.takeIf { it > 0 } ?: 30
                val res = when {
                    width >= 3840 -> "4K"
                    width >= 1920 -> "1080p"
                    width >= 1280 -> "720p"
                    else          -> "${width}p"
                }
                VideoMeta(TimeUnit.MILLISECONDS.toSeconds(durationMs), res, fps, sizeMb)
            }
        }.getOrDefault(VideoMeta(0, "?", 30, sizeMb))
    }

    /**
     * Copia o MP4 para a galeria pública (Movies/ShowRaw) via MediaStore.
     * Deve ser chamado numa coroutine com Dispatchers.IO.
     */
    suspend fun saveToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ShowRaw")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        }.getOrElse {
            context.contentResolver.delete(uri, null, null)
            false
        }
    }
}
