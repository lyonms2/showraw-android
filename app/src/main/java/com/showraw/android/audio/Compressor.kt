package com.showraw.android.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Compressor dinâmico stereo-linked com detector de envoltória.
 * Posição na cadeia: EQ → NoiseGate → [Compressor] → Limiter
 *
 * Stereo-linked: o nível é detectado como max(|L|, |R|) — ambos os canais
 * recebem o mesmo ganho de redução, preservando a imagem estéreo.
 */
class Compressor {

    private var thresholdLinear = 1f
    private var slope           = 0f   // (1 - 1/ratio)
    private var attackCoeff     = 0f
    private var releaseCoeff    = 0f
    private var makeupFactor    = 1f
    private var envelope        = 1f   // ganho atual (1 = sem redução)

    fun configure(
        thresholdDb: Float,
        ratio:       Float,
        attackMs:    Float,
        releaseMs:   Float,
        makeupGainDb: Float,
    ) {
        thresholdLinear = 10f.pow(thresholdDb / 20f)
        slope           = 1f - 1f / ratio.coerceAtLeast(1f)
        val sr          = 48_000f
        attackCoeff     = exp(-1000.0 / (attackMs  * sr)).toFloat()
        releaseCoeff    = exp(-1000.0 / (releaseMs * sr)).toFloat()
        makeupFactor    = 10f.pow(makeupGainDb / 20f)
        envelope        = 1f
    }

    fun gainReductionDb(): Float =
        if (envelope < 1f) -20f * log10(envelope.coerceAtLeast(1e-6f)) else 0f

    fun processBuffer(buffer: ShortArray, size: Int) {
        val thresh = thresholdLinear
        val sl     = slope
        val atk    = attackCoeff
        val rel    = releaseCoeff
        val mkp    = makeupFactor
        var env    = envelope

        var i = 0
        while (i + 1 < size) {
            val l = buffer[i]
            val r = buffer[i + 1]

            // Detecção stereo-linked: nível = max(|L|, |R|) normalizado
            val level = max(abs(l.toInt()), abs(r.toInt())) / 32768f

            // Computar ganho alvo
            val targetGain = if (level > thresh && level > 0f) {
                val overDb = 20f * log10(level / thresh)
                10f.pow(-overDb * sl / 20f)
            } else 1f

            // Envoltória: attack quando gain cai, release quando sobe
            env = if (targetGain < env) {
                atk * env + (1f - atk) * targetGain
            } else {
                rel * env + (1f - rel) * targetGain
            }

            val gain = env * mkp
            buffer[i]     = (l * gain).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i + 1] = (r * gain).toInt().coerceIn(-32768, 32767).toShort()
            i += 2
        }
        envelope = env
    }
}
