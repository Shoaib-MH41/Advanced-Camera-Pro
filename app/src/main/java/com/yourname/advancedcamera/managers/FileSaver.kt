package com.yourname.advancedcamera.managers

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.OutputStream

class FileSaver(private val context: Context) {
    
    fun saveImage(bitmap: Bitmap, lut: String): Boolean {
        return try {
            val filename = "DSLR_${System.currentTimeMillis()}_$lut.jpg"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/AdvancedCameraPro")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                var outputStream: OutputStream? = null
                try {
                    outputStream = context.contentResolver.openOutputStream(uri)
                    outputStream?.let { 
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                        Log.d(TAG, "✅ Image saved to Gallery: $filename")
                    }
                } finally {
                    outputStream?.close()
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save image: ${e.message}")
            false
        }
    }
    
    // ✅ VIDEO SAVE FUNCTION
    fun saveVideo(videoFile: File): Boolean {
        return try {
            val filename = "VIDEO_${System.currentTimeMillis()}.mp4"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/AdvancedCameraPro")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val inputStream = videoFile.inputStream()
                val outputStream = context.contentResolver.openOutputStream(uri)
                
                inputStream.use { input ->
                    outputStream?.use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                
                // Delete temporary file
                videoFile.delete()
                Log.d(TAG, "✅ Video saved to Gallery: $filename")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save video: ${e.message}")
            false
        }
    }
    
    companion object {
        private const val TAG = "FileSaver"
    }
}
