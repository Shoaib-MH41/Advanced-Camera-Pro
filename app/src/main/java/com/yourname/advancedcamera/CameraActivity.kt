package com.yourname.advancedcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections

class SimpleCameraActivity : AppCompatActivity() {
    
    private lateinit var textureView: TextureView
    private lateinit var captureBtn: android.widget.Button
    private lateinit var flashBtn: android.widget.Button
    private lateinit var switchBtn: android.widget.Button
    private lateinit var recordBtn: android.widget.Button
    
    // Camera2 components
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    
    // State
    private var isRecording = false
    private var currentFlashMode = "AUTO"
    private var isFrontCamera = false
    private var cameraId: String? = null
    private var previewSize: Size? = null
    
    // Handlers
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    // Manager
    private lateinit var cameraManager: android.hardware.camera2.CameraManager
    
    companion object {
        private const val TAG = "SimpleCamera"
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_camera)
        
        // Hide action bar for full screen
        supportActionBar?.hide()
        
        // Get camera manager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        
        // Initialize views
        initializeViews()
        
        // Setup click listeners
        setupClickListeners()
        
        // Check permissions
        if (checkPermissions()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }
    
    private fun initializeViews() {
        textureView = findViewById(R.id.textureView)
        captureBtn = findViewById(R.id.btnCapture)
        flashBtn = findViewById(R.id.btnFlash)
        switchBtn = findViewById(R.id.btnSwitch)
        recordBtn = findViewById(R.id.btnRecord)
    }
    
    private fun setupClickListeners() {
        captureBtn.setOnClickListener { capturePhoto() }
        flashBtn.setOnClickListener { toggleFlash() }
        switchBtn.setOnClickListener { switchCamera() }
        recordBtn.setOnClickListener { toggleRecording() }
    }
    
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            setupCamera(textureView.width, textureView.height)
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }
    
    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }
    
    // ==================== 📷 CAMERA LOGIC ====================
    
    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            setupCamera(width, height)
            openCamera()
        }
        
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }
        
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }
        
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    
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
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@SimpleCameraActivity,
                    "Camera error: $error",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private val captureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                val previewRequest = previewRequestBuilder?.build()
                captureSession?.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to start preview")
            }
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@SimpleCameraActivity,
                    "Failed to configure camera",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun setupCamera(width: Int, height: Int) {
        try {
            val cameraList = cameraManager.cameraIdList
            if (cameraList.isEmpty()) return
            
            // Find back camera
            cameraId = null
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
            
            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java), 
                width, 
                height
            )
            
            configureTransform(width, height)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera setup failed")
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            if (cameraId == null) return
            cameraManager.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera")
        }
    }
    
    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        
        texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
        val surface = Surface(texture)
        
        try {
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder!!.addTarget(surface)
            
            // Setup ImageReader for photos
            imageReader = ImageReader.newInstance(
                previewSize!!.width,
                previewSize!!.height,
                ImageFormat.JPEG, 
                1
            )
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
            
            cameraDevice!!.createCaptureSession(
                listOf(surface, imageReader!!.surface),
                captureSessionCallback, 
                backgroundHandler
            )
                
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start preview")
        }
    }
    
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                saveImage(bitmap)
                
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@SimpleCameraActivity,
                        "Photo saved!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } finally {
            image?.close()
        }
    }
    
    // ==================== 📸 CAPTURE PHOTO ====================
    
    private fun capturePhoto() {
        if (cameraDevice == null) return
        
        try {
            val captureBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )
            captureBuilder.addTarget(imageReader!!.surface)
            
            // Apply flash
            applyFlashToCapture(captureBuilder)
            
            captureSession?.stopRepeating()
            captureSession?.capture(captureBuilder.build(), null, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Capture failed")
        }
    }
    
    private fun applyFlashToCapture(builder: CaptureRequest.Builder) {
        when (currentFlashMode) {
            "AUTO" -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, 
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }
            "ON" -> {
                builder.set(CaptureRequest.FLASH_MODE, 
                    CaptureRequest.FLASH_MODE_SINGLE)
            }
            "OFF" -> {
                builder.set(CaptureRequest.FLASH_MODE, 
                    CaptureRequest.FLASH_MODE_OFF)
            }
        }
    }
    
    private fun saveImage(bitmap: Bitmap) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "IMG_$timeStamp.jpg"
            
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs()
            }
            
            val imageFile = File(storageDir, fileName)
            
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Notify gallery
            android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also {
                it.data = android.net.Uri.fromFile(imageFile)
                sendBroadcast(it)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image")
        }
    }
    
    // ==================== 🎬 VIDEO RECORDING ====================
    
    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
            recordBtn.text = "⏺️ Record"
        } else {
            if (startRecording()) {
                recordBtn.text = "⏹️ Stop"
            }
        }
    }
    
    private fun startRecording(): Boolean {
        return try {
            setupMediaRecorder()
            mediaRecorder?.start()
            isRecording = true
            android.widget.Toast.makeText(this, "Recording started", android.widget.Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording")
            false
        }
    }
    
    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isRecording = false
            android.widget.Toast.makeText(this, "Video saved", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording")
        }
    }
    
    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(5_000_000)
            setVideoFrameRate(30)
            setVideoSize(1920, 1080)
            
            val videoFile = File(
                getExternalFilesDir(null), 
                "VID_${System.currentTimeMillis()}.mp4"
            )
            setOutputFile(videoFile.absolutePath)
            
            prepare()
        }
    }
    
    // ==================== 🔦 FLASH CONTROL ====================
    
    private fun toggleFlash() {
        currentFlashMode = when (currentFlashMode) {
            "AUTO" -> "ON"
            "ON" -> "OFF"
            "OFF" -> "TORCH"
            else -> "AUTO"
        }
        
        flashBtn.text = when (currentFlashMode) {
            "AUTO" -> "⚡ Auto"
            "ON" -> "🔆 On"
            "OFF" -> "⚫ Off"
            else -> "🔦 Torch"
        }
        
        applyFlashToPreview()
    }
    
    private fun applyFlashToPreview() {
        try {
            when (currentFlashMode) {
                "AUTO" -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, 
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                "ON" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_SINGLE)
                }
                "OFF" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_OFF)
                }
                "TORCH" -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, 
                        CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to apply flash")
        }
    }
    
    // ==================== 🔄 SWITCH CAMERA ====================
    
    private fun switchCamera() {
        closeCamera()
        
        val cameraList = cameraManager.cameraIdList
        if (cameraList.size < 2) {
            android.widget.Toast.makeText(this, "Only one camera", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        isFrontCamera = !isFrontCamera
        cameraId = findCameraId(
            if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT 
            else CameraCharacteristics.LENS_FACING_BACK
        )
        
        switchBtn.text = if (isFrontCamera) "📱 Front" else "📷 Back"
        openCamera()
    }
    
    private fun findCameraId(facing: Int): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == facing
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== 🔧 UTILITIES ====================
    
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (textureView.surfaceTexture == null || previewSize == null) return
        
        val rotation = windowManager.defaultDisplay?.rotation ?: 0
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
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
            choices.firstOrNull() ?: Size(1920, 1080)
        }
    }
    
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Background thread interrupted")
        }
    }
    
    private fun closeCamera() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera")
        }
    }
    
    // ==================== 🔐 PERMISSIONS ====================
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "Permissions required!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private fun startCamera() {
        if (textureView.isAvailable) {
            setupCamera(textureView.width, textureView.height)
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
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
