package com.yourname.advancedcamera.managers

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraCaptureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CaptureManager"
    }
    
    // ==================== 💾 IMAGE SAVING ====================
    
    fun saveBitmapToFile(bitmap: Bitmap, fileNamePrefix: String = "IMG"): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            
            storageDir?.mkdirs()
            
            val imageFile = File(storageDir, "${fileNamePrefix}_${timeStamp}.jpg")
            
            val output = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.flush()
            output.close()
            
            Log.d(TAG, "✅ Image saved: ${imageFile.absolutePath}")
            imageFile
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save image: ${e.message}")
            null
        }
    }
    
    fun saveBitmapToExternalStorage(bitmap: Bitmap, folderName: String, fileName: String): Boolean {
        return try {
            val storageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), folderName)
            
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            
            val imageFile = File(storageDir, "$fileName.jpg")
            val output = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            output.flush()
            output.close()
            
            Log.d(TAG, "✅ Image saved to external: ${imageFile.absolutePath}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save to external: ${e.message}")
            false
        }
    }
    
    // ==================== 🖼️ IMAGE PROCESSING ====================
    
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return if (degrees != 0f) {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
    
    fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        return try {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to crop bitmap: ${e.message}")
            bitmap
        }
    }
    
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        return try {
            val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to resize bitmap: ${e.message}")
            bitmap
        }
    }
    
    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.GRAYSCALE -> toGrayscale(bitmap)
            FilterType.SEPIA -> applySepia(bitmap)
            FilterType.INVERT -> invertColors(bitmap)
            FilterType.BRIGHTNESS -> adjustBrightness(bitmap, 1.2f)
            else -> bitmap
        }
    }
    
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applySepia(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix().apply {
            setScale(1f, 0.95f, 0.82f, 1f) // Sepia tone
        }
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun invertColors(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun adjustBrightness(bitmap: Bitmap, factor: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix().apply {
            setScale(factor, factor, factor, 1f)
        }
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    // ==================== 📊 IMAGE METADATA ====================
    
    fun getImageInfo(bitmap: Bitmap): Map<String, Any> {
        return mapOf(
            "width" to bitmap.width,
            "height" to bitmap.height,
            "sizeInBytes" to bitmap.byteCount,
            "config" to bitmap.config.toString(),
            "isMutable" to bitmap.isMutable
        )
    }
    
    // ==================== 🧹 CLEANUP ====================
    
    fun clearCache() {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("camera_temp")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear cache: ${e.message}")
        }
    }
    
    enum class FilterType {
        GRAYSCALE, SEPIA, INVERT, BRIGHTNESS, NONE
    }
}
