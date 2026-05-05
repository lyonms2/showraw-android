package com.showraw.android.presets

enum class EqFilterType { LOW_SHELF, PEAKING, HIGH_SHELF }

data class EqBand(
    val type: EqFilterType = EqFilterType.PEAKING,
    val freq: Float = 1_000f,   // Hz
    val gainDb: Float = 0f,     // dB
    val q: Float = 0.707f,      // bandwidth (used for PEAKING; shelves use sqrt(2)/2 internally)
)

fun defaultEqBands() = listOf(
    EqBand(EqFilterType.LOW_SHELF,  200f,  0f, 0.707f),
    EqBand(EqFilterType.PEAKING,   1_000f, 0f, 0.707f),
    EqBand(EqFilterType.HIGH_SHELF, 8_000f, 0f, 0.707f),
)
