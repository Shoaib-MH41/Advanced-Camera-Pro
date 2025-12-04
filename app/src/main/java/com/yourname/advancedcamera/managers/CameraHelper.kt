package com.yourname.advancedcamera.managers

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import java.text.SimpleDateFormat
import java.util.*

object CameraHelper {
    
    // ==================== 📐 IMAGE PROCESSING ====================
    
    fun rotateBitmap(bitmap: Bitmap, rotation: Int): Bitmap {
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
    
    fun calculateTapArea(
        x: Float, 
        y: Float, 
        areaSize: Float,
        viewWidth: Int,
        viewHeight: Int
    ): Rect {
        val area = areaSize.toInt()
        
        if (viewWidth == 0 || viewHeight == 0) {
            return Rect(-1000, -1000, 1000, 1000)
        }
        
        // Convert touch coordinates to sensor coordinates (-1000 to 1000)
        val sensorX = ((x / viewWidth) * 2000 - 1000).toInt()
        val sensorY = ((y / viewHeight) * 2000 - 1000).toInt()
        
        val left = (sensorX - area / 2).coerceIn(-1000, 1000)
        val top = (sensorY - area / 2).coerceIn(-1000, 1000)
        val right = (left + area).coerceIn(-1000, 1000)
        val bottom = (top + area).coerceIn(-1000, 1000)
        
        return Rect(left, top, right, bottom)
    }
    
    // ==================== 📏 SIZE UTILITIES ====================
    
    fun chooseOptimalSize(
        choices: Array<Size>,
        width: Int,
        height: Int,
        maxWidth: Int = 1920,
        maxHeight: Int = 1080
    ): Size {
        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        
        val aspectRatio = width.toFloat() / height.toFloat()
        
        for (option in choices) {
            // Limit size for performance
            if (option.width <= maxWidth && option.height <= maxHeight) {
                val optionAspect = option.width.toFloat() / option.height.toFloat()
                
                // Allow small aspect ratio differences
                if (Math.abs(optionAspect - aspectRatio) <= 0.1) {
                    if (option.width >= width && option.height >= height) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }
        }
        
        return when {
            bigEnough.isNotEmpty() -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.isNotEmpty() -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> choices.firstOrNull() ?: Size(640, 480)
        }
    }
    
    // ==================== 📁 FILE UTILITIES ====================
    
    fun generateImageFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_${timeStamp}.jpg"
    }
    
    fun generateVideoFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "VID_${timeStamp}.mp4"
    }
    
    // ==================== 🔧 CAMERA UTILITIES ====================
    
    fun getFacingDescription(facing: Int?): String {
        return when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            else -> "External"
        }
    }
    
    fun isBackCamera(facing: Int?): Boolean {
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }
    
    fun isFrontCamera(facing: Int?): Boolean {
        return facing == CameraCharacteristics.LENS_FACING_FRONT
    }
    
    // ==================== 🎛️ FLASH UTILITIES ====================
    
    fun getFlashModeDescription(mode: String): String {
        return when (mode.uppercase()) {
            "AUTO" -> "Auto Flash"
            "ON" -> "Flash On"
            "OFF" -> "Flash Off"
            "TORCH" -> "Torch"
            else -> "Auto"
        }
    }
    
    // ==================== ⚙️ COMPARATOR ====================
    
    class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
}
