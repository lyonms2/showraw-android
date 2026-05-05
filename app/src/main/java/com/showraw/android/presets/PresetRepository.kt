package com.showraw.android.presets

object PresetRepository {

    val all: List<Preset> = listOf(

        Preset(
            id = "show", name = "Show / Concerto", emoji = "🎸",
            description = "Ambientes de show com volume alto. Limiter agressivo, HPF 80Hz preserva graves.",
            limiterThreshold = -6f, limiterAttack = 5f, limiterRelease = 80f,
            hpfFrequency = 80f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
            stabilization = true,
            // DSP
            inputGainDb = 12f,
            noiseGateThreshold = -60f,          // plateia nunca silencia — gate desabilitado
            eqLowGainDb = 0f, eqMidGainDb = 0f, eqHighGainDb = 3f,  // mid 1kHz é presença, não barro
            compressorThreshold = -20f, compressorRatio = 4f,
            compressorAttack = 10f, compressorRelease = 100f, compressorMakeupDb = 6f,
        ),

        Preset(
            id = "sport", name = "Esporte / Estádio", emoji = "🏟️",
            description = "Multidões, torcida, arquibancada. Alta taxa de quadros para movimento.",
            limiterThreshold = -8f, limiterAttack = 8f, limiterRelease = 100f,
            hpfFrequency = 80f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_60, estimatedMbPerMin = 600,
            contextualWarning = "Gera ~600 MB/min. Verifique o espaço disponível.",
            stabilization = true,
            // DSP
            inputGainDb = 12f,
            noiseGateThreshold = -60f,          // torcida nunca silencia — gate desabilitado
            eqLowGainDb = 0f, eqMidGainDb = -1f, eqHighGainDb = 2f,
            compressorThreshold = -20f, compressorRatio = 4f,
            compressorAttack = 10f, compressorRelease = 100f, compressorMakeupDb = 6f,
        ),

        Preset(
            id = "outdoor", name = "Ao ar livre", emoji = "🌳",
            description = "Parques, ruas, festivais. HPF mais alto para cortar vento.",
            limiterThreshold = -10f, limiterAttack = 10f, limiterRelease = 120f,
            hpfFrequency = 150f, hpfRolloff = 24,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = "Otimizado para voz com vento. Para instrumentos, use Ambiente interno.",
            stabilization = true,
            // DSP
            inputGainDb = 12f,
            noiseGateThreshold = -40f,
            eqLowGainDb = 0f, eqMidGainDb = 0f, eqHighGainDb = -2f,
            compressorThreshold = -20f, compressorRatio = 3f,
            compressorAttack = 15f, compressorRelease = 120f, compressorMakeupDb = 6f,
        ),

        Preset(
            id = "vlog", name = "Vlog / Rua", emoji = "📱",
            description = "Conteúdo de rua, fala próxima ao celular. Rolloff suave.",
            limiterThreshold = -10f, limiterAttack = 10f, limiterRelease = 100f,
            hpfFrequency = 120f, hpfRolloff = 12,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
            stabilization = true,
            // DSP
            inputGainDb = 9f,
            noiseGateThreshold = -35f,
            eqLowGainDb = 0f, eqMidGainDb = 2f, eqHighGainDb = 1f,
            compressorThreshold = -18f, compressorRatio = 2.5f,
            compressorAttack = 20f, compressorRelease = 150f, compressorMakeupDb = 4f,
        ),

        Preset(
            id = "indoor", name = "Ambiente interno", emoji = "🏠",
            description = "Acústica fechada, reverberação natural. HPF suave para preservar graves.",
            limiterThreshold = -12f, limiterAttack = 15f, limiterRelease = 150f,
            hpfFrequency = 80f, hpfRolloff = 12,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
            // DSP
            inputGainDb = 9f,
            noiseGateThreshold = -40f,
            eqLowGainDb = 1f, eqMidGainDb = 0f, eqHighGainDb = 0f,
            compressorThreshold = -20f, compressorRatio = 2f,
            compressorAttack = 20f, compressorRelease = 200f, compressorMakeupDb = 4f,
        ),

        Preset(
            id = "bar", name = "Bar / Restaurante", emoji = "🍺",
            description = "Ambiente com ruído de fundo moderado. 1080p para economizar espaço.",
            limiterThreshold = -8f, limiterAttack = 8f, limiterRelease = 100f,
            hpfFrequency = 100f, hpfRolloff = 18,
            videoResolution = Resolution.R1080P_30, estimatedMbPerMin = 130,
            contextualWarning = "Vídeo em 1080p para economizar espaço.",
            // DSP
            inputGainDb = 12f,
            noiseGateThreshold = -40f,
            eqLowGainDb = 0f, eqMidGainDb = -1f, eqHighGainDb = 2f,
            compressorThreshold = -18f, compressorRatio = 3f,
            compressorAttack = 15f, compressorRelease = 120f, compressorMakeupDb = 6f,
        ),

        Preset(
            id = "talk", name = "Palestra / Evento", emoji = "🎤",
            description = "Voz humana predominante. Limiter gentil, graves preservados.",
            limiterThreshold = -14f, limiterAttack = 20f, limiterRelease = 200f,
            hpfFrequency = 80f, hpfRolloff = 12,
            videoResolution = Resolution.R1080P_30, estimatedMbPerMin = 130,
            contextualWarning = null,
            // DSP
            inputGainDb = 9f,
            noiseGateThreshold = -35f,
            eqLowGainDb = -1f, eqMidGainDb = 2f, eqHighGainDb = 1f,
            compressorThreshold = -20f, compressorRatio = 2f,
            compressorAttack = 25f, compressorRelease = 200f, compressorMakeupDb = 4f,
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
