package com.showraw.android.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

class NoiseGate {

    private var thresholdLinear = 0f
    private var attackCoeff     = 0f
    private var releaseCoeff    = 0f
    private var holdTotal       = 0     // samples to hold open after signal drops
    private var holdRemaining   = 0
    private var gain            = 1f
    private var enabled         = false

    /**
     * [thresholdDb] ≤ -59 dBFS desabilita o gate completamente (bypass).
     * [holdMs] evita chattering: mantém aberto por este tempo após sinal cair.
     */
    fun configure(
        thresholdDb: Float,
        attackMs:    Float = 5f,
        releaseMs:   Float = 100f,
        holdMs:      Float = 60f,
        sampleRate:  Int   = 48_000,
    ) {
        if (thresholdDb <= -59f) { enabled = false; gain = 1f; return }
        enabled         = true
        thresholdLinear = 10f.pow(thresholdDb / 20f)
        attackCoeff     = exp(-1f / (attackMs  / 1000f * sampleRate))
        releaseCoeff    = exp(-1f / (releaseMs / 1000f * sampleRate))
        holdTotal       = (holdMs / 1000f * sampleRate).toInt()
        holdRemaining   = 0
        gain            = 1f
    }

    fun processBuffer(buffer: ShortArray, size: Int) {
        if (!enabled) return
        var i = 0
        while (i < size - 1) {
            val level = max(abs(buffer[i].toInt()), abs(buffer[i + 1].toInt())) / 32768f
            val target = if (level > thresholdLinear) {
                holdRemaining = holdTotal
                1f
            } else if (holdRemaining > 0) {
                holdRemaining--
                1f
            } else {
                0f
            }
            val coeff = if (target >= gain) attackCoeff else releaseCoeff
            gain = coeff * gain + (1f - coeff) * target
            buffer[i]     = (buffer[i].toInt()     * gain).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i + 1] = (buffer[i + 1].toInt() * gain).toInt().coerceIn(-32768, 32767).toShort()
            i += 2
        }
    }
}
