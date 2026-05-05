package com.showraw.android.video

import android.hardware.camera2.CaptureRequest

enum class WbMode(val label: String, val awbMode: Int) {
    AUTO       ("AWB",   CaptureRequest.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT   ("5600K", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY     ("6500K", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    TUNGSTEN   ("3200K", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("4000K", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
}
