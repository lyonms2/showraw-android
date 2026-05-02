package com.showraw.android.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportMode { VIDEO_FINAL, STEMS_WAV, BOTH }

data class ExportResult(
    val videoFile: File?,
    val audioFile: File?,
)

/**
 * Combina o vídeo bruto da câmera com o áudio processado pelo AudioEngine
 * usando MediaMuxer. Os timestamps de hardware garantem sincronização sem drift.
 */
class VideoExporter(private val context: Context) {

    suspend fun export(
        rawVideoFile: File,
        processedAudioPcm: File,
        outputDir: File,
        mode: ExportMode,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        // TODO: implementar muxing H.264 + AAC para vídeo final
        // TODO: implementar exportação de stems WAV PCM 48kHz / 24bit
        ExportResult(videoFile = null, audioFile = null)
    }
}
