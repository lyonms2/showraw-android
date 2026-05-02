package com.showraw.android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10

enum class HardwareValidationResult {
    RAW_CONFIRMED,     // UNPROCESSED difere significativamente de MIC → RAW real
    RAW_UNCONFIRMED,   // Sinais idênticos → fabricante não suporta UNPROCESSED
    PERMISSION_DENIED,
    ERROR,
}

object HardwareValidator {

    private const val SAMPLE_RATE    = 48_000
    private const val RECORD_MS      = 1_000
    private const val DIFF_THRESHOLD = 3.0  // dB — diferença mínima para confirmar RAW

    suspend fun validate(): HardwareValidationResult = withContext(Dispatchers.IO) {
        try {
            val bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            val rmsUnprocessed = recordRms(MediaRecorder.AudioSource.UNPROCESSED, bufSize)
            val rmsMic         = recordRms(MediaRecorder.AudioSource.MIC, bufSize)

            if (rmsUnprocessed < 0 || rmsMic < 0) return@withContext HardwareValidationResult.ERROR

            val diffDb = abs(20 * log10((rmsUnprocessed + 1e-9) / (rmsMic + 1e-9)))
            if (diffDb >= DIFF_THRESHOLD) HardwareValidationResult.RAW_CONFIRMED
            else                          HardwareValidationResult.RAW_UNCONFIRMED

        } catch (e: SecurityException) {
            HardwareValidationResult.PERMISSION_DENIED
        } catch (e: Exception) {
            HardwareValidationResult.ERROR
        }
    }

    private fun recordRms(source: Int, bufSize: Int): Double {
        val recorder = AudioRecord(
            source, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return -1.0

        val buffer   = ShortArray(bufSize / 2)
        var sumSq    = 0.0
        var count    = 0L
        val deadline = System.currentTimeMillis() + RECORD_MS

        recorder.startRecording()
        while (System.currentTimeMillis() < deadline) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                for (i in 0 until read) {
                    val s = buffer[i] / 32768.0
                    sumSq += s * s
                    count++
                }
            }
        }
        recorder.stop()
        recorder.release()

        return if (count > 0) Math.sqrt(sumSq / count) else 0.0
    }
}
