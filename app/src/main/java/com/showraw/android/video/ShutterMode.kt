package com.showraw.android.video

enum class ShutterMode(val label: String, val exposureNs: Long) {
    AUTO   ("Auto",  0L),
    S1_50  ("1/50",  20_000_000L),
    S1_60  ("1/60",  16_666_667L),
    S1_100 ("1/100", 10_000_000L),
    S1_120 ("1/120",  8_333_333L),
}
