package com.yourname.advancedcamera.managers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections
import android.hardware.camera2.params.MeteringRectangle
import android.media.MediaActionSound
import android.util.Rational
import android.util.Range

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraManager(private val context: Context, private val textureView: TextureView) {
    
    // ==================== 🎥 CAMERA COMPONENTS ====================
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var captureImageReader: ImageReader? = null // ✅ الگ ImageReader تصاویر کے لیے
    private var mediaRecorder: MediaRecorder? = null
    
    // ==================== 🎬 VIDEO RECORDING ====================
    private var isRecording = false
    private var videoFile: File? = null
    private var recorderSurface: Surface? = null
    
    // ==================== 📊 CAMERA STATE ====================
    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    // ✅ CameraManager initialization using lazy
    private val cameraManager: android.hardware.camera2.CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    }
    
    // ✅ FLASH STATE
    private var currentFlashMode: String = "AUTO"
    
    // ✅ Activity reference for orientation
    private var activity: android.app.Activity? = null
    
    // ✅ Callback for errors
    private var errorCallback: ((String) -> Unit)? = null
    
    // ✅ Media action sound
    private val mediaSound = MediaActionSound()
    
    init {
        if (context is android.app.Activity) {
            activity = context
        }
        mediaSound.load(MediaActionSound.SHUTTER_CLICK)
    }
    
    fun setErrorCallback(callback: (String) -> Unit) {
        errorCallback = callback
    }
    
    // ==================== 📱 SURFACE TEXTURE LISTENER ====================
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "✅ SurfaceTexture available: $width x $height")
            startBackgroundThread()
            setupCamera(width, height)
        }
        
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "🔄 Surface size changed: $width x $height")
            configureTransform(width, height)
        }
        
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "🗑️ Surface texture destroyed")
            closeCamera()
            stopBackgroundThread()
            return true
        }
        
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Texture updated
        }
    }
    
    // ==================== 📷 CAMERA STATE CALLBACK ====================
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "✅ Camera opened successfully: ${camera.id}")
            cameraDevice = camera
            createCameraPreviewSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "⚠️ Camera disconnected: ${camera.id}")
            closeCamera()
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "❌ Camera error: $error")
            errorCallback?.invoke("Camera error: $error")
            closeCamera()
        }
    }
    
    // ==================== 🎯 CAPTURE SESSION CALLBACK ====================
    private val captureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "✅ Capture session configured")
            
            // If camera device is null, close session
            if (cameraDevice == null) {
                session.close()
                return
            }
            
            captureSession = session
            try {
                // Configure capture request for preview
                previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    // ✅ Set AF mode for preview
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    
                    // ✅ Apply flash mode
                    applyFlashModeToRequest()
                    
                    // ✅ Auto focus should be enabled
                    set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                }
                
                // Start preview
                val previewRequest = previewRequestBuilder?.build()
                captureSession?.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
                
                Log.d(TAG, "🎬 Preview started successfully")
                
            } catch (e: CameraAccessException) {
                Log.e(TAG, "❌ Failed to start preview: ${e.message}")
                errorCallback?.invoke("Failed to start preview: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "❌ Illegal state: ${e.message}")
                errorCallback?.invoke("Camera in illegal state")
            }
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "❌ Capture session configuration failed")
            errorCallback?.invoke("Camera configuration failed")
        }
        
        override fun onClosed(session: CameraCaptureSession) {
            Log.d(TAG, "Capture session closed")
            if (captureSession === session) {
                captureSession = null
            }
        }
    }
    
    // ==================== 🖼️ IMAGE READER CALLBACK ====================
    private val captureImageListener = ImageReader.OnImageAvailableListener { reader ->
        Log.d(TAG, "📸 Image available from ImageReader")
        
        var image: Image? = null
        var output: FileOutputStream? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "⚠️ Acquired image is null")
                return@OnImageAvailableListener
            }
            
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            // ✅ Play shutter sound
            mediaSound.play(MediaActionSound.SHUTTER_CLICK)
            
            // ✅ Create bitmap from bytes
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            // ✅ Rotate bitmap if needed
            val rotatedBitmap = rotateBitmapIfRequired(bitmap)
            
            // ✅ Save to file
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imageFile = File(storageDir, "IMG_${timeStamp}.jpg")
            
            output = FileOutputStream(imageFile)
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.flush()
            
            Log.d(TAG, "✅ Image saved: ${imageFile.absolutePath}")
            
            // ✅ Callback with bitmap
            onImageCaptured?.invoke(rotatedBitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Image processing failed: ${e.message}", e)
            errorCallback?.invoke("Image processing failed: ${e.message}")
        } finally {
            image?.close()
            output?.close()
            
            // ✅ Restart preview after capture
            restartPreview()
        }
    }
    
    private var onImageCaptured: ((Bitmap) -> Unit)? = null
    
    // ==================== 🎮 PUBLIC METHODS ====================
    
    fun getSurfaceTextureListener(): TextureView.SurfaceTextureListener {
        return surfaceTextureListener
    }
    
    // ==================== 🔦 FLASH CONTROL ====================
    fun applyFlashMode(flashMode: String) {
        Log.d(TAG, "⚡ Setting flash mode: $flashMode")
        currentFlashMode = flashMode
        applyFlashModeToRequest()
    }
    
    private fun applyFlashModeToRequest() {
        try {
            if (previewRequestBuilder == null || captureSession == null) {
                Log.w(TAG, "⚠️ Cannot apply flash mode: preview not ready")
                return
            }
            
            when (currentFlashMode.uppercase()) {
                "AUTO" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_OFF)
                }
                "ON" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_SINGLE)
                }
                "OFF" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON)
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_OFF)
                }
                "TORCH" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            
            val request = previewRequestBuilder?.build()
            captureSession?.setRepeatingRequest(request!!, null, backgroundHandler)
            Log.d(TAG, "✅ Flash mode applied: $currentFlashMode")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Failed to apply flash mode: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ Illegal state while applying flash: ${e.message}")
        }
    }
    
    // ==================== 🎬 VIDEO RECORDING ====================
    @SuppressLint("MissingPermission")
    fun startVideoRecording(): Boolean {
        return try {
            if (isRecording) {
                Log.w(TAG, "⚠️ Already recording")
                return false
            }
            
            if (cameraDevice == null || captureSession == null) {
                Log.e(TAG, "❌ Camera not ready for recording")
                return false
            }
            
            // ✅ Stop preview before recording
            captureSession?.stopRepeating()
            
            // ✅ Setup media recorder
            setupMediaRecorder()
            
            // ✅ Create recording session
            val surfaces = ArrayList<Surface>()
            
            // Add preview surface
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            
            // Add recorder surface
            recorderSurface = mediaRecorder?.surface
            if (recorderSurface != null) {
                surfaces.add(recorderSurface!!)
            }
            
            // ✅ Create recording request
            val recordingRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                if (recorderSurface != null) {
                    addTarget(recorderSurface!!)
                }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            
            // ✅ Start recording session
            cameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        val request = recordingRequestBuilder.build()
                        session.setRepeatingRequest(request, null, backgroundHandler)
                        mediaRecorder?.start()
                        isRecording = true
                        Log.d(TAG, "✅ Video recording started")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to start recording session: ${e.message}")
                        isRecording = false
                    }
                }
                
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "❌ Recording session configuration failed")
                    isRecording = false
                }
            }, backgroundHandler)
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start video recording: ${e.message}", e)
            errorCallback?.invoke("Recording failed: ${e.message}")
            false
        }
    }
    
    fun stopVideoRecording(): File? {
        return try {
            if (!isRecording) {
                Log.w(TAG, "⚠️ Not recording")
                return null
            }
            
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            recorderSurface?.release()
            recorderSurface = null
            mediaRecorder = null
            
            isRecording = false
            Log.d(TAG, "✅ Video recording stopped")
            
            // ✅ Restart preview
            restartPreview()
            
            videoFile
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop video recording: ${e.message}", e)
            errorCallback?.invoke("Stop recording failed: ${e.message}")
            null
        }
    }
    
    private fun setupMediaRecorder() {
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(5_000_000) // Reduced for stability
                setVideoFrameRate(30)
                setVideoSize(1280, 720) // Reduced resolution for stability
                
                // Create video file
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                if (!storageDir?.exists()!!) {
                    storageDir.mkdirs()
                }
                videoFile = File(storageDir, "VID_${timeStamp}.mp4")
                setOutputFile(videoFile!!.absolutePath)
                
                prepare()
            }
            Log.d(TAG, "✅ MediaRecorder setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaRecorder setup failed: ${e.message}", e)
            mediaRecorder = null
        }
    }
    
    // ==================== 📷 CAMERA FUNCTIONS ====================
    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply { 
                start() 
            }
            backgroundHandler = Handler(backgroundThread!!.looper)
            Log.d(TAG, "✅ Background thread started")
        }
    }
    
    private fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
            Log.d(TAG, "✅ Background thread stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, "❌ Background thread interrupted: ${e.message}")
        }
    }
    
    fun onResume() {
        Log.d(TAG, "🔄 onResume called")
        if (textureView.isAvailable) {
            startBackgroundThread()
            setupCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }
    
    fun onPause() {
        Log.d(TAG, "⏸️ onPause called")
        closeCamera()
        stopBackgroundThread()
    }
    
    fun onDestroy() {
        Log.d(TAG, "🗑️ onDestroy called")
        mediaSound.release()
        closeCamera()
        stopBackgroundThread()
    }
    
    @SuppressLint("MissingPermission")
    private fun setupCamera(width: Int, height: Int) {
        try {
            Log.d(TAG, "🔧 Setting up camera for $width x $height")
            
            val cameraList = cameraManager.cameraIdList
            
            if (cameraList.isEmpty()) {
                Log.e(TAG, "❌ No camera found")
                errorCallback?.invoke("No camera found")
                return
            }
            
            // Find back camera first
            cameraId = null
            for (id in cameraList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    cameraCharacteristics = characteristics
                    Log.d(TAG, "📷 Found back camera: $id")
                    break
                }
            }
            
            // If no back camera, use first available
            if (cameraId == null) {
                cameraId = cameraList[0]
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                Log.d(TAG, "📷 Using first camera: $cameraId")
            }
            
            // Get camera characteristics
            val characteristics = cameraCharacteristics ?: return
            
            // Get available sizes
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return
            
            // Choose optimal size
            val textureSize = Size(width, height)
            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                textureSize.width,
                textureSize.height
            )
            
            Log.d(TAG, "📏 Selected preview size: ${previewSize?.width} x ${previewSize?.height}")
            
            // Setup ImageReader for still captures
            val largest = Collections.max(
                Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                CompareSizesByArea()
            )
            
            captureImageReader = ImageReader.newInstance(
                largest.width, 
                largest.height,
                ImageFormat.JPEG, 
                2
            )
            captureImageReader?.setOnImageAvailableListener(captureImageListener, backgroundHandler)
            
            // Open camera if texture is available
            if (textureView.isAvailable) {
                openCamera()
            }
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Camera setup failed: ${e.message}")
            errorCallback?.invoke("Camera setup failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error in setup: ${e.message}", e)
            errorCallback?.invoke("Setup error: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun openCamera() {
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
            Log.e(TAG, "❌ Failed to open camera: ${e.message}")
            errorCallback?.invoke("Camera access failed: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Camera permission denied: ${e.message}")
            errorCallback?.invoke("Camera permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error opening camera: ${e.message}", e)
            errorCallback?.invoke("Camera opening failed")
        }
    }
    
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture ?: return
            
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            val surface = Surface(texture)
            
            // Create preview request
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                applyFlashModeToRequest()
            }
            
            // Create surfaces list
            val surfaces = ArrayList<Surface>().apply {
                add(surface)
                captureImageReader?.surface?.let { add(it) }
            }
            
            // Create capture session
            cameraDevice!!.createCaptureSession(
                surfaces,
                captureSessionCallback,
                backgroundHandler
            )
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Failed to create preview session: ${e.message}")
            errorCallback?.invoke("Preview session failed: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ Illegal state creating session: ${e.message}")
            errorCallback?.invoke("Camera state error")
        }
    }
    
    private fun restartPreview() {
        try {
            if (cameraDevice == null || captureSession == null) {
                Log.w(TAG, "⚠️ Cannot restart preview: camera not ready")
                return
            }
            
            captureSession?.stopRepeating()
            
            // Rebuild preview request
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                val texture = textureView.surfaceTexture ?: return
                texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
                addTarget(Surface(texture))
                
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                applyFlashModeToRequest()
            }
            
            val request = previewRequestBuilder?.build()
            captureSession?.setRepeatingRequest(request!!, null, backgroundHandler)
            
            Log.d(TAG, "✅ Preview restarted")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restart preview: ${e.message}")
        }
    }
    
    fun captureImage(onImageCaptured: (Bitmap) -> Unit) {
        try {
            if (cameraDevice == null || captureSession == null) {
                Log.e(TAG, "❌ Cannot capture: camera not ready")
                errorCallback?.invoke("Camera not ready for capture")
                return
            }
            
            this.onImageCaptured = onImageCaptured
            
            Log.d(TAG, "📸 Capturing image...")
            
            // Stop preview to capture
            captureSession?.stopRepeating()
            
            // Create capture request
            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                captureImageReader?.surface?.let { addTarget(it) }
                
                // Set orientation
                val rotation = getOrientation()
                set(CaptureRequest.JPEG_ORIENTATION, rotation)
                
                // Set flash mode for capture
                when (currentFlashMode.uppercase()) {
                    "AUTO" -> set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    "ON" -> set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                    "OFF" -> set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    "TORCH" -> set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
                
                // Enable stabilization if available
                val characteristics = cameraCharacteristics
                if (characteristics != null) {
                    val stabilization = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    if (stabilization != null && stabilization.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
                        set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)
                    }
                }
            }
            
            // Capture the image
            captureSession?.capture(captureBuilder.build(), null, backgroundHandler)
            
            Log.d(TAG, "✅ Image capture initiated")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Capture failed: ${e.message}")
            errorCallback?.invoke("Capture failed: ${e.message}")
            restartPreview() // Restart preview on error
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected capture error: ${e.message}", e)
            errorCallback?.invoke("Capture error")
            restartPreview() // Restart preview on error
        }
    }
    
    fun switchCamera() {
        try {
            Log.d(TAG, "🔄 Switching camera...")
            
            closeCamera()
            
            val cameraList = cameraManager.cameraIdList
            if (cameraList.size < 2) {
                Log.d(TAG, "Only one camera available")
                errorCallback?.invoke("Only one camera available")
                openCamera() // Reopen same camera
                return
            }
            
            val currentIndex = cameraList.indexOf(cameraId)
            val nextIndex = (currentIndex + 1) % cameraList.size
            cameraId = cameraList[nextIndex]
            
            // Get characteristics
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val facing = cameraCharacteristics?.get(CameraCharacteristics.LENS_FACING)
            val facingText = when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                else -> "External"
            }
            
            Log.d(TAG, "🔄 Switching to $facingText Camera ($cameraId)")
            
            // Re-open camera with new ID
            if (textureView.isAvailable) {
                openCamera()
            }
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Switch camera failed: ${e.message}")
            errorCallback?.invoke("Switch camera failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected switch error: ${e.message}", e)
            errorCallback?.invoke("Camera switch error")
        }
    }
    
    fun applyZoom(zoomLevel: Float) {
        try {
            if (previewRequestBuilder == null || cameraCharacteristics == null) {
                return
            }
            
            // Get max zoom
            val maxZoom = cameraCharacteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            
            // Calculate zoom ratio (clamp between 1.0 and maxZoom)
            val zoomRatio = zoomLevel.coerceIn(1.0f, maxZoom)
            
            // For API level 30+ use CONTROL_ZOOM_RATIO
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
            } else {
                // For older APIs, use SCALER_CROP_REGION if available
                val activeArray = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if (activeArray != null) {
                    val minZoom = 1.0f
                    val clampedZoom = zoomRatio.coerceIn(minZoom, maxZoom)
                    
                    val cropWidth = (activeArray.width() / clampedZoom).toInt()
                    val cropHeight = (activeArray.height() / clampedZoom).toInt()
                    val cropX = (activeArray.width() - cropWidth) / 2
                    val cropY = (activeArray.height() - cropHeight) / 2
                    
                    val zoomRect = Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                    previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
                }
            }
            
            // Apply the changes
            val request = previewRequestBuilder?.build()
            captureSession?.setRepeatingRequest(request!!, null, backgroundHandler)
            
            Log.d(TAG, "🔍 Zoom applied: ${String.format("%.1f", zoomRatio)}x")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Zoom failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Zoom error: ${e.message}")
        }
    }
    
    fun applyManualSettings() {
        try {
            Log.d(TAG, "⚙️ Applying manual settings...")
            
            if (previewRequestBuilder == null) return
            
            // Set manual mode
            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            
            // Reapply flash mode
            applyFlashModeToRequest()
            
            val request = previewRequestBuilder?.build()
            captureSession?.setRepeatingRequest(request!!, null, backgroundHandler)
            
            Log.d(TAG, "✅ Manual settings applied")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Manual settings failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Manual settings error: ${e.message}")
        }
    }
    
    fun setFocusArea(x: Float, y: Float) {
        try {
            if (cameraCharacteristics == null || captureSession == null) {
                return
            }
            
            val maxRegions = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            if (maxRegions <= 0) {
                Log.d(TAG, "⚠️ Auto focus regions not supported")
                return
            }
            
            // Calculate tap area
            val focusRect = calculateTapArea(x, y, 100f)
            val meter = MeteringRectangle(focusRect, 1000)
            
            // Create focus request
            val focusBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                val texture = textureView.surfaceTexture ?: return
                texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
                addTarget(Surface(texture))
                
                // Cancel any previous focus
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meter))
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            }
            
            // Execute focus
            captureSession?.capture(focusBuilder.build(), null, null)
            Log.d(TAG, "🎯 Focus set at: (${x.toInt()}, ${y.toInt()})")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "❌ Focus failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Focus error: ${e.message}")
        }
    }
    
    private fun calculateTapArea(x: Float, y: Float, areaSize: Float): Rect {
        val area = (areaSize).toInt()
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        
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
    
    private fun rotateBitmapIfRequired(bitmap: Bitmap): Bitmap {
        val rotation = getOrientation()
        
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
    
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (textureView.surfaceTexture == null || previewSize == null) return
        
        try {
            val rotation = getOrientation()
            val matrix = Matrix()
            val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            
            if (rotation == 90 || rotation == 270) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                val scale = maxOf(
                    viewHeight.toFloat() / previewSize!!.height,
                    viewWidth.toFloat() / previewSize!!.width
                )
                matrix.postScale(scale, scale, centerX, centerY)
                matrix.postRotate((90 * (rotation / 90 - 2)).toFloat(), centerX, centerY)
            }
            
            textureView.setTransform(matrix)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Transform configuration failed: ${e.message}")
        }
    }
    
    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        // Collect sizes that are at least as big as the requested size
        val bigEnough = ArrayList<Size>()
        
        // Collect sizes that are smaller than the requested size
        val notBigEnough = ArrayList<Size>()
        
        val aspectRatio = width.toFloat() / height.toFloat()
        
        for (option in choices) {
            if (option.width <= 1920 && option.height <= 1080) { // Limit to 1080p for performance
                val optionAspect = option.width.toFloat() / option.height.toFloat()
                
                if (Math.abs(optionAspect - aspectRatio) <= 0.1) { // Allow small aspect ratio differences
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
            else -> choices[0]
        }
    }
    
    private fun closeCamera() {
        try {
            Log.d(TAG, "🔒 Closing camera...")
            
            captureSession?.close()
            captureSession = null
            
            cameraDevice?.close()
            cameraDevice = null
            
            captureImageReader?.close()
            captureImageReader = null
            
            mediaRecorder?.release()
            mediaRecorder = null
            
            recorderSurface?.release()
            recorderSurface = null
            
            Log.d(TAG, "✅ Camera closed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error closing camera: ${e.message}")
        }
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
