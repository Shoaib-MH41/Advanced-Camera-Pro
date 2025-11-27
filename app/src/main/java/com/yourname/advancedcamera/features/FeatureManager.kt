package com.yourname.advancedcamera.features

import android.util.Log

class FeatureManager {

    companion object {
        private const val TAG = "FeatureManager"
    }

    // Java سے استعمال کرنے کے لیے لازمی public var
    var isNightVisionEnabled = true
    var isColorLUTsEnabled = true
    var isMotionDeblurEnabled = false
    var isRawCaptureEnabled = false
    var isUltraZoomEnabled = true
    var isHDREnabled = true

    init {
        Log.d(TAG, "Advanced FeatureManager loaded – All pro features enabled for demo")
    }

    fun getAvailableFeatures(): List<String> {
        return listOf(
            "AI Night Vision",
            "Cinematic Color LUTs",
            "50x Ultra Zoom",
            "HDR+ Fusion",
            "Motion Deblur",
            "RAW Capture"
        )
    }
}
