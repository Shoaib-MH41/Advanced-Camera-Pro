package com.yourname.advancedcamera.managers

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.*
import android.media.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.io.File

class CameraManager(private val context: Context, private val textureView: TextureView) {
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var videoFile: File? = null
    
    // Camera2 components
    private lateinit var cameraManager: android.hardware.camera2.CameraManager
    private var backgroundHandler: android.os.Handler? = null
    private var backgroundThread: android.os.HandlerThread? = null
    
    fun startBackgroundThread() {
        backgroundThread = android.os.HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = android.os.Handler(backgroundThread!!.looper)
    }
    
    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Background thread interrupted: ${e.message}")
        }
    }
    
    fun onResume() {
        startBackgroundThread()
        if (textureView.isAvailable) {
            setupCamera(textureView.width, textureView.height)
            openCamera()
        }
    }
    
    fun onPause() {
        closeCamera()
        stopBackgroundThread()
    }
    
    fun captureImage(onImageCaptured: (Bitmap) -> Unit) {
        // Image capture logic
        try {
            // ... existing capture logic
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Capture failed: ${e.message}")
        }
    }
    
    fun switchCamera() {
        // Camera switch logic
    }
    
    fun applyZoom(zoomLevel: Float) {
        // Zoom logic
    }
    
    // ✅ VIDEO RECORDING FUNCTIONS
    fun startVideoRecording(): Boolean {
        return try {
            setupMediaRecorder()
            mediaRecorder?.start()
            isRecording = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording: ${e.message}")
            false
        }
    }
    
    fun stopVideoRecording(): File? {
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isRecording = false
            videoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop video recording: ${e.message}")
            null
        }
    }
    
    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(30)
            setVideoSize(1920, 1080) // 1080p
            
            videoFile = File(context.getExternalFilesDir(null), "VID_${System.currentTimeMillis()}.mp4")
            setOutputFile(videoFile!!.absolutePath)
            
            prepare()
        }
    }
    
    // ... باقی camera operations (setupCamera, openCamera, etc.)
    
    companion object {
        private const val TAG = "CameraManager"
    }
}
