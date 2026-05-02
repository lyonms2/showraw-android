package com.showraw.android.presets

object PresetRepository {

    val all: List<Preset> = listOf(
        Preset(
            id = "show", name = "Show / Concerto", emoji = "🎸",
            description = "Ambientes de show com volume alto. Limiter agressivo, HPF 120Hz.",
            limiterThreshold = -6f, limiterAttack = 5f, limiterRelease = 80f,
            hpfFrequency = 120f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
        ),
        Preset(
            id = "sport", name = "Esporte / Estádio", emoji = "🏟️",
            description = "Multidões, torcida, arquibancada. Alta taxa de quadros para movimento.",
            limiterThreshold = -8f, limiterAttack = 8f, limiterRelease = 100f,
            hpfFrequency = 100f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_60, estimatedMbPerMin = 600,
            contextualWarning = "Gera ~600 MB/min. Verifique o espaço disponível.",
        ),
        Preset(
            id = "outdoor", name = "Ao ar livre", emoji = "🌳",
            description = "Parques, ruas, festivais. HPF mais alto para cortar vento.",
            limiterThreshold = -10f, limiterAttack = 10f, limiterRelease = 120f,
            hpfFrequency = 150f, hpfRolloff = 24,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = "Otimizado para voz com vento. Para instrumentos, use Ambiente interno.",
        ),
        Preset(
            id = "vlog", name = "Vlog / Rua", emoji = "📱",
            description = "Conteúdo de rua, fala próxima ao celular. Rolloff suave.",
            limiterThreshold = -10f, limiterAttack = 10f, limiterRelease = 100f,
            hpfFrequency = 120f, hpfRolloff = 12,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
        ),
        Preset(
            id = "indoor", name = "Ambiente interno", emoji = "🏠",
            description = "Acústica fechada, reverberação natural. HPF suave para preservar graves.",
            limiterThreshold = -12f, limiterAttack = 15f, limiterRelease = 150f,
            hpfFrequency = 80f, hpfRolloff = 12,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
        ),
        Preset(
            id = "bar", name = "Bar / Restaurante", emoji = "🍺",
            description = "Ambiente com ruído de fundo moderado. 1080p para economizar espaço.",
            limiterThreshold = -8f, limiterAttack = 8f, limiterRelease = 100f,
            hpfFrequency = 100f, hpfRolloff = 18,
            videoResolution = Resolution.R1080P_30, estimatedMbPerMin = 130,
            contextualWarning = "Vídeo em 1080p para economizar espaço.",
        ),
        Preset(
            id = "talk", name = "Palestra / Evento", emoji = "🎤",
            description = "Voz humana predominante. Limiter gentil, graves preservados.",
            limiterThreshold = -14f, limiterAttack = 20f, limiterRelease = 200f,
            hpfFrequency = 80f, hpfRolloff = 12,
            videoResolution = Resolution.R1080P_30, estimatedMbPerMin = 130,
            contextualWarning = null,
        ),
        Preset(
            id = "manual", name = "Manual (Pro)", emoji = "🎛️",
            description = "Controle total sobre todos os parâmetros de áudio e vídeo.",
            limiterThreshold = -6f, limiterAttack = 5f, limiterRelease = 80f,
            hpfFrequency = 120f, hpfRolloff = 18,
            videoResolution = Resolution.R4K_30, estimatedMbPerMin = 350,
            contextualWarning = null,
        ),
    )

    fun findById(id: String): Preset? = all.firstOrNull { it.id == id }
}
