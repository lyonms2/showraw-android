package com.showraw.android.audio

import com.showraw.android.presets.EqBand
import com.showraw.android.presets.EqFilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class Equalizer {

    data class Coeffs(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

    private var coeffs  = emptyList<Coeffs>()
    private var statesL = emptyList<FloatArray>()
    private var statesR = emptyList<FloatArray>()
    private var enabled = false

    fun configure(bands: List<EqBand>, sampleRate: Int = 48_000) {
        enabled = bands.any { it.gainDb != 0f }
        coeffs  = bands.map { computeCoeffs(it, sampleRate) }
        statesL = bands.map { FloatArray(4) }
        statesR = bands.map { FloatArray(4) }
    }

    fun processBuffer(buffer: FloatArray, size: Int) {
        if (!enabled || coeffs.isEmpty()) return
        var i = 0
        while (i < size - 1) {
            var xL = buffer[i]
            var xR = buffer[i + 1]
            for (k in coeffs.indices) {
                xL = biquad(xL, coeffs[k], statesL[k])
                xR = biquad(xR, coeffs[k], statesR[k])
            }
            buffer[i]     = xL
            buffer[i + 1] = xR
            i += 2
        }
    }

    private fun biquad(x: Float, c: Coeffs, s: FloatArray): Float {
        val y = c.b0 * x + c.b1 * s[0] + c.b2 * s[1] - c.a1 * s[2] - c.a2 * s[3]
        s[1] = s[0]; s[0] = x
        s[3] = s[2]; s[2] = y
        return y
    }

    // ── Filter design (Audio EQ Cookbook — RBJ) ──────────────────────

    private fun computeCoeffs(band: EqBand, sr: Int): Coeffs = when (band.type) {
        EqFilterType.LOW_SHELF  -> lowShelf (band.gainDb, band.freq, sr)
        EqFilterType.HIGH_SHELF -> highShelf(band.gainDb, band.freq, sr)
        EqFilterType.PEAKING    -> peaking  (band.gainDb, band.freq, band.q, sr)
    }

    private fun lowShelf(gainDb: Float, freq: Float, sr: Int): Coeffs {
        val A    = 10f.pow(gainDb / 40f)
        val w0   = (2.0 * PI * freq / sr).toFloat()
        val cosW = cos(w0.toDouble()).toFloat()
        val sinW = sin(w0.toDouble()).toFloat()
        val alpha = sinW / 2f * sqrt(2f)
        val sqA  = sqrt(A.toDouble()).toFloat()
        val b0 =      A * ((A + 1) - (A - 1) * cosW + 2 * sqA * alpha)
        val b1 =  2 * A * ((A - 1) - (A + 1) * cosW)
        val b2 =      A * ((A + 1) - (A - 1) * cosW - 2 * sqA * alpha)
        val a0 =          (A + 1) + (A - 1) * cosW + 2 * sqA * alpha
        val a1 =     -2 * ((A - 1) + (A + 1) * cosW)
        val a2 =          (A + 1) + (A - 1) * cosW - 2 * sqA * alpha
        return Coeffs(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0)
    }

    private fun highShelf(gainDb: Float, freq: Float, sr: Int): Coeffs {
        val A    = 10f.pow(gainDb / 40f)
        val w0   = (2.0 * PI * freq / sr).toFloat()
        val cosW = cos(w0.toDouble()).toFloat()
        val sinW = sin(w0.toDouble()).toFloat()
        val alpha = sinW / 2f * sqrt(2f)
        val sqA  = sqrt(A.toDouble()).toFloat()
        val b0 =       A * ((A + 1) + (A - 1) * cosW + 2 * sqA * alpha)
        val b1 =  -2 * A * ((A - 1) + (A + 1) * cosW)
        val b2 =       A * ((A + 1) + (A - 1) * cosW - 2 * sqA * alpha)
        val a0 =           (A + 1) - (A - 1) * cosW + 2 * sqA * alpha
        val a1 =       2 * ((A - 1) - (A + 1) * cosW)
        val a2 =           (A + 1) - (A - 1) * cosW - 2 * sqA * alpha
        return Coeffs(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0)
    }

    private fun peaking(gainDb: Float, freq: Float, Q: Float, sr: Int): Coeffs {
        val A    = 10f.pow(gainDb / 40f)
        val w0   = (2.0 * PI * freq / sr).toFloat()
        val cosW = cos(w0.toDouble()).toFloat()
        val sinW = sin(w0.toDouble()).toFloat()
        val alpha = sinW / (2f * Q.coerceAtLeast(0.1f))
        val b0 =  1 + alpha * A;  val b1 = -2 * cosW;  val b2 = 1 - alpha * A
        val a0 =  1 + alpha / A;  val a1 = -2 * cosW;  val a2 = 1 - alpha / A
        return Coeffs(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0)
    }

    companion object {
        fun computeCoeffsStatic(band: EqBand, sr: Int = 48_000): Coeffs =
            Equalizer().run { computeCoeffs(band, sr) }
    }
}
