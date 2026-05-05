package com.showraw.android.audio

import kotlin.math.*

/**
 * Filtro IIR Butterworth high-pass, stereo-correto.
 * Coeficientes calculados uma vez em configure() — nunca durante processamento.
 * Canais L e R mantêm estados independentes para evitar contaminação cruzada.
 *
 * Rolloffs:
 *   12 dB/oct → 2ª ordem  = 1 biquad  (Q = √2/2)
 *   18 dB/oct → 3ª ordem  = 1ª ordem + 1 biquad (Q = 1.0)
 *   24 dB/oct → 4ª ordem  = 2 biquads (Q1 = 0.5412, Q2 = 1.3066 — Butterworth exato)
 */
class HighPassFilter(
    frequencyHz: Float = 120f,
    rolloffDbOct: Int  = 18,
    sampleRate: Int    = 48_000,
) {
    // Coeficientes imutáveis — compartilhados entre L e R
    private data class BiquadCoeffs(val b0: Double, val b1: Double, val b2: Double,
                                    val a1: Double, val a2: Double)
    private data class FirstOrderCoeffs(val b0: Double, val b1: Double, val a1: Double)

    // Estado mutável — um conjunto por canal
    private class BiquadState    { var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0 }
    private class FirstOrderState { var x1 = 0.0; var y1 = 0.0 }

    private var biquadCoeffs      = listOf<BiquadCoeffs>()
    private var firstOrderCoeffs: FirstOrderCoeffs? = null

    private var biquadStatesL     = listOf<BiquadState>()
    private var biquadStatesR     = listOf<BiquadState>()
    private var firstOrderStateL: FirstOrderState? = null
    private var firstOrderStateR: FirstOrderState? = null

    init { configure(frequencyHz, rolloffDbOct, sampleRate) }

    fun configure(frequencyHz: Float, rolloffDbOct: Int, sampleRate: Int = 48_000) {
        val fc = frequencyHz.toDouble()
        val fs = sampleRate.toDouble()

        val newCoeffs = mutableListOf<BiquadCoeffs>()
        var newFirst: FirstOrderCoeffs? = null

        when (rolloffDbOct) {
            12   -> newCoeffs.add(biquadCoeffs(fc, fs, Q_2ND))
            18   -> {
                newFirst = firstOrderCoeffs(fc, fs)
                newCoeffs.add(biquadCoeffs(fc, fs, Q_3RD_2ND_SECTION))
            }
            24   -> {
                newCoeffs.add(biquadCoeffs(fc, fs, Q_4TH_WIDE))
                newCoeffs.add(biquadCoeffs(fc, fs, Q_4TH_NARROW))
            }
            else -> newCoeffs.add(biquadCoeffs(fc, fs, Q_2ND))
        }

        biquadCoeffs   = newCoeffs
        firstOrderCoeffs = newFirst

        // Resetar estados ao reconfigurar
        biquadStatesL  = newCoeffs.map { BiquadState() }
        biquadStatesR  = newCoeffs.map { BiquadState() }
        firstOrderStateL = if (newFirst != null) FirstOrderState() else null
        firstOrderStateR = if (newFirst != null) FirstOrderState() else null
    }

    fun processBuffer(buffer: FloatArray, size: Int) {
        var i = 0
        while (i + 1 < size) {
            buffer[i]     = processSample(buffer[i].toDouble(),     firstOrderStateL, biquadStatesL).toFloat()
            buffer[i + 1] = processSample(buffer[i + 1].toDouble(), firstOrderStateR, biquadStatesR).toFloat()
            i += 2
        }
    }

    private fun processSample(
        s: Double,
        firstState: FirstOrderState?,
        bqStates: List<BiquadState>,
    ): Double {
        var v = s

        firstOrderCoeffs?.let { fc ->
            firstState?.let { fs ->
                val y = fc.b0 * v + fc.b1 * fs.x1 - fc.a1 * fs.y1
                fs.x1 = v; fs.y1 = y; v = y
            }
        }

        for (k in biquadCoeffs.indices) {
            val c  = biquadCoeffs[k]
            val st = bqStates[k]
            val y  = c.b0 * v + c.b1 * st.x1 + c.b2 * st.x2 - c.a1 * st.y1 - c.a2 * st.y2
            st.x2 = st.x1; st.x1 = v; st.y2 = st.y1; st.y1 = y; v = y
        }

        return v
    }

    private fun firstOrderCoeffs(fc: Double, fs: Double): FirstOrderCoeffs {
        val K  = tan(PI * fc / fs)
        return FirstOrderCoeffs(
            b0 =  1.0 / (1.0 + K),
            b1 = -1.0 / (1.0 + K),
            a1 = (K - 1.0) / (K + 1.0),
        )
    }

    private fun biquadCoeffs(fc: Double, fs: Double, q: Double): BiquadCoeffs {
        val w0    = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * q)
        val cosW  = cos(w0)
        val a0    = 1.0 + alpha
        return BiquadCoeffs(
            b0 =  (1.0 + cosW) / (2.0 * a0),
            b1 = -(1.0 + cosW) / a0,
            b2 =  (1.0 + cosW) / (2.0 * a0),
            a1 = -2.0 * cosW   / a0,
            a2 = (1.0 - alpha) / a0,
        )
    }

    companion object {
        private const val Q_2ND             = 0.7071067811865476
        private const val Q_3RD_2ND_SECTION = 1.0
        private const val Q_4TH_WIDE        = 0.5411961001338
        private const val Q_4TH_NARROW      = 1.3065629648763
    }
}
