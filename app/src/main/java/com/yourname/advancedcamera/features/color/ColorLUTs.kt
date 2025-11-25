package com.yourname.advancedcamera.features.color

import android.graphics.*
import android.util.Log

class ColorLUTs {
    
    companion object {
        private const val TAG = "ColorLUTs"
    }
    
    fun applyCinematicLUT(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "Applying Cinematic LUT")
        return applyColorFilter(bitmap, createCinematicFilter())
    }
    
    fun applyVintageLUT(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "Applying Vintage LUT")
        return applyColorFilter(bitmap, createVintageFilter())
    }
    
    fun applyModernLUT(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "Applying Modern LUT")
        return applyColorFilter(bitmap, createModernFilter())
    }
    
    fun applyNetflixLUT(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "Applying Netflix Style LUT")
        return applyColorFilter(bitmap, createNetflixFilter())
    }
    
    private fun applyColorFilter(bitmap: Bitmap, filter: ColorFilter): Bitmap {
        return try {
            val result = bitmap.copy(bitmap.config, true)
            val canvas = Canvas(result)
            val paint = Paint()
            paint.colorFilter = filter
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Color filter application failed: ${e.message}")
            bitmap
        }
    }
    
    private fun createCinematicFilter(): ColorFilter {
        // Netflix-style color grading
        val cinematicMatrix = floatArrayOf(
            1.1f, 0.0f, 0.0f, 0.0f, 0.0f,  // Red boost
            0.0f, 0.9f, 0.0f, 0.0f, 0.0f,  // Green reduce
            0.0f, 0.0f, 1.2f, 0.0f, 0.0f,  // Blue boost
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f   // Alpha unchanged
        )
        return ColorMatrixColorFilter(ColorMatrix(cinematicMatrix))
    }
    
    private fun createVintageFilter(): ColorFilter {
        // Vintage film effect
        val vintageMatrix = floatArrayOf(
            1.0f, 0.1f, 0.1f, 0.0f, 20.0f,   // Warm tones
            0.0f, 0.9f, 0.1f, 0.0f, 10.0f,   // Sepia effect
            0.1f, 0.1f, 0.8f, 0.0f, 5.0f,    // Blue reduction
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f     // Alpha unchanged
        )
        return ColorMatrixColorFilter(ColorMatrix(vintageMatrix))
    }
    
    private fun createModernFilter(): ColorFilter {
        // Modern social media look
        val modernMatrix = floatArrayOf(
            1.2f, 0.0f, 0.0f, 0.0f, -10.0f,  // Brightness and contrast
            0.0f, 1.1f, 0.0f, 0.0f, -5.0f,
            0.0f, 0.0f, 1.3f, 0.0f, -15.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
        return ColorMatrixColorFilter(ColorMatrix(modernMatrix))
    }
    
    private fun createNetflixFilter(): ColorFilter {
        // High-contrast cinematic look
        val netflixMatrix = floatArrayOf(
            1.3f, -0.1f, 0.1f, 0.0f, -20.0f,  // High contrast
            -0.1f, 1.1f, 0.0f, 0.0f, -10.0f,  // Rich colors
            0.1f, -0.1f, 1.4f, 0.0f, -25.0f,  // Deep blues
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
        return ColorMatrixColorFilter(ColorMatrix(netflixMatrix))
    }
    
    fun getAvailableLUTs(): List<String> {
        return listOf(
            "Cinematic",
            "Vintage", 
            "Modern",
            "Netflix Style"
        )
    }
}
