package com.showraw.android.presets

enum class Resolution(val label: String) {
    R4K_30("4K / 30fps"),
    R4K_60("4K / 60fps"),
    R1080P_30("1080p / 30fps"),
    R1080P_60("1080p / 60fps"),
}

data class Preset(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val limiterThreshold: Float,     // dBFS  — ex: -6f
    val limiterAttack: Float,        // ms    — ex: 5f
    val limiterRelease: Float,       // ms    — ex: 80f
    val hpfFrequency: Float,         // Hz    — ex: 120f
    val hpfRolloff: Int,             // dB/oct: 12, 18 ou 24
    val videoResolution: Resolution,
    val estimatedMbPerMin: Int,
    val contextualWarning: String?,  // null = sem aviso
    // DSP expansions
    val noiseGateThreshold: Float  = -60f,  // dBFS; ≤ -59 = desabilitado
    val eqBands: List<EqBand>      = defaultEqBands(),
    // Câmera
    val stabilization: Boolean     = false,
    // Ganho de entrada (compensa ausência de AGC ao usar UNPROCESSED)
    val inputGainDb: Float         = 6f,    // dB aplicados antes do HPF
    // Compressor (entre EQ e Limiter)
    val compressorThreshold: Float = -18f,  // dBFS
    val compressorRatio: Float     = 3f,    // ex: 3f = 3:1
    val compressorAttack: Float    = 20f,   // ms
    val compressorRelease: Float   = 150f,  // ms
    val compressorMakeupDb: Float  = 6f,    // dB de makeup gain
    // Gravação
    val maxDurationMinutes: Int    = 0,     // 0 = ilimitado
)
