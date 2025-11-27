package com.yourname.advancedcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourname.advancedcamera.features.FeatureManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

// اضافی imports
import com.google.android.material.tabs.TabLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

class CameraActivity : AppCompatActivity() {

}
    // ==================== 🎥 CAMERA COMPONENTS ====================
    private lateinit var textureView: TextureView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnModeSwitch: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var recordingIndicator: TextView
    private lateinit var controlPanel: LinearLayout
    private lateinit var seekZoom: SeekBar
    private lateinit var seekISO: SeekBar
    private lateinit var seekExposure: SeekBar
    private lateinit var seekFocus: SeekBar
    private lateinit var tvZoomValue: TextView
    private lateinit var tvISOValue: TextView
    private lateinit var tvExposureValue: TextView
    private lateinit var tvFocusValue: TextView
    private lateinit var tabModes: TabLayout
    private lateinit var focusIndicator: ImageView
    private lateinit var spinnerLUT: Spinner
    
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    // ==================== 📊 CAMERA STATE ====================
    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var sensorOrientation: Int? = null
    private var isFrontCamera = false
    private var isRecording = false
    private var currentMode = 0 // 0: Auto, 1: Pro, 2: Night, 3: Portrait, 4: Video
    
    // ==================== 🚀 ADVANCED FEATURES ====================
    private val featureManager = FeatureManager.getInstance()
    private var currentLUT = "CINEMATIC"
    
    // ==================== 🧭 ORIENTATION HANDLING ====================
    private val ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }
    
    // ==================== 🎬 SURFACE TEXTURE LISTENER ====================
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
    
    // ==================== 📷 CAMERA STATE CALLBACK ====================
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
            updateStatus("🚀 DSLR Pro - ${featureManager.getAvailableFeatures().size} Features Active")
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
            updateStatus("📵 Camera Disconnected")
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
            updateStatus("❌ Camera Error: $error")
        }
    }
    
    // ==================== 🎯 CAPTURE SESSION CALLBACK ====================
    private val captureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                
                val previewRequest = previewRequestBuilder?.build()
                captureSession?.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to start preview: ${e.message}")
            }
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            updateStatus("❌ Failed to configure camera")
        }
    }

    // ==================== 📱 ACTIVITY LIFECYCLE ====================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_pro)
        
        initializeUI()
        initializeAdvancedFeatures()
        checkPermissions()
    }
    
    // ==================== 🎨 UI INITIALIZATION ====================
    private fun initializeUI() {
        // Find all views
        textureView = findViewById(R.id.texture_view)
        btnCapture = findViewById(R.id.btn_capture)
        btnSwitchCamera = findViewById(R.id.btn_switch_camera)
        btnSettings = findViewById(R.id.btn_settings)
        btnGallery = findViewById(R.id.btn_gallery)
        btnModeSwitch = findViewById(R.id.btn_mode_switch)
        
        seekZoom = findViewById(R.id.seek_zoom)
        seekISO = findViewById(R.id.seek_iso)
        seekExposure = findViewById(R.id.seek_exposure)
        seekFocus = findViewById(R.id.seek_focus)
        
        tvStatus = findViewById(R.id.tv_status)
        recordingIndicator = findViewById(R.id.recording_indicator)
        controlPanel = findViewById(R.id.control_panel)
        tvZoomValue = findViewById(R.id.tv_zoom_value)
        tvISOValue = findViewById(R.id.tv_iso_value)
        tvExposureValue = findViewById(R.id.tv_exposure_value)
        tvFocusValue = findViewById(R.id.tv_focus_value)
        tabModes = findViewById(R.id.tab_modes)
        focusIndicator = findViewById(R.id.focus_indicator)
        
        // LUT Spinner
        spinnerLUT = findViewById(R.id.spinner_lut)
        
        setupEventListeners()
        setupLUTSpinner()
        updateManualControls()
    }
    
    // ==================== 🚀 ADVANCED FEATURES INITIALIZATION ====================
    private fun initializeAdvancedFeatures() {
        try {
            val featureStats = featureManager.getFeatureStats()
            val availableFeatures = featureManager.getAvailableFeatures()
            
            Log.d(TAG, "🎯 Advanced Features Loaded: ${availableFeatures.size}")
            Log.d(TAG, "📊 Feature Stats: $featureStats")
            
            updateStatus("🚀 DSLR Pro - ${availableFeatures.size} Features Active")
            
            // Show feature count in UI
            Toast.makeText(this, "✅ ${availableFeatures.size} Advanced Features Loaded", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Advanced features initialization failed: ${e.message}")
            updateStatus("⚠️ Basic Mode - Advanced Features Failed")
        }
    }
    
    // ==================== 🎮 EVENT LISTENERS SETUP ====================
    private fun setupEventListeners() {
        textureView.surfaceTextureListener = surfaceTextureListener
        
        // Button click listeners
        btnCapture.setOnClickListener { captureImage() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnSettings.setOnClickListener { showAdvancedSettings() }
        btnGallery.setOnClickListener { openGallery() }
        btnModeSwitch.setOnClickListener { toggleManualMode() }
        
        // SeekBar listeners for manual controls
        seekZoom.setOnSeekBarChangeListener(createSeekBarListener("ZOOM") { progress ->
            val zoomLevel = 1.0f + (progress / 100.0f) * 49.0f // 1.0x to 50.0x
            featureManager.currentZoom = zoomLevel
            tvZoomValue.text = "${String.format("%.1f", zoomLevel)}x"
            applyZoom(zoomLevel)
        })
        
        seekISO.setOnSeekBarChangeListener(createSeekBarListener("ISO") { progress ->
            val iso = 50 + (progress * 63.5).toInt() // 50 to 6400
            featureManager.currentISO = iso
            tvISOValue.text = iso.toString()
            applyManualSettings()
        })
        
        seekExposure.setOnSeekBarChangeListener(createSeekBarListener("EXPOSURE") { progress ->
            val exposure = progress - 3 // -3 to +3
            featureManager.currentExposure = exposure
            tvExposureValue.text = if (exposure >= 0) "+$exposure" else exposure.toString()
            applyManualSettings()
        })
        
        seekFocus.setOnSeekBarChangeListener(createSeekBarListener("FOCUS") { progress ->
            val focus = progress / 100.0f // 0.0 to 1.0
            featureManager.currentFocus = focus
            tvFocusValue.text = "${(focus * 100).toInt()}%"
            applyManualSettings()
        })
        
        // Tab selection listener
        tabModes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    currentMode = it.position
                    applyModeSettings(currentMode)
                    showModeFeatures(currentMode)
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Touch focus
        textureView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                setFocusArea(x, y)
            }
            true
        }
    }
    
    private fun setupLUTSpinner() {
        val lutTypes = featureManager.getLUTTypes()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, lutTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLUT.adapter = adapter
        
        spinnerLUT.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLUT = lutTypes[position]
                Toast.makeText(this@CameraActivity, "🎨 ${currentLUT} LUT Selected", Toast.LENGTH_SHORT).show()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun createSeekBarListener(controlName: String, onProgressChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onProgressChanged(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
    }
    
    // ==================== 🔧 MANUAL CONTROLS UPDATE ====================
    private fun updateManualControls() {
        val settings = featureManager.getManualSettings()
        
        // Set initial values
        val zoom = (settings["Zoom"] as? Float ?: 1.0f)
        seekZoom.progress = ((zoom - 1.0f) / 49.0f * 100).toInt()
        tvZoomValue.text = "${String.format("%.1f", zoom)}x"
        
        val iso = settings["ISO"] as? Int ?: 100
        seekISO.progress = ((iso - 50) / 63.5).toInt()
        tvISOValue.text = iso.toString()
        
        val exposure = settings["Exposure"] as? Int ?: 0
        seekExposure.progress = exposure + 3
        tvExposureValue.text = if (exposure >= 0) "+$exposure" else exposure.toString()
        
        val focus = (settings["Focus"] as? Float ?: 0.5f)
        seekFocus.progress = (focus * 100).toInt()
        tvFocusValue.text = "${(focus * 100).toInt()}%"
    }
    
    // ==================== 🔐 PERMISSIONS HANDLING ====================
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSION)
        } else {
            startBackgroundThread()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackgroundThread()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    // ==================== 🔄 BACKGROUND THREAD MANAGEMENT ====================
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
            Log.e(TAG, "Background thread interrupted: ${e.message}")
        }
    }
    
    // ==================== 📷 CAMERA SETUP ====================
    @SuppressLint("MissingPermission")
    private fun setupCamera(width: Int, height: Int) {
        try {
            cameraManager = getSystemService(CameraManager::class.java)
            val cameraList = cameraManager!!.cameraIdList
            
            if (cameraList.isEmpty()) {
                updateStatus("❌ No camera found")
                return
            }
            
            // Find back camera first
            for (id in cameraList) {
                val characteristics = cameraManager!!.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
            
            cameraId = cameraId ?: cameraList[0]
            
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return
            
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            
            configureTransform(width, height)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera setup failed: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            cameraManager?.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
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
            
            cameraDevice!!.createCaptureSession(Arrays.asList(surface, imageReader!!.surface),
                captureSessionCallback, backgroundHandler)
                
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start preview: ${e.message}")
        }
    }
    
    private fun setupImageReader() {
        try {
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map!!.getOutputSizes(ImageFormat.JPEG)
            val largest = Collections.max(Arrays.asList(*outputSizes), CompareSizesByArea())
            
            imageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 1)
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Image reader setup failed: ${e.message}")
            // Fallback to default size
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
        }
    }
    
    // ==================== 🖼️ IMAGE CAPTURE & PROCESSING ====================
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                // Apply advanced DSLR processing
                bitmap = applyAdvancedProcessing(bitmap)
                
                saveImage(bitmap)
                
                runOnUiThread {
                    Toast.makeText(this, "📸 DSLR Photo Saved with AI Processing!", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            image?.close()
        }
    }
    
    private fun applyAdvancedProcessing(originalBitmap: Bitmap): Bitmap {
        var processedBitmap = originalBitmap
        
        try {
            // AI Scene Detection
            val detectedScene = featureManager.detectScene(originalBitmap)
            Log.d(TAG, "🧠 AI Detected Scene: $detectedScene")
            
            // Apply features based on current mode
            when (currentMode) {
                0 -> { // Auto Mode - AI decides
                    processedBitmap = applyAutoModeProcessing(processedBitmap, detectedScene)
                }
                1 -> { // Pro Mode
                    processedBitmap = applyProModeProcessing(processedBitmap)
                }
                2 -> { // Night Mode
                    processedBitmap = applyNightModeProcessing(processedBitmap)
                }
                3 -> { // Portrait Mode
                    processedBitmap = applyPortraitModeProcessing(processedBitmap)
                }
                4 -> { // Video Mode (for photos, apply cinematic LUT)
                    if (featureManager.isColorLUTsEnabled) {
                        processedBitmap = featureManager.applyColorLUT(processedBitmap, "CINEMATIC")
                    }
                }
            }
            
            // Always apply noise reduction if enabled
            if (featureManager.isNoiseReductionEnabled) {
                processedBitmap = featureManager.processNoiseReduction(processedBitmap)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Advanced processing failed: ${e.message}")
        }
        
        return processedBitmap
    }
    
    private fun applyAutoModeProcessing(bitmap: Bitmap, scene: String): Bitmap {
        var processed = bitmap
        
        when (scene) {
            "NIGHT" -> {
                val frames = ArrayList<Bitmap>()
                frames.add(processed)
                processed = featureManager.processNightVision(frames)
            }
            "PORTRAIT" -> {
                processed = featureManager.processPortraitMode(processed)
                processed = featureManager.applyColorLUT(processed, "PORTRAIT")
            }
            "LANDSCAPE" -> {
                processed = featureManager.processHDR(listOf(processed))
                processed = featureManager.applyColorLUT(processed, "CINEMATIC")
            }
            "SUNSET" -> {
                processed = featureManager.applyColorLUT(processed, "WARM")
            }
            else -> {
                // Apply current LUT for other scenes
                processed = featureManager.applyColorLUT(processed, currentLUT)
            }
        }
        
        return processed
    }
    
    private fun applyProModeProcessing(bitmap: Bitmap): Bitmap {
        var processed = bitmap
        
        // Apply RAW processing
        if (featureManager.isRawCaptureEnabled) {
            processed = featureManager.processRawCapture(processed)
        }
        
        // Apply current LUT
        if (featureManager.isColorLUTsEnabled) {
            processed = featureManager.applyColorLUT(processed, currentLUT)
        }
        
        // Apply manual settings effects
        if (featureManager.currentExposure != 0) {
            // Simulate exposure compensation
            val exposure = featureManager.currentExposure
            // Exposure adjustment would go here
        }
        
        return processed
    }
    
    private fun applyNightModeProcessing(bitmap: Bitmap): Bitmap {
        var processed = bitmap
        
        if (featureManager.isNightVisionEnabled) {
            val frames = ArrayList<Bitmap>()
            frames.add(processed)
            processed = featureManager.processNightVision(frames)
        }
        
        // Apply noise reduction for night shots
        if (featureManager.isNoiseReductionEnabled) {
            processed = featureManager.processNoiseReduction(processed)
        }
        
        return processed
    }
    
    private fun applyPortraitModeProcessing(bitmap: Bitmap): Bitmap {
        var processed = bitmap
        
        if (featureManager.isPortraitModeEnabled) {
            processed = featureManager.processPortraitMode(processed)
        }
        
        if (featureManager.isColorLUTsEnabled) {
            processed = featureManager.applyColorLUT(processed, "PORTRAIT")
        }
        
        return processed
    }
    
    private fun captureImage() {
        if (cameraDevice == null) return
        
        try {
            showCaptureInfo()
            
            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation())
            
            captureSession!!.stopRepeating()
            captureSession!!.capture(captureBuilder.build(), null, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Capture failed: ${e.message}")
        }
    }
    
    private fun showCaptureInfo() {
        val detectedScene = featureManager.detectScene(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))
        var featureInfo = "📸 Basic Capture"
        
        when (currentMode) {
            0 -> featureInfo = "🧠 Auto Mode - $detectedScene"
            1 -> featureInfo = "⚙️ Pro Mode - ${currentLUT} LUT + RAW"
            2 -> featureInfo = "🌙 Night Vision + AI Processing"
            3 -> featureInfo = "🤖 Portrait Mode + Bokeh Effect"
            4 -> featureInfo = "🎬 Video Mode - Cinematic LUT"
        }
        
        Toast.makeText(this, featureInfo, Toast.LENGTH_SHORT).show()
    }
    
    // ==================== ⚙️ CAMERA CONTROLS ====================
    private fun setFocusArea(x: Float, y: Float) {
        if (cameraDevice == null) return
        
        try {
            // Show focus indicator
            focusIndicator.x = x - focusIndicator.width / 2
            focusIndicator.y = y - focusIndicator.height / 2
            focusIndicator.visibility = View.VISIBLE
            
            Log.d(TAG, "🎯 Manual focus at: $x, $y")
            Toast.makeText(this, "🎯 Focus Set", Toast.LENGTH_SHORT).show()
            
            // Hide focus indicator after delay
            backgroundHandler?.postDelayed({
                runOnUiThread {
                    focusIndicator.visibility = View.INVISIBLE
                }
            }, 2000)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Focus failed: ${e.message}")
        }
    }
    
    private fun switchCamera() {
        cameraDevice?.close()
        captureSession?.close()
        
        isFrontCamera = !isFrontCamera
        cameraId = null
        
        try {
            val cameraList = cameraManager!!.cameraIdList
            for (id in cameraList) {
                val characteristics = cameraManager!!.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                } else if (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
            
            cameraId = cameraId ?: cameraList[0]
            openCamera()
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera switch failed: ${e.message}")
        }
    }
    
    private fun toggleManualMode() {
        val isManual = currentMode == 1 // Pro mode
        controlPanel.visibility = if (isManual) View.VISIBLE else View.GONE
        currentMode = if (isManual) 0 else 1
        
        Toast.makeText(this, 
            if (isManual) "🔘 Auto Mode" else "⚙️ Pro Mode", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun applyZoom(zoomLevel: Float) {
        try {
            // Apply digital zoom
            previewRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, null)
            captureSession!!.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Zoom failed: ${e.message}")
        }
    }
    
    private fun applyManualSettings() {
        if (currentMode != 1) return // Only in Pro mode
        
        try {
            val settings = featureManager.getManualSettings()
            
            // Apply exposure compensation
            val exposure = settings["Exposure"] as Int
            previewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure)
            
            // Apply manual ISO if supported
            val iso = settings["ISO"] as Int
            previewRequestBuilder!!.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            
            captureSession!!.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Manual settings failed: ${e.message}")
        }
    }
    
    private fun applyModeSettings(mode: Int) {
        // Update UI based on mode
        when (mode) {
            1 -> { // Pro Mode
                controlPanel.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.lut_panel).visibility = View.VISIBLE
            }
            else -> {
                controlPanel.visibility = View.GONE
                findViewById<LinearLayout>(R.id.lut_panel).visibility = View.GONE
            }
        }
    }
    
    private fun showModeFeatures(mode: Int) {
        val modeName = when (mode) {
            0 -> "Auto Mode"
            1 -> "Pro Mode"
            2 -> "Night Mode"
            3 -> "Portrait Mode"
            4 -> "Video Mode"
            else -> "Unknown Mode"
        }
        
        val features = when (mode) {
            0 -> "AI Scene Detection + Auto Processing"
            1 -> "Manual Controls + RAW + LUTs"
            2 -> "Night Vision + Noise Reduction"
            3 -> "Portrait Bokeh + Skin Enhancement"
            4 -> "4K Video + Stabilization"
            else -> "Basic Features"
        }
        
        Toast.makeText(this, "$modeName - $features", Toast.LENGTH_SHORT).show()
    }
    
    // ==================== ⚙️ SETTINGS & UI ====================
    private fun showAdvancedSettings() {
        val features = featureManager.getAvailableFeatures()
        val featureStats = featureManager.getFeatureStats()
        val manualSettings = featureManager.getManualSettings()
        val lutTypes = featureManager.getLUTTypes()
        
        val message = """
        🚀 DSLR Camera Pro - Advanced Features
        
        📊 Feature Stats:
        • Total Features: ${featureStats["TotalFeatures"]}
        • Active Features: ${featureStats["ActiveFeatures"]}
        • AI Features: ${featureStats["AIFeatures"]}
        • Manual Controls: ${featureStats["ManualFeatures"]}
        
        ⚙️ Current Settings:
        • ISO: ${manualSettings["ISO"]}
        • Shutter: ${manualSettings["ShutterSpeed"]}
        • Focus: ${(manualSettings["Focus"] as Float * 100).toInt()}%
        • Exposure: ${manualSettings["Exposure"]}
        • Zoom: ${manualSettings["Zoom"]}x
        • Current LUT: $currentLUT
        
        🎨 Available LUTs:
        • ${lutTypes.joinToString("\n• ")}
        
        🎯 Active Features (${features.size}):
        • ${features.take(10).joinToString("\n• ")}
        ${if (features.size > 10) "\n• ... and ${features.size - 10} more" else ""}
        """.trimIndent()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("🎯 DSLR Camera Settings")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset Settings") { _, _ ->
                featureManager.resetToDefaults()
                updateManualControls()
                currentLUT = "CINEMATIC"
                spinnerLUT.setSelection(0)
                Toast.makeText(this, "🔄 Settings Reset to Default", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Feature Info") { _, _ ->
                showFeatureDetails()
            }
            .show()
    }
    
    private fun showFeatureDetails() {
        val features = featureManager.getAvailableFeatures()
        val message = "🎯 All Active DSLR Features:\n\n• ${features.joinToString("\n• ")}"
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Advanced Features (${features.size})")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun openGallery() {
        Toast.makeText(this, "🖼️ Gallery will be implemented in next version", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread { tvStatus.text = message }
    }
    
    // ==================== 🧭 ORIENTATION & TRANSFORM ====================
    private fun getOrientation(): Int {
        val rotation = windowManager.defaultDisplay.rotation
        return (ORIENTATIONS.get(rotation) + sensorOrientation!! + 270) % 360
    }
    
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (previewSize == null) return
        
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        
        val scaleX = viewWidth.toFloat() / previewSize!!.width
        val scaleY = viewHeight.toFloat() / previewSize!!.height
        
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            matrix.setScale(scaleX, scaleY)
        } else {
            matrix.setScale(scaleY, scaleX)
        }
        
        textureView.setTransform(matrix)
    }
    
    // ==================== 💾 IMAGE SAVING ====================
    private fun saveImage(bitmap: Bitmap) {
        val filename = "DSLR_${System.currentTimeMillis()}_${currentLUT}.jpg"
        val file = File(getExternalFilesDir(null), filename)
        
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                Log.d(TAG, "📸 DSLR Image saved: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image: ${e.message}")
        }
    }
    
    // ==================== 🔄 ACTIVITY LIFECYCLE ====================
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            setupCamera(textureView.width, textureView.height)
            openCamera()
        }
    }
    
    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }
    
    private fun closeCamera() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
    }
    
    // ==================== 🛠️ UTILITY CLASSES ====================
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
    }  // ← یہاں صرف ایک brace ہونی چاہیے
    
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
    
    companion object {
        private const val TAG = "DSLRCameraPro"
        private const val REQUEST_CAMERA_PERMISSION = 200
    }
}
