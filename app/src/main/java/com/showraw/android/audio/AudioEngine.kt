package com.showraw.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.pow
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

    private var monitorTrack: AudioTrack? = null
    @Volatile private var monitoringEnabled = false
    @Volatile private var preGainFactor = 1f

    private val hpf        = HighPassFilter()
    private val equalizer  = Equalizer()
    private val noiseGate  = NoiseGate()
    private val compressor = Compressor()
    private val limiter    = Limiter()

    var onBufferReady: ((ShortArray, Int) -> Unit)? = null
    var onStats:       ((AudioStats)      -> Unit)? = null
    var onSplWarning:  (() -> Unit)?                = null

    private var splWarningSince = 0L
    @Volatile private var paused = false

    fun configure(preset: Preset) {
        preGainFactor = 10.0.pow(preset.inputGainDb / 20.0).toFloat()
        hpf.configure(preset.hpfFrequency, preset.hpfRolloff)
        equalizer.configure(preset.eqBands)
        noiseGate.configure(preset.noiseGateThreshold)
        compressor.configure(
            thresholdDb  = preset.compressorThreshold,
            ratio        = preset.compressorRatio,
            attackMs     = preset.compressorAttack,
            releaseMs    = preset.compressorRelease,
            makeupGainDb = preset.compressorMakeupDb,
        )
        limiter.configure(preset.limiterThreshold, preset.limiterAttack, preset.limiterRelease)
    }

    fun enableMonitoring(enable: Boolean) {
        monitoringEnabled = enable
        if (enable) {
            monitorTrack?.play()
        } else {
            monitorTrack?.pause()
            monitorTrack?.flush()
        }
    }

    fun start() {
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CFG, ENCODING)
            .coerceAtLeast(1024 * 2 * 2)  // mínimo 1024 samples, estéreo, 16bit

        recorder = createAudioRecord(bufSize).also { it.startRecording() }
        monitorTrack = createMonitorTrack()

        val buffer = ShortArray(bufSize / 2)

        captureJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isActive) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: break
                if (read <= 0) continue
                if (paused) continue  // drena AudioRecord sem processar

                if (preGainFactor != 1f) {
                    for (i in 0 until read) {
                        buffer[i] = (buffer[i] * preGainFactor).toInt()
                            .coerceIn(-32768, 32767).toShort()
                    }
                }
                hpf.processBuffer(buffer, read)
                equalizer.processBuffer(buffer, read)
                noiseGate.processBuffer(buffer, read)
                compressor.processBuffer(buffer, read)
                limiter.processBuffer(buffer, read)

                if (monitoringEnabled) monitorTrack?.write(buffer, 0, read)

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

    private fun createAudioRecord(bufSize: Int): AudioRecord {
        val sources = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,  // raw — ideal para nosso DSP
            MediaRecorder.AudioSource.CAMCORDER,    // tuned para vídeo, sem AGC
            MediaRecorder.AudioSource.MIC,          // fallback final
        )
        for (source in sources) {
            try {
                val rec = AudioRecord(source, SAMPLE_RATE, CHANNEL_CFG, ENCODING, bufSize)
                if (rec.state == AudioRecord.STATE_INITIALIZED) return rec
                rec.release()
            } catch (_: Exception) { /* tentar próxima fonte */ }
        }
        throw IllegalStateException("Nenhuma fonte de áudio disponível neste dispositivo")
    }

    fun pause()  { paused = true  }
    fun resume() { paused = false }

    fun stop() {
        paused = false
        monitoringEnabled = false
        captureJob?.cancel()
        captureJob = null
        recorder?.stop()
        recorder?.release()
        recorder = null
        monitorTrack?.stop()
        monitorTrack?.release()
        monitorTrack = null
    }

    private fun createMonitorTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, ENCODING)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
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
