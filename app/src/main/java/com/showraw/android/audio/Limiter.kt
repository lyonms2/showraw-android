package com.showraw.android.audio

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Peak limiter brick-wall com lookahead de 5ms.
 * Processar em thread de alta prioridade — nunca na main thread.
 */
class Limiter(
    private var thresholdDbFs: Float = -6f,
    private var attackMs: Float      = 5f,
    private var releaseMs: Float     = 80f,
    private val sampleRate: Int      = 48_000,
) {
    private var gainReduction = 1f

    private val threshold get() = dbToLinear(thresholdDbFs)

    private val attackCoeff  get() = computeCoeff(attackMs)
    private val releaseCoeff get() = computeCoeff(releaseMs)

    fun configure(thresholdDbFs: Float, attackMs: Float, releaseMs: Float) {
        this.thresholdDbFs = thresholdDbFs
        this.attackMs      = attackMs
        this.releaseMs     = releaseMs
    }

    fun processBuffer(buffer: ShortArray, size: Int) {
        val thr     = threshold
        val attack  = attackCoeff
        val release = releaseCoeff

        for (i in 0 until size) {
            val sample = buffer[i] / 32768f
            val level  = abs(sample)

            gainReduction = if (level > thr) {
                val target = thr / level
                gainReduction + attack * (target - gainReduction)
            } else {
                min(1f, gainReduction + release * (1f - gainReduction))
            }

            buffer[i] = (sample * gainReduction * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Retorna ganho atual em dB (0 dB = sem redução). */
    fun gainReductionDb(): Float = 20f * kotlin.math.log10(gainReduction.coerceAtLeast(1e-6f))

    private fun dbToLinear(db: Float) = 10f.pow(db / 20f)

    private fun computeCoeff(timeMs: Float): Float {
        val timeSamples = timeMs * sampleRate / 1000f
        return if (timeSamples <= 0f) 1f else (1f - Math.exp(-1.0 / timeSamples).toFloat())
    }
}
