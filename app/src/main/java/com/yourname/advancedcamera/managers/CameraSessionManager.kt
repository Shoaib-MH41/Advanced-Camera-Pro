package com.yourname.advancedcamera.managers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraSessionManager(private val context: Context, private val textureView: TextureView) {
    
    companion object {
        private const val TAG = "SessionManager"
    }
    
    // Camera components
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var captureImageReader: ImageReader? = null
    
    // Video recording
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var videoFile: File? = null
    private var recorderSurface: Surface? = null
    
    // Camera state
    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    // Callbacks
    private var errorCallback: ((String) -> Unit)? = null
    private var onImageCaptured: ((Bitmap) -> Unit)? = null
    
    // Camera manager
    private val cameraManager: android.hardware.camera2.CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    }
    
    // Activity reference
    private var activity: android.app.Activity? = null
    
    init {
        if (context is android.app.Activity) {
            activity = context
        }
    }
    
    // ==================== 🎬 SESSION MANAGEMENT ====================
    
    fun setErrorCallback(callback: (String) -> Unit) {
        errorCallback = callback
    }
    
    fun startBackgroundThread() {
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
    
    @SuppressLint("MissingPermission")
    fun setupCamera(width: Int, height: Int) {
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
            
            // Choose optimal size
            val map = cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return
            
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
            
            val cameraId = this.cameraId ?: run {
                Log.e(TAG, "❌ Camera ID is null")
                return
            }
            
            if (backgroundHandler == null) {
                Log.w(TAG, "⚠️ Background handler is null, starting thread...")
                startBackgroundThread()
            }
            
            cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
            
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
            
            if (cameraDevice == null) {
                session.close()
                return
            }
            
            captureSession = session
            
            try {
                previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                
                previewRequestBuilder?.build()?.let { request ->
                    captureSession?.setRepeatingRequest(request, null, backgroundHandler)
                }
                
                Log.d(TAG, "🎬 Preview started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start preview: ${e.message}")
                errorCallback?.invoke("Failed to start preview: ${e.message}")
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
    
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture ?: return
            val previewSize = this.previewSize ?: return
            
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)
            
            previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            
            val surfaces = ArrayList<Surface>().apply {
                add(surface)
                captureImageReader?.surface?.let { add(it) }
            }
            
            cameraDevice?.createCaptureSession(
                surfaces,
                captureSessionCallback,
                backgroundHandler
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create preview session: ${e.message}")
            errorCallback?.invoke("Preview session failed: ${e.message}")
        }
    }
    
    fun restartPreview() {
        try {
            if (cameraDevice == null || captureSession == null) {
                Log.w(TAG, "⚠️ Cannot restart preview: camera not ready")
                return
            }
            
            captureSession?.stopRepeating()
            
            previewRequestBuilder?.build()?.let { request ->
                captureSession?.setRepeatingRequest(request, null, backgroundHandler)
            }
            
            Log.d(TAG, "✅ Preview restarted")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restart preview: ${e.message}")
        }
    }
    
    // ==================== 🔄 CAMERA SWITCHING ====================
    
    @SuppressLint("MissingPermission")
    fun switchCamera() {
        try {
            Log.d(TAG, "🔄 Switching camera...")
            
            closeCamera()
            
            val cameraList = cameraManager.cameraIdList
            if (cameraList.size < 2) {
                Log.d(TAG, "Only one camera available")
                errorCallback?.invoke("Only one camera available")
                openCamera()
                return
            }
            
            val currentIndex = cameraList.indexOf(cameraId)
            val nextIndex = (currentIndex + 1) % cameraList.size
            cameraId = cameraList[nextIndex]
            
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            
            if (textureView.isAvailable) {
                openCamera()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Switch camera failed: ${e.message}")
            errorCallback?.invoke("Switch camera failed: ${e.message}")
        }
    }
    
    // ==================== ⚙️ SETTINGS CONTROL ====================
    
    fun applyFlashMode(flashMode: String) {
        try {
            if (previewRequestBuilder == null || captureSession == null) {
                return
            }
            
            when (flashMode.uppercase()) {
                "AUTO" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                "ON" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                }
                "OFF" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON)
                }
                "TORCH" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            
            previewRequestBuilder?.build()?.let { request ->
                captureSession?.setRepeatingRequest(request, null, backgroundHandler)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to apply flash mode: ${e.message}")
        }
    }
    
    fun applyZoom(zoomLevel: Float) {
        try {
            if (previewRequestBuilder == null || cameraCharacteristics == null) {
                return
            }
            
            val maxZoom = cameraCharacteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            val zoomRatio = zoomLevel.coerceIn(1.0f, maxZoom)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
            } else {
                val activeArray = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if (activeArray != null) {
                    val cropWidth = (activeArray.width() / zoomRatio).toInt()
                    val cropHeight = (activeArray.height() / zoomRatio).toInt()
                    val cropX = (activeArray.width() - cropWidth) / 2
                    val cropY = (activeArray.height() - cropHeight) / 2
                    
                    val zoomRect = Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                    previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
                }
            }
            
            previewRequestBuilder?.build()?.let { request ->
                captureSession?.setRepeatingRequest(request, null, backgroundHandler)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Zoom failed: ${e.message}")
        }
    }
    
    fun applyManualSettings() {
        try {
            if (previewRequestBuilder == null) return
            
            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            
            previewRequestBuilder?.build()?.let { request ->
                captureSession?.setRepeatingRequest(request, null, backgroundHandler)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Manual settings failed: ${e.message}")
        }
    }
    
    fun setFocusArea(x: Float, y: Float) {
        try {
            if (cameraCharacteristics == null || captureSession == null) {
                return
            }
            
            val focusRect = calculateTapArea(x, y, 100f)
            val meter = MeteringRectangle(focusRect, 1000)
            
            val focusBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                val texture = textureView.surfaceTexture ?: return
                val previewSize = this@CameraSessionManager.previewSize ?: return
                
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                addTarget(Surface(texture))
                
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meter))
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            } ?: return
            
            captureSession?.capture(focusBuilder.build(), null, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Focus failed: ${e.message}")
        }
    }
    
    // ==================== 🎥 VIDEO RECORDING ====================
    
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
            
            captureSession?.stopRepeating()
            
            if (!setupMediaRecorder()) {
                return false
            }
            
            val surfaces = ArrayList<Surface>()
            
            val texture = textureView.surfaceTexture ?: return false
            previewSize?.let {
                texture.setDefaultBufferSize(it.width, it.height)
            }
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            
            recorderSurface = mediaRecorder?.surface
            recorderSurface?.let {
                surfaces.add(it)
            }
            
            val recordingRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
                addTarget(previewSurface)
                recorderSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } ?: return false
            
            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
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
            
            restartPreview()
            
            videoFile
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop video recording: ${e.message}", e)
            errorCallback?.invoke("Stop recording failed: ${e.message}")
            null
        }
    }
    
    private fun setupMediaRecorder(): Boolean {
        return try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(5_000_000)
                setVideoFrameRate(30)
                setVideoSize(1280, 720)
                
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                storageDir?.mkdirs()
                videoFile = File(storageDir, "VID_${timeStamp}.mp4")
                setOutputFile(videoFile?.absolutePath ?: return false)
                
                prepare()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaRecorder setup failed: ${e.message}", e)
            mediaRecorder = null
            false
        }
    }
    
    // ==================== 📸 IMAGE CAPTURE ====================
    
    fun captureImage(onImageCaptured: (Bitmap) -> Unit) {
        this.onImageCaptured = onImageCaptured
        captureImageReader?.setOnImageAvailableListener(
            { reader -> handleImageCapture(reader) },
            backgroundHandler
        )
    }
    
    private fun handleImageCapture(reader: ImageReader) {
        var image: Image? = null
        
        try {
            image = reader.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "⚠️ Acquired image is null")
                return
            }
            
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val rotatedBitmap = rotateBitmapIfRequired(bitmap)
            
            onImageCaptured?.invoke(rotatedBitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Image processing failed: ${e.message}", e)
            errorCallback?.invoke("Image processing failed: ${e.message}")
        } finally {
            image?.close()
            restartPreview()
        }
    }
    
    // ==================== 🔧 UTILITY FUNCTIONS ====================
    
    private fun calculateTapArea(x: Float, y: Float, areaSize: Float): Rect {
        val area = areaSize.toInt()
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        
        if (viewWidth == 0 || viewHeight == 0) {
            return Rect(-1000, -1000, 1000, 1000)
        }
        
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
    
    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        
        val aspectRatio = width.toFloat() / height.toFloat()
        
        for (option in choices) {
            if (option.width <= 1920 && option.height <= 1080) {
                val optionAspect = option.width.toFloat() / option.height.toFloat()
                
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
            
            videoFile = null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error closing camera: ${e.message}")
        }
    }
    
    fun onPause() {
        closeCamera()
        stopBackgroundThread()
    }
    
    fun onDestroy() {
        closeCamera()
        stopBackgroundThread()
    }
    
    // ==================== 📊 GETTERS ====================
    
    fun getSurfaceTextureListener() = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "✅ SurfaceTexture available: $width x $height")
            startBackgroundThread()
            setupCamera(width, height)
        }
        
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "🔄 Surface size changed: $width x $height")
        }
        
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "🗑️ Surface texture destroyed")
            closeCamera()
            stopBackgroundThread()
            return true
        }
        
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun isPreviewActive(): Boolean = captureSession != null
    
    fun getPreviewSize(): Size? = previewSize
    
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
}
