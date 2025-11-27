package com.yourname.advancedcamera.features

import android.util.Log

class FeatureManager {

    companion object {
        private const val TAG = "FeatureManager"
    }

    // ✅ Java سے access کے لیے @JvmField استعمال
    @JvmField var isNightVisionEnabled = true
    @JvmField var isColorLUTsEnabled = true
    @JvmField var isMotionDeblurEnabled = false
    @JvmField var isRawCaptureEnabled = false
    @JvmField var isUltraZoomEnabled = true
    @JvmField var isHDREnabled = true

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

    // ✅ Getter methods inside the class (correct placement)
    fun getIsNightVisionEnabled(): Boolean = isNightVisionEnabled
    fun getIsColorLUTsEnabled(): Boolean = isColorLUTsEnabled
    fun getIsMotionDeblurEnabled(): Boolean = isMotionDeblurEnabled
    fun getIsRawCaptureEnabled(): Boolean = isRawCaptureEnabled
    fun getIsUltraZoomEnabled(): Boolean = isUltraZoomEnabled
    fun getIsHDREnabled(): Boolean = isHDREnabled
}
