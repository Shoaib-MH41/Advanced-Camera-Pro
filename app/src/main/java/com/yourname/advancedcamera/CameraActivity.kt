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
import android.view.View
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections

class CameraActivity : AppCompatActivity() {
    
    // UI Elements
    private lateinit var textureView: TextureView
    private lateinit var previewView: android.view.View
    private lateinit var btnCapture: MaterialButton
    private lateinit var btnVideoRecord: MaterialButton
    private lateinit var btnSwitchCamera: MaterialButton
    private lateinit var btnFlash: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var tabModes: TabLayout
    private lateinit var proControlsPanel: CardView
    private lateinit var scrollProControls: NestedScrollView
    private lateinit var btnExpandControls: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var recordingIndicator: LottieAnimationView
    private lateinit var focusIndicator: LottieAnimationView
    private lateinit var seekZoom: SeekBar
    private lateinit var tvZoomValue: android.widget.TextView
    private lateinit var spinnerLUT: Spinner
    
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
    private var videoFile: File? = null
    private var isProControlsExpanded = false
    
    // Handlers
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    // Manager
    private lateinit var cameraManager: android.hardware.camera2.CameraManager
    
    // Recording timer
    private var recordingStartTime: Long = 0
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private lateinit var tvRecordingTimer: android.widget.TextView
    
    companion object {
        private const val TAG = "CameraActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        
        // Hide action bar for full screen
        supportActionBar?.hide()
        
        // Initialize camera manager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        
        // Initialize all UI elements
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
        // Main preview
        textureView = findViewById(R.id.texture_view)
        previewView = findViewById(R.id.previewView)
        
        // Buttons
        btnCapture = findViewById(R.id.btn_capture)
        btnVideoRecord = findViewById(R.id.btn_video_record)
        btnSwitchCamera = findViewById(R.id.btn_switch_camera)
        btnFlash = findViewById(R.id.btn_flash)
        btnGallery = findViewById(R.id.btn_gallery)
        btnSettings = findViewById(R.id.btn_settings)
        
        // Mode tabs
        tabModes = findViewById(R.id.tab_modes)
        
        // Pro controls
        proControlsPanel = findViewById(R.id.pro_controls_panel)
        scrollProControls = findViewById(R.id.scrollProControls)
        btnExpandControls = findViewById(R.id.fab_expand_controls)
        
        // Indicators
        recordingIndicator = findViewById(R.id.recording_indicator)
        focusIndicator = findViewById(R.id.focus_indicator)
        
        // Pro controls elements
        seekZoom = findViewById(R.id.seek_zoom)
        tvZoomValue = findViewById(R.id.tv_zoom_value)
        spinnerLUT = findViewById(R.id.spinner_lut)
        
        // Recording timer
        tvRecordingTimer = findViewById(R.id.tv_recording_timer)
    }
    
    private fun setupClickListeners() {
        // Capture photo
        btnCapture.setOnClickListener {
            capturePhoto()
        }
        
        // Record video
        btnVideoRecord.setOnClickListener {
            toggleVideoRecording()
        }
        
        // Switch camera
        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }
        
        // Flash control
        btnFlash.setOnClickListener {
            toggleFlashMode()
        }
        
        // Gallery
        btnGallery.setOnClickListener {
            openGallery()
        }
        
        // Settings
        btnSettings.setOnClickListener {
            openSettings()
        }
        
        // Expand pro controls
        btnExpandControls.setOnClickListener {
            toggleProControls()
        }
        
