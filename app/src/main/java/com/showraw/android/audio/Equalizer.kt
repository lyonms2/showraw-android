package com.showraw.android.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * EQ 3 bandas baseado nas fórmulas do Audio EQ Cookbook (RBJ).
 *   Band 1 — Low shelf  @ 200 Hz
 *   Band 2 — Peaking    @ 1 kHz  (Q = 0.707)
 *   Band 3 — High shelf @ 8 kHz
 */
class Equalizer {

    private data class Coeffs(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

    // Per-channel biquad state: [x1, x2, y1, y2]
    private var sLowL  = FloatArray(4); private var sLowR  = FloatArray(4)
    private var sMidL  = FloatArray(4); private var sMidR  = FloatArray(4)
    private var sHighL = FloatArray(4); private var sHighR = FloatArray(4)

    private var cLow  = identity()
    private var cMid  = identity()
    private var cHigh = identity()
    private var enabled = false

    fun configure(
        lowGainDb:  Float = 0f, lowFreq:  Float = 200f,
        midGainDb:  Float = 0f, midFreq:  Float = 1_000f, midQ: Float = 0.707f,
        highGainDb: Float = 0f, highFreq: Float = 8_000f,
        sampleRate: Int   = 48_000,
    ) {
        enabled = lowGainDb != 0f || midGainDb != 0f || highGainDb != 0f
        cLow  = lowShelf (lowGainDb,  lowFreq,  sampleRate)
        cMid  = peaking  (midGainDb,  midFreq,  midQ, sampleRate)
        cHigh = highShelf(highGainDb, highFreq, sampleRate)
        // Reset state on reconfigure to avoid discontinuities
        sLowL = FloatArray(4); sLowR = FloatArray(4)
        sMidL = FloatArray(4); sMidR = FloatArray(4)
        sHighL = FloatArray(4); sHighR = FloatArray(4)
    }

    fun processBuffer(buffer: ShortArray, size: Int) {
        if (!enabled) return
        var i = 0
        while (i < size - 1) {
            val xL = buffer[i].toFloat()     / 32768f
            val xR = buffer[i + 1].toFloat() / 32768f
            buffer[i]     = (chain(xL, sLowL, sMidL, sHighL) * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i + 1] = (chain(xR, sLowR, sMidR, sHighR) * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            i += 2
        }
    }

    private fun chain(x: Float, sL: FloatArray, sM: FloatArray, sH: FloatArray): Float =
        biquad(biquad(biquad(x, cLow, sL), cMid, sM), cHigh, sH)

    // Direct Form I biquad: y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2
    private fun biquad(x: Float, c: Coeffs, s: FloatArray): Float {
        val y = c.b0 * x + c.b1 * s[0] + c.b2 * s[1] - c.a1 * s[2] - c.a2 * s[3]
        s[1] = s[0]; s[0] = x
        s[3] = s[2]; s[2] = y
        return y
    }

    // ── Filter design ────────────────────────────────────────────────

    private fun lowShelf(gainDb: Float, freq: Float, sr: Int): Coeffs {
        val A    = 10f.pow(gainDb / 40f)
        val w0   = (2.0 * PI * freq / sr).toFloat()
        val cosW = cos(w0.toDouble()).toFloat()
        val sinW = sin(w0.toDouble()).toFloat()
        val alpha = sinW / 2f * sqrt(2f)          // S = 1
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
        val alpha = sinW / (2f * Q)

        val b0 =  1 + alpha * A
        val b1 = -2 * cosW
        val b2 =  1 - alpha * A
        val a0 =  1 + alpha / A
        val a1 = -2 * cosW
        val a2 =  1 - alpha / A
        return Coeffs(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0)
    }

    private fun identity() = Coeffs(1f, 0f, 0f, 0f, 0f)
}
