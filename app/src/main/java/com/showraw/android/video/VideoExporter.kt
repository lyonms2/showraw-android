package com.showraw.android.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.showraw.android.audio.AudioEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class ExportResult(
    val videoMp4:  File,   // MP4 final com vídeo + áudio processado
    val audioM4a:  File,   // stem de áudio AAC 256kbps (MP4/M4A)
)

object VideoExporter {

    /**
     * Combina o vídeo-only do CameraX com o áudio processado (WAV) num MP4 final.
     *
     * Etapas:
     *   1. PCM WAV → AAC MP4 (MediaCodec, ~70% do progresso)
     *   2. MediaExtractor lê video track + audio track
     *   3. MediaMuxer escreve o MP4 final com A/V sync
     *   4. Arquivos temporários deletados; AAC preservado como stem
     */
    suspend fun mux(
        videoFile:  File,
        wavFile:    File,
        outputFile: File,
        location:   android.location.Location? = null,
        onProgress: (Float) -> Unit = {},
    ): ExportResult = withContext(Dispatchers.IO) {

        onProgress(0f)

        // ── Passo 1: codificar áudio WAV → AAC MP4 ──────────────────
        val aacMp4 = AudioEncoder.encodeWavToAacMp4(wavFile) { p ->
            onProgress(p * 0.70f)
        }
        onProgress(0.70f)

        // ── Passo 2: abrir extratores ────────────────────────────────
        val videoExtractor = MediaExtractor().also { it.setDataSource(videoFile.absolutePath) }
        val audioExtractor = MediaExtractor().also { it.setDataSource(aacMp4.absolutePath) }

        val videoTrackIdx = (0 until videoExtractor.trackCount).first { i ->
            videoExtractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        videoExtractor.selectTrack(videoTrackIdx)
        audioExtractor.selectTrack(0)

        // MediaExtractor.getTrackFormat() não inclui KEY_ROTATION — é metadado de container
        // (caixa tkhd do MP4), não de trilha. Ler com MediaMetadataRetriever e reinjetar.
        val srcRotation = MediaMetadataRetriever().use { r ->
            r.setDataSource(videoFile.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        }

        val videoFormat = videoExtractor.getTrackFormat(videoTrackIdx)
        val audioFormat = audioExtractor.getTrackFormat(0)

        // ── Passo 3: mux ─────────────────────────────────────────────
        val muxer  = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vTrack = muxer.addTrack(videoFormat)
        val aTrack = muxer.addTrack(audioFormat)
        // setOrientationHint escreve a rotação no cabeçalho do container MP4 (tkhd box),
        // que é o que players leem. MediaFormat.KEY_ROTATION na trilha é ignorado pelo MediaMuxer.
        muxer.setOrientationHint(srcRotation)
        location?.let { muxer.setLocation(it.latitude.toFloat(), it.longitude.toFloat()) }
        muxer.start()

        val buf  = ByteBuffer.allocate(1024 * 1024)
        val info = MediaCodec.BufferInfo()

        // Copiar vídeo
        while (true) {
            val size = videoExtractor.readSampleData(buf, 0)
            if (size < 0) break
            info.offset             = 0
            info.size               = size
            info.presentationTimeUs = videoExtractor.sampleTime
            info.flags              = videoExtractor.sampleFlags
            muxer.writeSampleData(vTrack, buf, info)
            videoExtractor.advance()
        }
        onProgress(0.85f)

        // Copiar áudio
        while (true) {
            val size = audioExtractor.readSampleData(buf, 0)
            if (size < 0) break
            info.offset             = 0
            info.size               = size
            info.presentationTimeUs = audioExtractor.sampleTime
            info.flags              = audioExtractor.sampleFlags
            muxer.writeSampleData(aTrack, buf, info)
            audioExtractor.advance()
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
        onProgress(0.95f)

        // ── Passo 4: limpar temporários (WAV e vídeo raw); AAC preservado ──
        videoFile.delete()
        wavFile.delete()

        // Renomear AAC para nome legível ao lado do MP4 final
        val audioStem = File(outputFile.parent, "${outputFile.nameWithoutExtension}_audio.m4a")
        if (!aacMp4.renameTo(audioStem)) {
            aacMp4.copyTo(audioStem, overwrite = true)
            aacMp4.delete()
        }

        onProgress(1f)
        ExportResult(videoMp4 = outputFile, audioM4a = audioStem)
    }
}
