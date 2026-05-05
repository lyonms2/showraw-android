package com.showraw.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import com.showraw.android.presets.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioStats(
    val rms: Float,
    val peakDbFs: Float,
    val gainReductionDb: Float,
    val isClipping: Boolean,    // pico > -1 dBFS por > 2s consecutivos
)

/**
 * Motor principal de captura e DSP.
 *
 * Cadeia de processamento (ordem correta do ponto de vista acústico):
 *   Captura → Gain → HPF → NoiseGate → EQ → Compressor → Limiter → Saída
 *
 * Pipeline interno em Float32 — sem conversão Int16 intermediária.
 * A conversão Short↔Float acontece apenas uma vez: na entrada e na saída.
 */
class AudioEngine {

    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_CFG = AudioFormat.CHANNEL_IN_STEREO
        private const val ENCODING    = AudioFormat.ENCODING_PCM_16BIT
        private const val SPL_WARN_DB = -1f   // dBFS
        private const val SPL_WARN_MS = 2_000L

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
    private val noiseGate  = NoiseGate()
    private val equalizer  = Equalizer()
    private val compressor = Compressor()
    private val limiter    = Limiter()

    var onBufferReady: ((ShortArray, Int) -> Unit)? = null
    var onStats:       ((AudioStats)      -> Unit)? = null
    var onSplWarning:  (() -> Unit)?                = null

    private var splWarningSince = 0L
    @Volatile private var paused = false

    fun configure(preset: Preset) {
        preGainFactor = Math.pow(10.0, preset.inputGainDb / 20.0).toFloat()
        hpf.configure(preset.hpfFrequency, preset.hpfRolloff)
        noiseGate.configure(preset.noiseGateThreshold)
        equalizer.configure(preset.eqBands)
        compressor.configure(
            thresholdDb  = preset.compressorThreshold,
            ratio        = preset.compressorRatio,
            attackMs     = preset.compressorAttack,
            releaseMs    = preset.compressorRelease,
            makeupGainDb = preset.compressorMakeupDb,
            enabled      = preset.compressorEnabled,
        )
        limiter.configure(preset.limiterThreshold, preset.limiterAttack, preset.limiterRelease)
    }

    fun enableMonitoring(enable: Boolean) {
        monitoringEnabled = enable
        if (enable) monitorTrack?.play() else { monitorTrack?.pause(); monitorTrack?.flush() }
    }

    fun start() {
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CFG, ENCODING)
            .coerceAtLeast(1024 * 2 * 2)

        recorder = createAudioRecord(bufSize).also { it.startRecording() }
        monitorTrack = createMonitorTrack()

        // Buffer de captura (Int16, exigido pelo AudioRecord)
        val shortBuf = ShortArray(bufSize / 2)
        // Buffer interno de processamento DSP (Float32)
        val floatBuf = FloatArray(bufSize / 2)

        captureJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isActive) {
                val read = recorder?.read(shortBuf, 0, shortBuf.size) ?: break
                if (read <= 0 || paused) continue

                // ── Short → Float (única conversão de entrada) ──────────────
                for (i in 0 until read) floatBuf[i] = shortBuf[i] / 32768f

                // ── Ganho de entrada ────────────────────────────────────────
                if (preGainFactor != 1f) {
                    for (i in 0 until read) floatBuf[i] *= preGainFactor
                }

                // ── Cadeia DSP — ordem acusticamente correta ────────────────
                hpf.processBuffer(floatBuf, read)         // 1. Remove sub-graves e ruído de contato
                noiseGate.processBuffer(floatBuf, read)   // 2. Gate no sinal pré-EQ (não reage a boost)
                equalizer.processBuffer(floatBuf, read)   // 3. Equalização tonal
                compressor.processBuffer(floatBuf, read)  // 4. Controle dinâmico
                limiter.processBuffer(floatBuf, read)     // 5. Teto absoluto de amplitude

                // ── Estatísticas (medidas no sinal final, antes da conversão) ─
                val rms  = computeRms(floatBuf, read)
                val peak = computePeakDbFs(floatBuf, read)
                val gr   = limiter.gainReductionDb()

                // ── Float → Short (única conversão de saída) ────────────────
                for (i in 0 until read) {
                    shortBuf[i] = (floatBuf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }

                if (monitoringEnabled) monitorTrack?.write(shortBuf, 0, read)

                // ── Detecção de clipping prolongado ────────────────────────
                val now = System.currentTimeMillis()
                val clipping = if (peak > SPL_WARN_DB) {
                    if (splWarningSince == 0L) splWarningSince = now
                    val exceeded = (now - splWarningSince) > SPL_WARN_MS
                    if (exceeded) onSplWarning?.invoke()
                    exceeded
                } else {
                    splWarningSince = 0L
                    false
                }

                onStats?.invoke(AudioStats(rms, peak, gr, clipping))
                onBufferReady?.invoke(shortBuf.copyOf(read), read)
            }
        }
    }

    private fun createAudioRecord(bufSize: Int): AudioRecord {
        val sources = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            try {
                val rec = AudioRecord(source, SAMPLE_RATE, CHANNEL_CFG, ENCODING, bufSize)
                if (rec.state == AudioRecord.STATE_INITIALIZED) return rec
                rec.release()
            } catch (_: Exception) { }
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

    private fun computeRms(buffer: FloatArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) sum += buffer[i] * buffer[i]
        return sqrt(sum / size).toFloat()
    }

    private fun computePeakDbFs(buffer: FloatArray, size: Int): Float {
        var max = 0f
        for (i in 0 until size) {
            val a = abs(buffer[i])
            if (a > max) max = a
        }
        if (max == 0f) return -96f
        return (20.0 * log10(max.toDouble())).toFloat()
    }

    external fun nativeGetOboeVersion(): String
}
