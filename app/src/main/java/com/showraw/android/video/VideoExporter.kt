package com.showraw.android.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.showraw.android.audio.AudioEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

enum class ExportMode { VIDEO_FINAL, STEMS_WAV, BOTH }

object VideoExporter {

    /**
     * Combina o vídeo-only do CameraX com o áudio processado (WAV) num MP4 final.
     *
     * Etapas:
     *   1. PCM WAV → AAC MP4 (MediaCodec, ~70% do progresso)
     *   2. MediaExtractor lê video track + audio track
     *   3. MediaMuxer escreve o MP4 final com A/V sync
     *   4. Arquivos temporários deletados
     */
    suspend fun mux(
        videoFile:  File,
        wavFile:    File,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {

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

        val videoFormat = videoExtractor.getTrackFormat(videoTrackIdx)
        val audioFormat = audioExtractor.getTrackFormat(0)

        // ── Passo 3: mux ─────────────────────────────────────────────
        val muxer  = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vTrack = muxer.addTrack(videoFormat)
        val aTrack = muxer.addTrack(audioFormat)
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

        // ── Passo 4: limpar temporários ──────────────────────────────
        videoFile.delete()
        wavFile.delete()
        aacMp4.delete()

        onProgress(1f)
        outputFile
    }
}
