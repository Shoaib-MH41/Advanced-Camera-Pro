package com.yourname.advancedcamera.managers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.MediaActionSound
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.TextureView
import androidx.annotation.RequiresApi
import java.io.File

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraController(private val context: Context, private val textureView: TextureView) {
    
    companion object {
        private const val TAG = "CameraController"
    }
    
    // Managers
    private lateinit var sessionManager: CameraSessionManager
    private lateinit var captureManager: CameraCaptureManager
    private var activity: android.app.Activity? = null
    
    // UI State
    private var currentFlashMode: String = "AUTO"
    private var errorCallback: ((String) -> Unit)? = null
    
    // Media sound
    private val mediaSound = MediaActionSound()
    
    init {
        if (context is android.app.Activity) {
            activity = context
        }
        mediaSound.load(MediaActionSound.SHUTTER_CLICK)
        
        // Initialize managers
        sessionManager = CameraSessionManager(context, textureView)
        captureManager = CameraCaptureManager(context)
        
        // Set callbacks
        sessionManager.setErrorCallback { error ->
            errorCallback?.invoke(error)
        }
    }
    
    fun setErrorCallback(callback: (String) -> Unit) {
        errorCallback = callback
        sessionManager.setErrorCallback(callback)
    }
    
    // ==================== 🎬 PUBLIC API ====================
    
    fun getSurfaceTextureListener() = sessionManager.getSurfaceTextureListener()
    
    fun onResume() {
        Log.d(TAG, "🔄 Controller onResume")
        sessionManager.startBackgroundThread()
    }
    
    fun onPause() {
        Log.d(TAG, "⏸️ Controller onPause")
        sessionManager.onPause()
    }
    
    fun onDestroy() {
        Log.d(TAG, "🗑️ Controller onDestroy")
        mediaSound.release()
        sessionManager.onDestroy()
    }
    
    // ==================== 📸 IMAGE CAPTURE ====================
    
    fun captureImage(onImageCaptured: (Bitmap) -> Unit) {
        Log.d(TAG, "📸 Controller: Capturing image")
        sessionManager.captureImage { bitmap ->
            mediaSound.play(MediaActionSound.SHUTTER_CLICK)
            onImageCaptured(bitmap)
        }
    }
    
    // ==================== 🎥 VIDEO RECORDING ====================
    
    fun startVideoRecording(): Boolean {
        Log.d(TAG, "🎬 Controller: Starting video recording")
        return sessionManager.startVideoRecording()
    }
    
    fun stopVideoRecording(): File? {
        Log.d(TAG, "⏹️ Controller: Stopping video recording")
        return sessionManager.stopVideoRecording()
    }
    
    // ==================== ⚙️ SETTINGS CONTROL ====================
    
    fun applyFlashMode(flashMode: String) {
        Log.d(TAG, "⚡ Controller: Setting flash to $flashMode")
        currentFlashMode = flashMode.uppercase()
        sessionManager.applyFlashMode(currentFlashMode)
    }
    
    fun getCurrentFlashMode(): String = currentFlashMode
    
    fun switchCamera() {
        Log.d(TAG, "🔄 Controller: Switching camera")
        sessionManager.switchCamera()
    }
    
    fun applyZoom(zoomLevel: Float) {
        Log.d(TAG, "🔍 Controller: Applying zoom $zoomLevel")
        sessionManager.applyZoom(zoomLevel)
    }
    
    fun setFocusArea(x: Float, y: Float) {
        Log.d(TAG, "🎯 Controller: Setting focus at ($x, $y)")
        sessionManager.setFocusArea(x, y)
    }
    
    fun applyManualSettings() {
        Log.d(TAG, "⚙️ Controller: Applying manual settings")
        sessionManager.applyManualSettings()
    }
    
    // ==================== 📊 INFO GETTERS ====================
    
    fun isRecording(): Boolean = sessionManager.isRecording()
    
    fun isPreviewActive(): Boolean = sessionManager.isPreviewActive()
    
    fun getPreviewSize(): Size? = sessionManager.getPreviewSize()
    
    fun getOrientation(): Int = sessionManager.getOrientation()
}
