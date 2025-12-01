package com.yourname.advancedcamera.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.media.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.io.ByteArrayOutputStream
import java.io.File

class CameraManager(private val context: Context, private val textureView: TextureView) {
    
    // Camera2 components
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    
    // Video recording
    private var isRecording = false
    private var videoFile: File? = null
    
    // Camera state
    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var backgroundHandler: android.os.Handler? = null
    private var backgroundThread: android.os.HandlerThread? = null
    
    private lateinit var cameraManager: android.hardware.camera2.CameraManager
    
    // ✅ FLASH STATE
    private var currentFlashMode: String = "AUTO"
    
    // Surface Texture Listener
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            setupCamera(width, height)
            openCamera()
        }
        
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }
        
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    
    // Camera State Callback
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }
    
    // Capture Session Callback
    private val captureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                
                // ✅ APPLY CURRENT FLASH MODE
                applyFlashModeToRequest()
                
                val previewRequest = previewRequestBuilder?.build()
                captureSession?.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to start preview: ${e.message}")
            }
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "❌ Capture session configuration failed")
        }
    }
    
    // Image Available Listener
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                onImageCaptured?.invoke(bitmap)
            }
        } finally {
            image?.close()
        }
    }
    
    private var onImageCaptured: ((Bitmap) -> Unit)? = null
    
    // ✅ GET SURFACE TEXTURE LISTENER
    fun getSurfaceTextureListener(): TextureView.SurfaceTextureListener {
        return surfaceTextureListener
    }
    
    // ==================== 🔦 FLASH CONTROL FUNCTIONS ====================
    fun applyFlashMode(flashMode: String) {
        currentFlashMode = flashMode
        applyFlashModeToRequest()
    }
    
    private fun applyFlashModeToRequest() {
        try {
            val flashModeConstant = when (currentFlashMode) {
                "AUTO" -> CaptureRequest.FLASH_MODE_AUTO
                "ON" -> CaptureRequest.FLASH_MODE_SINGLE
                "OFF" -> CaptureRequest.FLASH_MODE_OFF
                "TORCH" -> CaptureRequest.FLASH_MODE_TORCH
                else -> CaptureRequest.FLASH_MODE_AUTO
            }
            
            previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, flashModeConstant)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to apply flash mode: ${e.message}")
        }
    }
    
    // ==================== 🎬 VIDEO RECORDING FUNCTIONS ====================
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
    
    // ==================== 📷 EXISTING CAMERA FUNCTIONS ====================
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
    }
    
    fun onPause() {
        closeCamera()
        stopBackgroundThread()
    }
    
    @android.annotation.SuppressLint("MissingPermission")
    fun setupCamera(width: Int, height: Int) {
        try {
            cameraManager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
            val cameraList = cameraManager.cameraIdList
            
            if (cameraList.isEmpty()) {
                Log.e(TAG, "❌ No camera found")
                return
            }
            
            // Find back camera first
            for (id in cameraList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
            
            cameraId = cameraId ?: cameraList[0]
            
            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return
            
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height)
            
            configureTransform(width, height)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera setup failed: ${e.message}")
        }
    }
    
    @android.annotation.SuppressLint("MissingPermission")
    fun openCamera() {
        try {
            cameraManager.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera: ${e.message}")
        }
    }
    
    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        
        texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
        val surface = Surface(texture)
        
        try {
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)
            
            setupImageReader()
            
            cameraDevice!!.createCaptureSession(listOf(surface, imageReader!!.surface),
                captureSessionCallback, backgroundHandler)
                
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start preview: ${e.message}")
        }
    }
    
    private fun setupImageReader() {
        try {
            imageReader = ImageReader.newInstance(
                previewSize!!.width,
                previewSize!!.height,
                ImageFormat.JPEG, 1
            )
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Image reader setup failed: ${e.message}")
        }
    }
    
    fun captureImage(onImageCaptured: (Bitmap) -> Unit) {
        if (cameraDevice == null) return
        
        try {
            this.onImageCaptured = onImageCaptured
            
            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            
            // ✅ APPLY FLASH MODE FOR CAPTURE
            val flashModeConstant = when (currentFlashMode) {
                "AUTO" -> CaptureRequest.FLASH_MODE_AUTO
                "ON" -> CaptureRequest.FLASH_MODE_SINGLE
                "OFF" -> CaptureRequest.FLASH_MODE_OFF
                "TORCH" -> CaptureRequest.FLASH_MODE_TORCH
                else -> CaptureRequest.FLASH_MODE_AUTO
            }
            captureBuilder.set(CaptureRequest.FLASH_MODE, flashModeConstant)
            
            captureSession!!.stopRepeating()
            captureSession!!.capture(captureBuilder.build(), null, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Capture failed: ${e.message}")
        }
    }
    
    fun switchCamera() {
        // Implementation for switching cameras
        Log.d(TAG, "🔄 Switching camera")
    }
    
    fun applyZoom(zoomLevel: Float) {
        try {
            previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, null)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Zoom failed: ${e.message}")
        }
    }
    
    fun applyManualSettings() {
        // Implementation for manual settings
    }
    
    fun setFocusArea(x: Float, y: Float) {
        // Implementation for focus area
        Log.d(TAG, "🎯 Focus set at: $x, $y")
    }
    
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        // Implementation for transform configuration
    }
    
    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        val bigEnough = ArrayList<Size>()
        
        for (option in choices) {
            if (option.height == option.width * height / width &&
                option.width <= width && option.height <= height) {
                bigEnough.add(option)
            }
        }
        
        return if (bigEnough.isNotEmpty()) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else {
            choices[0]
        }
    }
    
    private fun closeCamera() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
    }
    
    fun getOrientation(): Int {
        // Implementation for orientation
        return 0
    }
    
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
    
    companion object {
        private const val TAG = "CameraManager"
    }
}
