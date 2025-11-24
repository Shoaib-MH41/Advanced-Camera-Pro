package com.yourname.advancedcamera.features.color

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

class ColorLUTs {
    
    companion object {
        const val TAG = "ColorLUTs"
        
        // LUT Presets
        const val LUT_CINEMATIC = "cinematic"
        const val LUT_VINTAGE = "vintage" 
        const val LUT_BW = "black_white"
        const val LUT_COOL = "cool"
        const val LUT_WARM = "warm"
    }
    
    /**
     * Apply cinematic color grading (Netflix-style)
     */
    fun applyCinematicLUT(bitmap: Bitmap): Bitmap {
        return try {
            val result = bitmap.copy(bitmap.config, true)
            applyCinematicColorGrade(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Cinematic LUT failed: ${e.message}")
            bitmap
        }
    }
    
    /**
     * Apply vintage film look
     */
    fun applyVintageLUT(bitmap: Bitmap): Bitmap {
        return try {
            val result = bitmap.copy(bitmap.config, true)
            applyVintageColorGrade(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Vintage LUT failed: ${e.message}")
            bitmap
        }
    }
    
    /**
     * Apply black and white with contrast
     */
    fun applyBlackWhiteLUT(bitmap: Bitmap): Bitmap {
        return try {
            val result = bitmap.copy(bitmap.config, true)
            applyBWColorGrade(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "BW LUT failed: ${e.message}")
            bitmap
        }
    }
    
    private fun applyCinematicColorGrade(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel) 
            val b = Color.blue(pixel)
            
            // Cinematic teal and orange tones
            val newR = (r * 1.1).toInt().coerceIn(0, 255)
            val newG = (g * 0.9).toInt().coerceIn(0, 255)
            val newB = (b * 1.2).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.argb(Color.alpha(pixel), newR, newG, newB)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
    
    private fun applyVintageColorGrade(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            // Vintage sepia tones
            val newR = (r * 1.2).toInt().coerceIn(0, 255)
            val newG = (g * 1.1).toInt().coerceIn(0, 255)
            val newB = (b * 0.8).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.argb(Color.alpha(pixel), newR, newG, newB)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
    
    private fun applyBWColorGrade(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            // Convert to grayscale with contrast
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val contrastGray = ((gray - 128) * 1.5 + 128).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.argb(Color.alpha(pixel), contrastGray, contrastGray, contrastGray)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
    
    /**
     * Apply LUT by name
     */
    fun applyLUT(bitmap: Bitmap, lutName: String): Bitmap {
        return when (lutName) {
            LUT_CINEMATIC -> applyCinematicLUT(bitmap)
            LUT_VINTAGE -> applyVintageLUT(bitmap)
            LUT_BW -> applyBlackWhiteLUT(bitmap)
            else -> bitmap
        }
    }
}