        // Mode selection
        tabModes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    when (it.position) {
                        0 -> setMode("AUTO")
                        1 -> setMode("PRO")
                        2 -> setMode("NIGHT")
                        3 -> setMode("PORTRAIT")
                        4 -> setMode("VIDEO")
                    }
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Zoom control
        seekZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val zoomValue = 1.0f + (progress / 100f) * 9f // 1.0x to 10.0x
                tvZoomValue.text = String.format("%.1fx", zoomValue)
                applyZoom(zoomValue)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Touch focus
        textureView.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                setFocusArea(x, y)
                showFocusIndicator(x, y)
            }
            true
        }
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
        super.onPause()
        closeCamera()
        stopBackgroundThread()
        recordingTimerHandler.removeCallbacksAndMessages(null)
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
                Toast.makeText(this@CameraActivity, "Camera error: $error", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this@CameraActivity, "Failed to configure camera", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun setupCamera(width: Int, height: Int) {
        try {
            val cameraList = cameraManager.cameraIdList
            if (cameraList.isEmpty()) {
                Toast.makeText(this, "No camera found", Toast.LENGTH_LONG).show()
                return
            }
            
            // Find appropriate camera
            cameraId = if (isFrontCamera) {
                findCameraId(CameraCharacteristics.LENS_FACING_FRONT)
            } else {
                findCameraId(CameraCharacteristics.LENS_FACING_BACK)
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
    
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            if (cameraId == null) return
            cameraManager.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera")
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied")
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
                2
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
                    showCaptureFeedback(bitmap)
                    Toast.makeText(
                        this@CameraActivity,
                        "Photo saved!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } finally {
            image?.close()
        }
    }
    
    // ==================== 📸 CAPTURE PHOTO ====================
    
    private fun capturePhoto() {
        if (cameraDevice == null || imageReader == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }
        
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
            "TORCH" -> {
                builder.set(CaptureRequest.FLASH_MODE, 
                    CaptureRequest.FLASH_MODE_TORCH)
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
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
    
    private fun toggleVideoRecording() {
        if (isRecording) {
            stopRecording()
            btnVideoRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            recordingIndicator.visibility = View.GONE
            tvRecordingTimer.visibility = View.GONE
            recordingTimerHandler.removeCallbacksAndMessages(null)
        } else {
            if (startRecording()) {
                btnVideoRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                recordingIndicator.visibility = View.VISIBLE
                tvRecordingTimer.visibility = View.VISIBLE
                recordingStartTime = System.currentTimeMillis()
                startRecordingTimer()
            }
        }
    }
    
    private fun startRecording(): Boolean {
        return try {
            setupMediaRecorder()
            mediaRecorder?.start()
            isRecording = true
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show()
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
            
            // Use optimal size
            val videoWidth = previewSize?.width ?: 1920
            val videoHeight = previewSize?.height ?: 1080
            setVideoSize(videoWidth, videoHeight)
            
            videoFile = File(
                getExternalFilesDir(null), 
                "VID_${System.currentTimeMillis()}.mp4"
            )
            setOutputFile(videoFile!!.absolutePath)
            
            prepare()
        }
    }
    
    private fun startRecordingTimer() {
        recordingTimerHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsedTime = System.currentTimeMillis() - recordingStartTime
                    val seconds = (elapsedTime / 1000) % 60
                    val minutes = (elapsedTime / (1000 * 60)) % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)
                    tvRecordingTimer.text = timeString
                    recordingTimerHandler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }
    
    // ==================== 🔦 FLASH CONTROL ====================
    
    private fun toggleFlashMode() {
        currentFlashMode = when (currentFlashMode) {
            "AUTO" -> {
                btnFlash.text = "🔆 ON"
                "ON"
            }
            "ON" -> {
                btnFlash.text = "⚫ OFF"
                "OFF"
            }
            "OFF" -> {
                btnFlash.text = "🔦 TORCH"
                "TORCH"
            }
            else -> {
                btnFlash.text = "⚡ AUTO"
                "AUTO"
            }
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
        isFrontCamera = !isFrontCamera
        
        btnSwitchCamera.text = if (isFrontCamera) "📱 Front" else "📷 Back"
        
        // Small animation
        btnSwitchCamera.animate()
            .rotationBy(180f)
            .setDuration(300)
            .start()
        
        openCamera()
    }
    
    // ==================== ⚙️ PRO CONTROLS ====================
    
    private fun toggleProControls() {
        isProControlsExpanded = !isProControlsExpanded
        
        if (isProControlsExpanded) {
            proControlsPanel.visibility = View.VISIBLE
            btnExpandControls.setImageResource(R.drawable.ic_collapse_pro)
            scrollProControls.smoothScrollTo(0, 0)
        } else {
            proControlsPanel.visibility = View.GONE
            btnExpandControls.setImageResource(R.drawable.ic_expand_pro)
        }
    }
    
    private fun applyZoom(zoomLevel: Float) {
        try {
            // Simple zoom implementation
            // For real digital zoom, you'd need to set SCALER_CROP_REGION
            previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, null)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), 
                null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Zoom failed")
        }
    }
    
    private fun setFocusArea(x: Float, y: Float) {
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
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Focus failed")
        }
    }
    
    private fun showFocusIndicator(x: Float, y: Float) {
        focusIndicator.x = x - focusIndicator.width / 2
        focusIndicator.y = y - focusIndicator.height / 2
        focusIndicator.visibility = View.VISIBLE
        focusIndicator.playAnimation()
        
        Handler(Looper.getMainLooper()).postDelayed({
            focusIndicator.visibility = View.GONE
        }, 1000)
    }
    
    private fun showCaptureFeedback(bitmap: Bitmap) {
        val captureFeedback = findViewById<android.widget.ImageView>(R.id.capture_feedback)
        captureFeedback.setImageBitmap(bitmap)
        captureFeedback.visibility = View.VISIBLE
        captureFeedback.alpha = 0.7f
        
        captureFeedback.animate()
            .alpha(0f)
            .setDuration(500)
            .withEndAction {
                captureFeedback.visibility = View.GONE
            }
            .start()
    }
    
    // ==================== 🎯 MODES ====================
    
    private fun setMode(mode: String) {
        when (mode) {
            "PRO" -> {
                btnExpandControls.visibility = View.VISIBLE
            }
            else -> {
                btnExpandControls.visibility = View.GONE
                proControlsPanel.visibility = View.GONE
                isProControlsExpanded = false
            }
        }
        Toast.makeText(this, "Mode: $mode", Toast.LENGTH_SHORT).show()
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
    
    private fun calculateTapArea(x: Float, y: Float, areaSize: Float): Rect {
        val area = (areaSize * 10).toInt()
        val left = ((x * 2000 / textureView.width - 1000 - area / 2).toInt())
            .coerceIn(-1000, 1000)
        val top = ((y * 2000 / textureView.height - 1000 - area / 2).toInt())
            .coerceIn(-1000, 1000)
        return Rect(left, top, left + area, top + area)
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
    
    // ==================== 📁 GALLERY & SETTINGS ====================
    
    private fun openGallery() {
        Toast.makeText(this, "Opening Gallery", Toast.LENGTH_SHORT).show()
        // Implement gallery intent here
    }
    
    private fun openSettings() {
        Toast.makeText(this, "Opening Settings", Toast.LENGTH_SHORT).show()
        // Implement settings activity here
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
                Toast.makeText(
                    this,
                    "Permissions required!",
                    Toast.LENGTH_LONG
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
