package com.showraw.android.presets

object PresetRepository {

    val all: List<Preset> = listOf(

        Preset(
            id = "show", name = "Show / Ao Vivo", emoji = "🎸",
            description = "Concertos, shows, estádios e festivais. Graves preservados, limiter agressivo.",
            limiterThreshold = -6f, limiterAttack = 5f, limiterRelease = 80f,
            hpfFrequency = 80f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
            stabilization = true,
            inputGainDb = 12f,
            noiseGateThreshold = -60f,          // plateia nunca silencia — gate desabilitado
            eqLowGainDb = 0f, eqMidGainDb = 0f, eqHighGainDb = 3f,
            compressorThreshold = -20f, compressorRatio = 4f,
            compressorAttack = 10f, compressorRelease = 100f, compressorMakeupDb = 6f,
        ),

        Preset(
            id = "voz", name = "Voz / Vlog", emoji = "🎤",
            description = "Vlog, entrevista, palestra ou conversa. Otimizado para a voz humana.",
            limiterThreshold = -10f, limiterAttack = 15f, limiterRelease = 150f,
            hpfFrequency = 100f, hpfRolloff = 12,
            videoResolution = Resolution.R1080P_30, estimatedMbPerMin = 130,
            contextualWarning = null,
            stabilization = true,
            inputGainDb = 9f,
            noiseGateThreshold = -35f,
            eqLowGainDb = -1f, eqMidGainDb = 2f, eqHighGainDb = 1f,
            compressorThreshold = -18f, compressorRatio = 2f,
            compressorAttack = 20f, compressorRelease = 150f, compressorMakeupDb = 4f,
        ),

        Preset(
            id = "externo", name = "Externo / Vento", emoji = "🌿",
            description = "Ao ar livre com vento — rua, natureza, viagem. HPF forte contra rajadas.",
            limiterThreshold = -10f, limiterAttack = 10f, limiterRelease = 120f,
            hpfFrequency = 150f, hpfRolloff = 24,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = "HPF agressivo (150 Hz) bloqueia vento mas corta graves de instrumentos. Para shows ao ar livre use Show / Ao Vivo.",
            stabilization = true,
            inputGainDb = 12f,
            noiseGateThreshold = -40f,
            eqLowGainDb = 0f, eqMidGainDb = 0f, eqHighGainDb = -2f,
            compressorThreshold = -20f, compressorRatio = 3f,
            compressorAttack = 15f, compressorRelease = 120f, compressorMakeupDb = 6f,
        ),

        Preset(
            id = "manual", name = "Manual (Pro)", emoji = "🎛️",
            description = "Controle total sobre todos os parâmetros de áudio e vídeo.",
            limiterThreshold = -6f, limiterAttack = 5f, limiterRelease = 80f,
            hpfFrequency = 120f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
            // DSP — valores neutros, usuário define em Ajustes Avançados
            inputGainDb = 0f,
            noiseGateThreshold = -60f,
            eqLowGainDb = 0f, eqMidGainDb = 0f, eqHighGainDb = 0f,
            compressorThreshold = -18f, compressorRatio = 3f,
            compressorAttack = 20f, compressorRelease = 150f, compressorMakeupDb = 6f,
        ),
    )

    fun findById(id: String): Preset? = all.firstOrNull { it.id == id }
}
