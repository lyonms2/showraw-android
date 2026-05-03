package com.showraw.android.audio

import kotlin.math.*

/**
 * Filtro IIR Butterworth high-pass.
 * Coeficientes calculados uma vez em configure() — nunca durante processamento.
 *
 * Rolloffs corretos:
 *   12 dB/oct → 2ª ordem  = 1 biquad  (Q = √2/2)
 *   18 dB/oct → 3ª ordem  = 1ª ordem + 1 biquad (Q = 1.0)
 *   24 dB/oct → 4ª ordem  = 2 biquads (Q1 = 0.5412, Q2 = 1.3066 — Butterworth exato)
 */
class HighPassFilter(
    frequencyHz: Float = 120f,
    rolloffDbOct: Int  = 18,
    sampleRate: Int    = 48_000,
) {
    private data class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
        var x1: Double = 0.0, var x2: Double = 0.0,
        var y1: Double = 0.0, var y2: Double = 0.0,
    )

    // Seção de 1ª ordem usada apenas no modo 18 dB/oct
    private data class FirstOrder(
        val b0: Double, val b1: Double, val a1: Double,
        var x1: Double = 0.0, var y1: Double = 0.0,
    )

    private var stages     = mutableListOf<Biquad>()
    private var firstStage: FirstOrder? = null

    init { configure(frequencyHz, rolloffDbOct, sampleRate) }

    fun configure(frequencyHz: Float, rolloffDbOct: Int, sampleRate: Int = 48_000) {
        stages.clear()
        firstStage = null
        val fc = frequencyHz.toDouble()
        val fs = sampleRate.toDouble()
        when (rolloffDbOct) {
            12   -> stages.add(biquad(fc, fs, Q_2ND))
            18   -> {
                firstStage = firstOrder(fc, fs)
                stages.add(biquad(fc, fs, Q_3RD_2ND_SECTION))
            }
            24   -> {
                stages.add(biquad(fc, fs, Q_4TH_WIDE))
                stages.add(biquad(fc, fs, Q_4TH_NARROW))
            }
            else -> stages.add(biquad(fc, fs, Q_2ND))
        }
    }

    fun processBuffer(buffer: ShortArray, size: Int) {
        for (i in 0 until size) {
            var s = buffer[i] / 32768.0

            firstStage?.let { fo ->
                val y = fo.b0 * s + fo.b1 * fo.x1 - fo.a1 * fo.y1
                fo.x1 = s; fo.y1 = y; s = y
            }

            for (bq in stages) {
                val y = bq.b0 * s + bq.b1 * bq.x1 + bq.b2 * bq.x2 - bq.a1 * bq.y1 - bq.a2 * bq.y2
                bq.x2 = bq.x1; bq.x1 = s; bq.y2 = bq.y1; bq.y1 = y; s = y
            }

            buffer[i] = (s * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    // 1ª ordem HPF via transformada bilinear: H(s) = s/(s + ωc)
    private fun firstOrder(fc: Double, fs: Double): FirstOrder {
        val K  = tan(PI * fc / fs)
        val b0 =  1.0 / (1.0 + K)
        val b1 = -1.0 / (1.0 + K)
        val a1 = (K - 1.0) / (K + 1.0)
        return FirstOrder(b0, b1, a1)
    }

    private fun biquad(fc: Double, fs: Double, q: Double): Biquad {
        val w0    = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * q)
        val cosW  = cos(w0)
        val a0    = 1.0 + alpha
        return Biquad(
            b0 =  (1.0 + cosW) / (2.0 * a0),
            b1 = -(1.0 + cosW) / a0,
            b2 =  (1.0 + cosW) / (2.0 * a0),
            a1 = -2.0 * cosW   / a0,
            a2 = (1.0 - alpha) / a0,
        )
    }

    companion object {
        private const val Q_2ND              = 0.7071067811865476  // √2/2 — 2ª ordem Butterworth
        private const val Q_3RD_2ND_SECTION  = 1.0                 // seção complexa do 3º ordem Butterworth
        private const val Q_4TH_WIDE         = 0.5411961001338     // par de polos largo do 4º ordem
        private const val Q_4TH_NARROW       = 1.3065629648763     // par de polos estreito do 4º ordem
    }
}
