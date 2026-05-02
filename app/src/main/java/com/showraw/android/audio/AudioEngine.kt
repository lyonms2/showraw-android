package com.showraw.android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.showraw.android.presets.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioStats(
    val rms: Float,
    val peakDbFs: Float,
    val gainReductionDb: Float,
    val isClipping: Boolean,    // pico > -1 dBFS por > 2s consecutivos
)

/**
 * Motor principal de captura e DSP.
 * Captura com UNPROCESSED, aplica HPF → Limiter, entrega buffer processado
 * via [onBufferReady] em thread de alta prioridade.
 */
class AudioEngine {

    companion object {
        private const val SAMPLE_RATE   = 48_000
        private const val CHANNEL_CFG   = AudioFormat.CHANNEL_IN_STEREO
        private const val ENCODING      = AudioFormat.ENCODING_PCM_16BIT
        private const val SPL_WARN_DB   = -1f  // dBFS
        private const val SPL_WARN_MS   = 2_000L

        init {
            System.loadLibrary("showraw-audio")
        }
    }

    private var recorder: AudioRecord? = null
    private var captureJob: Job?       = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val limiter = Limiter()
    private val hpf     = HighPassFilter()

    var onBufferReady: ((ShortArray, Int) -> Unit)? = null
    var onStats:       ((AudioStats)      -> Unit)? = null
    var onSplWarning:  (() -> Unit)?                = null

    private var splWarningSince = 0L

    fun configure(preset: Preset) {
        limiter.configure(preset.limiterThreshold, preset.limiterAttack, preset.limiterRelease)
        hpf.configure(preset.hpfFrequency, preset.hpfRolloff)
    }

    fun start() {
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CFG, ENCODING)
            .coerceAtLeast(1024 * 2 * 2)  // mínimo 1024 samples, estéreo, 16bit

        recorder = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            SAMPLE_RATE, CHANNEL_CFG, ENCODING, bufSize,
        ).also { it.startRecording() }

        val buffer = ShortArray(bufSize / 2)

        captureJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isActive) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: break
                if (read <= 0) continue

                hpf.processBuffer(buffer, read)
                limiter.processBuffer(buffer, read)

                val rms  = MicBlender.computeRms(buffer, read)
                val peak = computePeakDbFs(buffer, read)
                val gr   = limiter.gainReductionDb()

                val now = System.currentTimeMillis()
                val clipping = if (peak > SPL_WARN_DB) {
                    if (splWarningSince == 0L) splWarningSince = now
                    val dur = now - splWarningSince
                    if (dur > SPL_WARN_MS) { onSplWarning?.invoke(); true } else false
                } else {
                    splWarningSince = 0L
                    false
                }

                onStats?.invoke(AudioStats(rms, peak, gr, clipping))
                onBufferReady?.invoke(buffer.copyOf(read), read)
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    private fun computePeakDbFs(buffer: ShortArray, size: Int): Float {
        var max = 0
        for (i in 0 until size) {
            val abs = Math.abs(buffer[i].toInt())
            if (abs > max) max = abs
        }
        if (max == 0) return -96f
        return (20 * Math.log10(max / 32768.0)).toFloat()
    }

    // JNI — retorna versão da Oboe para validação de build
    external fun nativeGetOboeVersion(): String
}
