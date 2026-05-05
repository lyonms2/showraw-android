package com.showraw.android.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Peak limiter brick-wall com lookahead de 5ms.
 * O lookahead funciona via delay buffer: o ganho é computado olhando o sinal
 * FUTURO (240 frames à frente) e aplicado no sinal ATRASADO — garantindo que
 * o ganho já está reduzido quando o transiente chega na saída.
 *
 * Operar em FloatArray: sem conversão int16 intermediária, sem hard-clip parcial.
 */
class Limiter(
    private var thresholdDbFs: Float = -6f,
    private var attackMs: Float      = 5f,
    private var releaseMs: Float     = 80f,
    private val sampleRate: Int      = 48_000,
    private val lookaheadMs: Float   = 5f,
) {
    private var gainReduction = 1f

    private val lookaheadFrames = (lookaheadMs * sampleRate / 1000f).toInt()  // 240 @ 48kHz
    private val delayBuf = FloatArray(lookaheadFrames * 2)
    private var delayPos = 0

    private val threshold    get() = dbToLinear(thresholdDbFs)
    private val attackCoeff  get() = computeCoeff(attackMs)
    private val releaseCoeff get() = computeCoeff(releaseMs)

    fun configure(thresholdDbFs: Float, attackMs: Float, releaseMs: Float) {
        this.thresholdDbFs = thresholdDbFs
        this.attackMs      = attackMs
        this.releaseMs     = releaseMs
        gainReduction = 1f
        delayBuf.fill(0f)
        delayPos = 0
    }

    fun processBuffer(buffer: FloatArray, size: Int) {
        val thr      = threshold
        val attack   = attackCoeff
        val release  = releaseCoeff
        val delayLen = delayBuf.size

        var i = 0
        while (i + 1 < size) {
            val futureL = buffer[i]
            val futureR = buffer[i + 1]
            val level   = max(abs(futureL), abs(futureR))

            gainReduction = if (level > thr) {
                val target = thr / level
                gainReduction + attack * (target - gainReduction)
            } else {
                min(1f, gainReduction + release * (1f - gainReduction))
            }

            buffer[i]     = delayBuf[delayPos]     * gainReduction
            buffer[i + 1] = delayBuf[delayPos + 1] * gainReduction

            delayBuf[delayPos]     = futureL
            delayBuf[delayPos + 1] = futureR
            delayPos = (delayPos + 2) % delayLen

            i += 2
        }
    }

    fun gainReductionDb(): Float = 20f * log10(gainReduction.coerceAtLeast(1e-6f))

    private fun dbToLinear(db: Float) = 10f.pow(db / 20f)

    private fun computeCoeff(timeMs: Float): Float {
        val timeSamples = timeMs * sampleRate / 1000f
        return if (timeSamples <= 0f) 1f else (1f - exp(-1.0 / timeSamples).toFloat())
    }
}
