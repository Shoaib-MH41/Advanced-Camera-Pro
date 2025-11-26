package com.yourname.advancedcamera.features

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

class FeatureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FeatureManager"
    }
    
    // Feature flags
    var isNightVisionEnabled = true
    var isColorLUTsEnabled = true
    var isMotionDeblurEnabled = false
    var isRawCaptureEnabled = false
    var isUltraZoomEnabled = false
    var isHDREnalbed = false
    
    // Feature capabilities
    private var cameraCapabilities = mutableListOf<String>()
    
    fun initializeFeatures() {
        Log.d(TAG, "Initializing advanced features...")
        
        // Check camera capabilities
        checkCameraCapabilities()
        
        // Initialize features based on capabilities
        isNightVisionEnabled = checkNightVisionSupport()
        isColorLUTsEnabled = true // Always enabled (software-based)
        isMotionDeblurEnabled = checkMotionDeblurSupport()
        isRawCaptureEnabled = checkRawCaptureSupport()
        isUltraZoomEnabled = checkUltraZoomSupport()
        isHDREnalbed = checkHDRSupport()
        
        Log.d(TAG, "Features initialized: ${getAvailableFeatures()}")
    }
    
    fun getAvailableFeatures(): List<String> {
        val features = mutableListOf<String>()
        
        if (isNightVisionEnabled) features.add("Night Vision")
        if (isColorLUTsEnabled) features.add("Cinematic LUTs") 
        if (isMotionDeblurEnabled) features.add("Motion Deblur")
        if (isRawCaptureEnabled) features.add("RAW Capture")
        if (isUltraZoomEnabled) features.add("Ultra Zoom")
        if (isHDREnalbed) features.add("HDR Fusion")
        
        return features
    }
    
    fun getFeatureStatus(): Map<String, Boolean> {
        return mapOf(
            "Night Vision" to isNightVisionEnabled,
            "Cinematic LUTs" to isColorLUTsEnabled,
            "Motion Deblur" to isMotionDeblurEnabled,
            "RAW Capture" to isRawCaptureEnabled,
            "Ultra Zoom" to isUltraZoomEnabled,
            "HDR Fusion" to isHDREnalbed
        )
    }
    
    private fun checkCameraCapabilities() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = getBackCameraId(cameraManager)
            
            if (cameraId != null) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                
                // Check available capabilities
                val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                capabilities?.forEach { capability ->
                    when (capability) {
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> 
                            cameraCapabilities.add("Manual Sensor")
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> 
                            cameraCapabilities.add("Manual Processing")
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> 
                            cameraCapabilities.add("RAW")
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> 
                            cameraCapabilities.add("Burst Capture")
                    }
                }
                
                Log.d(TAG, "Camera capabilities: $cameraCapabilities")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera capabilities: ${e.message}")
        }
    }
    
    private fun getBackCameraId(cameraManager: CameraManager): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun checkNightVisionSupport(): Boolean {
        // Check if device has good low-light capabilities
        return cameraCapabilities.contains("Manual Sensor") 
    }
    
    private fun checkMotionDeblurSupport(): Boolean {
        // Motion deblur requires burst capture capability
        return cameraCapabilities.contains("Burst Capture")
    }
    
    private fun checkRawCaptureSupport(): Boolean {
        // Check RAW capability
        return cameraCapabilities.contains("RAW")
    }
    
    private fun checkUltraZoomSupport(): Boolean {
        // Ultra zoom requires multiple cameras or high resolution sensor
        return true // Software-based zoom is always available
    }
    
    private fun checkHDRSupport(): Boolean {
        // HDR requires manual control capabilities
        return cameraCapabilities.contains("Manual Sensor")
    }
    
    fun getCameraCapabilities(): List<String> {
        return cameraCapabilities
    }
    
    fun isFeatureAvailable(featureName: String): Boolean {
        return when (featureName) {
            "Night Vision" -> isNightVisionEnabled
            "Cinematic LUTs" -> isColorLUTsEnabled
            "Motion Deblur" -> isMotionDeblurEnabled
            "RAW Capture" -> isRawCaptureEnabled
            "Ultra Zoom" -> isUltraZoomEnabled
            "HDR Fusion" -> isHDREnalbed
            else -> false
        }
    }
}
