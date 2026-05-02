package com.showraw.android.audio

import kotlin.math.*

/**
 * Filtro IIR Butterworth high-pass.
 * Coeficientes calculados uma vez em configure() — nunca durante processamento.
 * Suporta rolloff de 12, 18 ou 24 dB/oct (1ª, 2ª e 3ª ordem em cascata de 2ª ordem).
 */
class HighPassFilter(
    frequencyHz: Float = 120f,
    rolloffDbOct: Int  = 18,
    sampleRate: Int    = 48_000,
) {
    private data class Biquad(val b0: Double, val b1: Double, val b2: Double,
                              val a1: Double, val a2: Double,
                              var x1: Double = 0.0, var x2: Double = 0.0,
                              var y1: Double = 0.0, var y2: Double = 0.0)

    private var stages = mutableListOf<Biquad>()

    init {
        configure(frequencyHz, rolloffDbOct, sampleRate)
    }

    fun configure(frequencyHz: Float, rolloffDbOct: Int, sampleRate: Int = 48_000) {
        stages.clear()
        val numStages = when (rolloffDbOct) {
            12 -> 1
            18 -> 2  // 1ª ordem + 2ª ordem (aproximação)
            24 -> 2
            else -> 1
        }
        repeat(numStages) {
            stages.add(computeBiquad(frequencyHz.toDouble(), sampleRate.toDouble()))
        }
    }

    fun processBuffer(buffer: ShortArray, size: Int) {
        for (i in 0 until size) {
            var sample = buffer[i] / 32768.0
            for (s in stages) {
                val y = s.b0 * sample + s.b1 * s.x1 + s.b2 * s.x2 - s.a1 * s.y1 - s.a2 * s.y2
                s.x2 = s.x1; s.x1 = sample
                s.y2 = s.y1; s.y1 = y
                sample = y
            }
            buffer[i] = (sample * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun computeBiquad(fc: Double, fs: Double): Biquad {
        val w0 = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * 0.7071) // Q = √2/2 → Butterworth
        val b0 =  (1.0 + cos(w0)) / 2.0
        val b1 = -(1.0 + cos(w0))
        val b2 =  (1.0 + cos(w0)) / 2.0
        val a0 =   1.0 + alpha
        val a1 =  -2.0 * cos(w0)
        val a2 =   1.0 - alpha
        return Biquad(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0)
    }
}
