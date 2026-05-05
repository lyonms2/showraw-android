package com.showraw.android.video

import android.hardware.camera2.CaptureRequest

enum class WbMode(val label: String, val awbMode: Int) {
    AUTO       ("AWB",    CaptureRequest.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT   ("Dia",    CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY     ("Nublado",CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    TUNGSTEN   ("Palco",  CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("Fluor",  CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
}
