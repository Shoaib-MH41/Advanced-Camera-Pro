package com.yourname.advancedcamera.features

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

class FeatureManager(private val context: Context) {

    companion object {
        private const val TAG = "FeatureManager"
    }

    // یہ سب public ہیں تاکہ CameraActivity استعمال کر سکے
    var isNightVisionEnabled = false
        public set

    var isColorLUTsEnabled = true
        public set

    var isMotionDeblurEnabled = false
        public set

    var isRawCaptureEnabled = false
        public set

    var isUltraZoomEnabled = true
        public set

    var isHDREnabled = false
        public set

    private val cameraCapabilities = mutableListOf<String>()

    fun initializeFeatures() {
        Log.d(TAG, "Initializing advanced features...")
        checkCameraCapabilities()

        isNightVisionEnabled = checkNightVisionSupport()
        isColorLUTsEnabled = true
        isMotionDeblurEnabled = checkMotionDeblurSupport()
        isRawCaptureEnabled = checkRawCaptureSupport()
        isUltraZoomEnabled = true
        // software zoom
        isHDREnabled = checkHDRSupport()
    }

    fun getAvailableFeatures(): List<String> {
        val list = mutableListOf<String>()
        if (isNightVisionEnabled) list.add("Night Vision")
        if (isColorLUTsEnabled) list.add("Cinematic LUTs")
        if (isMotionDeblurEnabled) list.add("Motion Deblur")
        if (isRawCaptureEnabled) list.add("RAW Capture")
        if (isUltraZoomEnabled) list.add("Ultra Zoom")
        if (isHDREnabled) list.add("HDR Fusion")
        return list
    }

    private fun checkCameraCapabilities() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return

            val caps = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return

            caps.forEach { cap ->
                when (cap) {
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> cameraCapabilities.add("MANUAL_SENSOR")
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> cameraCapabilities.add("MANUAL_POST")
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> cameraCapabilities.add("RAW")
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> cameraCapabilities.add("BURST")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capability check failed", e)
        }
    }

    private fun checkNightVisionSupport() = cameraCapabilities.contains("MANUAL_SENSOR")
    private fun checkMotionDeblurSupport() = cameraCapabilities.contains("BURST")
    private fun checkRawCaptureSupport() = cameraCapabilities.contains("RAW")
    private fun checkHDRSupport() = cameraCapabilities.contains("MANUAL_SENSOR")
}
