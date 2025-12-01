package com.yourname.advancedcamera.processors

import android.graphics.Bitmap
import android.util.Log
import com.yourname.advancedcamera.features.FeatureManager

class ImageProcessor {

    fun applyAdvancedProcessing(
        bitmap: Bitmap,
        mode: Int,
        lut: String,
        featureManager: FeatureManager
    ): Bitmap {

        var processedBitmap = bitmap

        try {
            // 🔥 Correct: originalBitmap کی جگہ bitmap
            val detectedScene = featureManager.detectScene(bitmap)

            when (mode) {
                0 -> processedBitmap = applyAutoModeProcessing(processedBitmap, detectedScene, featureManager)
                1 -> processedBitmap = applyProModeProcessing(processedBitmap, lut, featureManager)
                2 -> processedBitmap = applyNightModeProcessing(processedBitmap, featureManager)
                3 -> processedBitmap = applyPortraitModeProcessing(processedBitmap, featureManager)
                4 -> processedBitmap = applyVideoModeProcessing(processedBitmap, lut, featureManager)
            }

            if (featureManager.isNoiseReductionEnabled) {
                processedBitmap = featureManager.processNoiseReduction(processedBitmap)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Advanced processing failed: ${e.message}")
        }

        return processedBitmap
    }

    private fun applyAutoModeProcessing(bitmap: Bitmap, scene: String, featureManager: FeatureManager): Bitmap {
        var processed = bitmap

        when (scene) {
            "NIGHT" -> {
                val frames = ArrayList<Bitmap>()
                frames.add(processed)
                processed = featureManager.processNightVision(frames)
            }
            "PORTRAIT" -> {
                processed = featureManager.processPortraitMode(processed)
                processed = featureManager.applyColorLUT(processed, "PORTRAIT")
            }
        }

        return processed
    }

    private fun applyProModeProcessing(bitmap: Bitmap, lut: String, featureManager: FeatureManager): Bitmap {
        var processed = bitmap

        if (featureManager.isRawCaptureEnabled) {
            processed = featureManager.processRawCapture(processed)
        }

        if (featureManager.isColorLUTsEnabled) {
            processed = featureManager.applyColorLUT(processed, lut)
        }

        return processed
    }

    private fun applyNightModeProcessing(bitmap: Bitmap, featureManager: FeatureManager): Bitmap {
        return featureManager.processNightVision(listOf(bitmap))
    }

    private fun applyPortraitModeProcessing(bitmap: Bitmap, featureManager: FeatureManager): Bitmap {
        var result = featureManager.processPortraitMode(bitmap)
        result = featureManager.applyColorLUT(result, "PORTRAIT")
        return result
    }

    private fun applyVideoModeProcessing(bitmap: Bitmap, lut: String, featureManager: FeatureManager): Bitmap {
        return featureManager.applyColorLUT(bitmap, lut)
    }

    companion object {
        private const val TAG = "ImageProcessor"
    }
}
