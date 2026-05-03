package com.showraw.android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class HardwareValidationResult {
    RAW_CONFIRMED,      // UNPROCESSED tem mais energia em baixas frequências que MIC → RAW real
    RAW_UNCONFIRMED,    // Perfil espectral idêntico → fabricante não suporta UNPROCESSED
    AMBIENT_TOO_QUIET,  // Sinal ambiente insuficiente para análise espectral confiável
    PERMISSION_DENIED,
    ERROR,
}

object HardwareValidator {

    private const val SAMPLE_RATE       = 48_000
    private const val RECORD_MS         = 2_000   // 2s para melhor resolução espectral no Goertzel
    private const val AMBIENT_RMS_MIN   = 0.002   // sinal mínimo para análise válida (~-54 dBFS)
    // UNPROCESSED deve ter ratio low/mid pelo menos RATIO_THRESHOLD maior que MIC
    // se NC estiver ativo no MIC, ele corta 100-200Hz → UNPROCESSED fica acima
    private const val RATIO_THRESHOLD   = 0.12

    suspend fun validate(): HardwareValidationResult = withContext(Dispatchers.IO) {
        try {
            val bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            val (samplesUnprocessed, rmsUnprocessed) = recordSamples(
                MediaRecorder.AudioSource.UNPROCESSED, bufSize
            )
            if (samplesUnprocessed == null) return@withContext HardwareValidationResult.ERROR

            // Checar se há sinal suficiente para análise espectral significativa
            if (rmsUnprocessed < AMBIENT_RMS_MIN) {
                return@withContext HardwareValidationResult.AMBIENT_TOO_QUIET
            }

            val (samplesMic, _) = recordSamples(MediaRecorder.AudioSource.MIC, bufSize)
            if (samplesMic == null) return@withContext HardwareValidationResult.ERROR

            val ratioUnprocessed = spectralRatio(samplesUnprocessed)
            val ratioMic         = spectralRatio(samplesMic)

            // Se UNPROCESSED preserva significativamente mais energia em baixas frequências,
            // confirma que o NC do pipeline MIC está sendo bypassado
            if (ratioUnprocessed - ratioMic > RATIO_THRESHOLD) {
                HardwareValidationResult.RAW_CONFIRMED
            } else {
                HardwareValidationResult.RAW_UNCONFIRMED
            }

        } catch (e: SecurityException) {
            HardwareValidationResult.PERMISSION_DENIED
        } catch (e: Exception) {
            HardwareValidationResult.ERROR
        }
    }

    /**
     * Razão entre energia em baixas frequências (120Hz + 200Hz) e médias (1kHz + 2kHz).
     * NC típico de voz atenua abaixo de ~300Hz → UNPROCESSED terá ratio maior se NC estiver ativo.
     */
    private fun spectralRatio(samples: DoubleArray): Double {
        val lowEnergy = goertzel(samples, 120.0) + goertzel(samples, 200.0)
        val midEnergy = goertzel(samples, 1000.0) + goertzel(samples, 2000.0)
        return if (midEnergy > 0.0) lowEnergy / midEnergy else 0.0
    }

    /**
     * Algoritmo de Goertzel — calcula energia em uma única frequência sem FFT completa.
     * Muito mais eficiente que FFT quando só precisamos de algumas frequências específicas.
     */
    private fun goertzel(samples: DoubleArray, targetFreq: Double): Double {
        val k     = (targetFreq * samples.size / SAMPLE_RATE + 0.5).roundToInt()
        val omega = 2.0 * PI * k / samples.size
        val coeff = 2.0 * cos(omega)
        var s1    = 0.0
        var s2    = 0.0
        for (x in samples) {
            val s0 = x + coeff * s1 - s2
            s2 = s1; s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    private fun recordSamples(source: Int, bufSize: Int): Pair<DoubleArray?, Double> {
        val recorder = AudioRecord(
            source, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return Pair(null, 0.0)

        val buffer   = ShortArray(bufSize / 2)
        val all      = ArrayList<Double>(SAMPLE_RATE * 2)
        var sumSq    = 0.0
        val deadline = System.currentTimeMillis() + RECORD_MS

        recorder.startRecording()
        while (System.currentTimeMillis() < deadline) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                for (i in 0 until read) {
                    val s = buffer[i] / 32768.0
                    all.add(s)
                    sumSq += s * s
                }
            }
        }
        recorder.stop()
        recorder.release()

        val rms = if (all.isNotEmpty()) sqrt(sumSq / all.size) else 0.0
        return Pair(if (all.isNotEmpty()) all.toDoubleArray() else null, rms)
    }
}
