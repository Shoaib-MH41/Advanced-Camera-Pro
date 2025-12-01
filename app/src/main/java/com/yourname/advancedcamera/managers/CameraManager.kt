package com.yourname.advancedcamera.managers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.util.*
import java.util.Collections

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
    
    // ✅ CameraManager initialization using lazy
    private val cameraManager: android.hardware.camera2.CameraManager by lazy {
        context.getSystemService(android.hardware.camera2.CameraManager::class.java)!!
    }
    
    // ✅ FLASH STATE
    private var currentFlashMode: String = "AUTO"
    
    // ✅ Activity reference for orientation
    private var activity: android.app.Activity? = null
    
    init {
        if (context is android.app.Activity) {
            activity = context
        }
    }
    
    // Surface Texture Listener
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "✅ SurfaceTexture available: $width x $height")
            setupCamera(width, height)
            openCamera()
        }
        
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }
        
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }
        
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    
    // Camera State Callback
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "✅ Camera opened successfully")
            cameraDevice = camera
            startPreview()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "⚠️ Camera disconnected")
            cameraDevice?.close()
            cameraDevice = null
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "❌ Camera error: $error")
            cameraDevice?.close()
            cameraDevice = null
        }
    }
    
    // Capture Session Callback
    private val captureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "✅ Capture session configured")
            captureSession = session
            try {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, 
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                    CaptureRequest.CONTROL_AE_MODE_ON)
                
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
            when (currentFlashMode) {
                "AUTO" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                "ON" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_SINGLE)
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON)
                }
                "OFF" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_OFF)
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON)
                }
                "TORCH" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), 
                null, backgroundHandler)
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
            Log.d(TAG, "✅ Video recording started")
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
            Log.d(TAG, "✅ Video recording stopped")
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
            
            videoFile = File(context.getExternalFilesDir(null), 
                "VID_${System.currentTimeMillis()}.mp4")
            setOutputFile(videoFile!!.absolutePath)
            
            prepare()
        }
    }
    
    // ==================== 📷 CAMERA FUNCTIONS ====================
    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = android.os.HandlerThread("CameraBackground").apply { 
                start() 
            }
            backgroundHandler = android.os.Handler(backgroundThread!!.looper)
            Log.d(TAG, "✅ Background thread started")
        }
    }
    
    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
            Log.d(TAG, "✅ Background thread stopped")
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
    
    @SuppressLint("MissingPermission")
    fun setupCamera(width: Int, height: Int) {
        try {
            Log.d(TAG, "🔧 Setting up camera for $width x $height")
            
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
            
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), 
                width, height)
            
            Log.d(TAG, "📏 Selected preview size: ${previewSize?.width} x ${previewSize?.height}")
            
            configureTransform(width, height)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera setup failed: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun openCamera() {
        try {
            Log.d(TAG, "🎬 Opening camera: $cameraId")
            
            if (cameraId == null) {
                Log.e(TAG, "❌ Camera ID is null")
                return
            }
            
            if (backgroundHandler == null) {
                Log.w(TAG, "⚠️ Background handler is null, starting thread...")
                startBackgroundThread()
            }
            
            cameraManager.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied: ${e.message}")
        }
    }
    
    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        
        texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
        val surface = Surface(texture)
        
        try {
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)
            
            setupImageReader()
            
            cameraDevice!!.createCaptureSession(
                listOf(surface, imageReader!!.surface),
                captureSessionCallback, 
                backgroundHandler
            )
                
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start preview: ${e.message}")
        }
    }
    
    private fun setupImageReader() {
        try {
            imageReader = ImageReader.newInstance(
                previewSize!!.width,
                previewSize!!.height,
                ImageFormat.JPEG, 
                1
            )
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, 
                backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Image reader setup failed: ${e.message}")
        }
    }
    
    fun captureImage(onImageCaptured: (Bitmap) -> Unit) {
        if (cameraDevice == null) return
        
        try {
            this.onImageCaptured = onImageCaptured
            
            val captureBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            
            when (currentFlashMode) {
                "AUTO" -> {
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                "ON" -> {
                    captureBuilder.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_SINGLE)
                }
                "OFF" -> {
                    captureBuilder.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_OFF)
                }
                "TORCH" -> {
                    captureBuilder.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            
            captureSession!!.stopRepeating()
            captureSession!!.capture(captureBuilder.build(), null, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Capture failed: ${e.message}")
        }
    }
    
    fun switchCamera() {
        try {
            closeCamera()
            
            val cameraList = cameraManager.cameraIdList
            if (cameraList.size < 2) {
                Log.d(TAG, "Only one camera available")
                return
            }
            
            val currentIndex = cameraList.indexOf(cameraId)
            val nextIndex = (currentIndex + 1) % cameraList.size
            cameraId = cameraList[nextIndex]
            
            // Get characteristics to check facing direction
            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val facingText = when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                else -> "External"
            }
            
            Log.d(TAG, "🔄 Switching to $facingText Camera")
            
            // Re-open camera with new ID
            openCamera()
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Switch camera failed: ${e.message}")
        }
    }
    
    fun applyZoom(zoomLevel: Float) {
        try {
            previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, null)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), 
                null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Zoom failed: ${e.message}")
        }
    }
    
    fun applyManualSettings() {
        try {
            // Apply manual settings if needed
            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, 
                CaptureRequest.CONTROL_MODE_OFF)
            
            // You can add more manual controls here
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), 
                null, backgroundHandler)
            Log.d(TAG, "⚙️ Manual settings applied")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Manual settings failed: ${e.message}")
        }
    }
    
    fun setFocusArea(x: Float, y: Float) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val maxRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            
            if (maxRegions <= 0) return
            
            val focusRect = calculateTapArea(x, y, 100f)
            val focusRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW)
            focusRequestBuilder.addTarget(Surface(textureView.surfaceTexture))
            
            val meter = MeteringRectangle(focusRect, 1000)
            focusRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, 
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            focusRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meter))
            focusRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, 
                CameraMetadata.CONTROL_AF_TRIGGER_START)
            focusRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 
                CaptureRequest.CONTROL_AF_MODE_AUTO)
            
            captureSession?.capture(focusRequestBuilder.build(), null, null)
            Log.d(TAG, "🎯 Focus set at: $x, $y")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Focus failed: ${e.message}")
        }
    }
    
    private fun calculateTapArea(x: Float, y: Float, areaSize: Float): Rect {
        val area = (areaSize * 10).toInt()
        val left = ((x * 2000 / textureView.width - 1000 - area / 2).toInt())
            .coerceIn(-1000, 1000)
        val top = ((y * 2000 / textureView.height - 1000 - area / 2).toInt())
            .coerceIn(-1000, 1000)
        return Rect(left, top, left + area, top + area)
    }
    
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (textureView.surfaceTexture == null || previewSize == null) return
        
        val rotation = activity?.windowManager?.defaultDisplay?.rotation ?: 0
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), 
            previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), 
                centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / previewSize!!.height,
                viewWidth.toFloat() / previewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        
        textureView.setTransform(matrix)
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
        Log.d(TAG, "✅ Camera closed")
    }
    
    fun getOrientation(): Int {
        return when (activity?.windowManager?.defaultDisplay?.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
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
