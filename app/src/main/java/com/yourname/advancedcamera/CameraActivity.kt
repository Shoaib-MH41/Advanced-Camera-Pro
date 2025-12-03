package com.yourname.advancedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.yourname.advancedcamera.features.FeatureManager
import com.yourname.advancedcamera.managers.CameraManager
import com.yourname.advancedcamera.managers.FileSaver
import com.yourname.advancedcamera.processors.ImageProcessor

class CameraActivity : AppCompatActivity() {
    
    // ==================== 🎥 UI COMPONENTS ====================
    private lateinit var textureView: TextureView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnModeSwitch: ImageButton
    private lateinit var btnVideoRecord: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var recordingIndicator: TextView
    private lateinit var controlPanel: LinearLayout
    private lateinit var lutPanel: LinearLayout  // ✅ نیا اضافہ
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
    
    // ==================== 🚀 MANAGERS ====================
    private lateinit var cameraManager: CameraManager
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var fileSaver: FileSaver
    private val featureManager = FeatureManager.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())  // ✅ بہتر Handler
    
    // ==================== 📊 APP STATE ====================
    private var currentMode = 0
    private var currentLUT = "CINEMATIC"
    private var isRecording = false
    private var currentFlashMode = "AUTO"
    private var isManualModeActive = false  // ✅ نیا state

    companion object {
        private const val TAG = "DSLRCameraPro"
        private const val REQUEST_CAMERA_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)  // ✅ درست کیا گیا!
        
        Log.d(TAG, "🎬 CameraActivity Created")
        
        try {
            initializeUI()
            initializeManagers()
            setupEventListeners()
            initializeAdvancedFeatures()
            checkPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
            Toast.makeText(this, "App initialization failed. Please restart.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    // ==================== 🎯 UI INITIALIZATION ====================
    private fun initializeUI() {
        try {
            // ✅ تمام UI elements کو ایک ہی function میں initialize کریں
            textureView = findViewById(R.id.texture_view)
            btnCapture = findViewById(R.id.btn_capture)
            btnSwitchCamera = findViewById(R.id.btn_switch_camera)
            btnSettings = findViewById(R.id.btn_settings)
            btnGallery = findViewById(R.id.btn_gallery)
            btnModeSwitch = findViewById(R.id.btn_mode_switch)
            btnVideoRecord = findViewById(R.id.btn_video_record)
            btnFlash = findViewById(R.id.btn_flash)
            
            seekZoom = findViewById(R.id.seek_zoom)
            seekISO = findViewById(R.id.seek_iso)
            seekExposure = findViewById(R.id.seek_exposure)
            seekFocus = findViewById(R.id.seek_focus)
            
            tvStatus = findViewById(R.id.tv_status)
            recordingIndicator = findViewById(R.id.recording_indicator)
            controlPanel = findViewById(R.id.control_panel)
            lutPanel = findViewById(R.id.lut_panel)  // ✅ درست reference
            tvZoomValue = findViewById(R.id.tv_zoom_value)
            tvISOValue = findViewById(R.id.tv_iso_value)
            tvExposureValue = findViewById(R.id.tv_exposure_value)
            tvFocusValue = findViewById(R.id.tv_focus_value)
            tabModes = findViewById(R.id.tab_modes)
            focusIndicator = findViewById(R.id.focus_indicator)
            spinnerLUT = findViewById(R.id.spinner_lut)
            
            // ✅ ابتدائی visibility
            controlPanel.visibility = View.GONE
            lutPanel.visibility = View.GONE
            recordingIndicator.visibility = View.GONE
            focusIndicator.visibility = View.INVISIBLE
            
            setupLUTSpinner()
            updateManualControls()
            updateFlashIcon()
            
            Log.d(TAG, "✅ UI Initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ UI Initialization error: ${e.message}", e)
            throw e  // دوبارہ throw کریں تاکہ onCreate میں catch ہو جائے
        }
    }
    
    private fun initializeManagers() {
        // ✅ ترتیب میں initialization
        cameraManager = CameraManager(this, textureView).apply {
            setErrorCallback { error ->
                runOnUiThread {
                    Toast.makeText(this@CameraActivity, "Camera Error: $error", Toast.LENGTH_SHORT).show()
                    updateStatus("⚠️ Camera Error: $error")
                }
            }
        }
        
        imageProcessor = ImageProcessor()
        fileSaver = FileSaver(this)
        
        // ✅ TextureView listener
        textureView.surfaceTextureListener = cameraManager.getSurfaceTextureListener()
    }
    
    private fun initializeAdvancedFeatures() {
        try {
            val availableFeatures = featureManager.getAvailableFeatures()
            updateStatus("🚀 DSLR Pro - ${availableFeatures.size} Features Active")
            Toast.makeText(this, "✅ ${availableFeatures.size} Advanced Features Loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Advanced features initialization failed: ${e.message}", e)
            updateStatus("⚠️ Basic Mode - Advanced Features Failed")
        }
    }
    
    // ==================== 🎮 EVENT LISTENERS ====================
    private fun setupEventListeners() {
        // ✅ تمام button click listeners
        btnCapture.setOnClickListener { captureImage() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnSettings.setOnClickListener { showAdvancedSettings() }
        btnGallery.setOnClickListener { openGallery() }
        btnModeSwitch.setOnClickListener { toggleManualMode() }
        btnVideoRecord.setOnClickListener { toggleVideoRecording() }
        btnFlash.setOnClickListener { toggleFlashMode() }
        
        // ✅ SeekBar listeners
        setupSeekBarListeners()
        
        // ✅ Tab selection
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
        
        // ✅ Touch focus
        textureView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                setFocusArea(x, y)
            }
            true
        }
        
        Log.d(TAG, "✅ Event listeners setup completed")
    }
    
    private fun setupSeekBarListeners() {
        seekZoom.setOnSeekBarChangeListener(createSeekBarListener("ZOOM") { progress ->
            try {
                val zoomLevel = 1.0f + (progress / 100.0f) * 49.0f
                featureManager.currentZoom = zoomLevel
                tvZoomValue.text = "${String.format("%.1f", zoomLevel)}x"
                cameraManager.applyZoom(zoomLevel)
            } catch (e: Exception) {
                Log.e(TAG, "Zoom error: ${e.message}")
            }
        })
        
        seekISO.setOnSeekBarChangeListener(createSeekBarListener("ISO") { progress ->
            try {
                val iso = 50 + (progress * 63.5).toInt()
                featureManager.currentISO = iso
                tvISOValue.text = iso.toString()
                cameraManager.applyManualSettings()
            } catch (e: Exception) {
                Log.e(TAG, "ISO error: ${e.message}")
            }
        })
        
        seekExposure.setOnSeekBarChangeListener(createSeekBarListener("EXPOSURE") { progress ->
            try {
                val exposure = progress - 3
                featureManager.currentExposure = exposure
                tvExposureValue.text = if (exposure >= 0) "+$exposure" else exposure.toString()
                cameraManager.applyManualSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Exposure error: ${e.message}")
            }
        })
        
        seekFocus.setOnSeekBarChangeListener(createSeekBarListener("FOCUS") { progress ->
            try {
                val focus = progress / 100.0f
                featureManager.currentFocus = focus
                tvFocusValue.text = "${(focus * 100).toInt()}%"
                cameraManager.applyManualSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Focus error: ${e.message}")
            }
        })
    }
    
    // ==================== ⚡ FLASH CONTROL ====================
    private fun toggleFlashMode() {
        currentFlashMode = when (currentFlashMode) {
            "AUTO" -> "ON"
            "ON" -> "OFF"
            "OFF" -> "TORCH"
            "TORCH" -> "AUTO"
            else -> "AUTO"
        }
        
        updateFlashIcon()
        cameraManager.applyFlashMode(currentFlashMode)
        Toast.makeText(this, "⚡ Flash: $currentFlashMode", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateFlashIcon() {
        when (currentFlashMode) {
            "AUTO" -> btnFlash.setImageResource(R.drawable.ic_flash_auto)
            "ON" -> btnFlash.setImageResource(R.drawable.ic_flash_on)
            "OFF" -> btnFlash.setImageResource(R.drawable.ic_flash_off)
            "TORCH" -> btnFlash.setImageResource(R.drawable.ic_flash_on)
        }
    }
    
    // ==================== 🎬 VIDEO RECORDING ====================
    private fun toggleVideoRecording() {
        if (!isRecording) {
            startVideoRecording()
        } else {
            stopVideoRecording()
        }
    }
    
    private fun startVideoRecording() {
        try {
            if (cameraManager.startVideoRecording()) {
                isRecording = true
                runOnUiThread {
                    btnVideoRecord.setBackgroundResource(R.drawable.btn_video_recording)
                    btnVideoRecord.setImageResource(R.drawable.ic_video_stop)
                    recordingIndicator.visibility = View.VISIBLE
                    Toast.makeText(this, "🎬 Video Recording Started", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "❌ Failed to start video recording", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video recording start error: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "❌ Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopVideoRecording() {
        try {
            val videoFile = cameraManager.stopVideoRecording()
            if (videoFile != null) {
                fileSaver.saveVideo(videoFile)
                runOnUiThread {
                    Toast.makeText(this, "✅ Video Saved to Gallery", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "❌ Failed to save video", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video recording stop error: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "❌ Save error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            isRecording = false
            runOnUiThread {
                btnVideoRecord.setBackgroundResource(R.drawable.btn_video_record)
                btnVideoRecord.setImageResource(R.drawable.ic_video_record)
                recordingIndicator.visibility = View.GONE
            }
        }
    }
    
    // ==================== 📷 CAMERA ACTIONS ====================
    private fun captureImage() {
        try {
            cameraManager.captureImage { bitmap ->
                try {
                    val processedBitmap = imageProcessor.applyAdvancedProcessing(
                        bitmap, currentMode, currentLUT, featureManager
                    )
                    fileSaver.saveImage(processedBitmap, currentLUT)
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "✅ Image Saved!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Image processing error: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "❌ Processing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture error: ${e.message}", e)
            Toast.makeText(this, "❌ Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun switchCamera() {
        try {
            cameraManager.switchCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Camera switch error: ${e.message}", e)
            Toast.makeText(this, "❌ Camera switch failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setFocusArea(x: Float, y: Float) {
        try {
            cameraManager.setFocusArea(x, y)
            
            // Show focus indicator
            runOnUiThread {
                focusIndicator.x = x - focusIndicator.width / 2
                focusIndicator.y = y - focusIndicator.height / 2
                focusIndicator.visibility = View.VISIBLE
            }
            
            // Hide after delay using mainHandler
            mainHandler.postDelayed({
                runOnUiThread {
                    focusIndicator.visibility = View.INVISIBLE
                }
            }, 2000L)
        } catch (e: Exception) {
            Log.e(TAG, "Focus error: ${e.message}", e)
        }
    }
    
    // ==================== ⚙️ UI CONTROLS ====================
    private fun setupLUTSpinner() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "LUT spinner error: ${e.message}", e)
        }
    }
    
    private fun createSeekBarListener(controlName: String, onProgressChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        onProgressChanged(progress)
                    } catch (e: Exception) {
                        Log.e(TAG, "$controlName control error: ${e.message}")
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
    }
    
    private fun updateManualControls() {
        try {
            val settings = featureManager.getManualSettings()
            
            settings["Zoom"]?.let {
                val zoom = it as? Float ?: 1.0f
                seekZoom.progress = ((zoom - 1.0f) / 49.0f * 100).toInt()
                tvZoomValue.text = "${String.format("%.1f", zoom)}x"
            }
            
            settings["ISO"]?.let {
                val iso = it as? Int ?: 100
                seekISO.progress = ((iso - 50) / 63.5).toInt()
                tvISOValue.text = iso.toString()
            }
            
            settings["Exposure"]?.let {
                val exposure = it as? Int ?: 0
                seekExposure.progress = exposure + 3
                tvExposureValue.text = if (exposure >= 0) "+$exposure" else exposure.toString()
            }
            
            settings["Focus"]?.let {
                val focus = it as? Float ?: 0.5f
                seekFocus.progress = (focus * 100).toInt()
                tvFocusValue.text = "${(focus * 100).toInt()}%"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual controls update error: ${e.message}", e)
        }
    }
    
    private fun toggleManualMode() {
        isManualModeActive = !isManualModeActive
        currentMode = if (isManualModeActive) 1 else 0
        
        applyModeSettings(currentMode)
        
        Toast.makeText(this, 
            if (isManualModeActive) "⚙️ Pro Mode" else "🔘 Auto Mode", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun applyModeSettings(mode: Int) {
        runOnUiThread {
            when (mode) {
                1 -> {
                    controlPanel.visibility = View.VISIBLE
                    lutPanel.visibility = View.VISIBLE  // ✅ درست reference
                }
                else -> {
                    controlPanel.visibility = View.GONE
                    lutPanel.visibility = View.GONE  // ✅ درست reference
                }
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
    
    // ==================== 🔐 PERMISSIONS ====================
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
        
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CAMERA_PERMISSION)
        } else {
            cameraManager.startBackgroundThread()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraManager.startBackgroundThread()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    // ==================== ⚙️ SETTINGS & UI ====================
    private fun showAdvancedSettings() {
        try {
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
            • Focus: ${(manualSettings["Focus"] as? Float ?: 0.5f) * 100}%
            • Exposure: ${manualSettings["Exposure"]}
            • Zoom: ${manualSettings["Zoom"]}x
            • Current LUT: $currentLUT
            • Flash Mode: $currentFlashMode
            
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
                    currentFlashMode = "AUTO"
                    updateFlashIcon()
                    Toast.makeText(this, "🔄 Settings Reset to Default", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Settings dialog error: ${e.message}", e)
            Toast.makeText(this, "❌ Settings unavailable", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        Toast.makeText(this, "🖼️ Gallery will be implemented in next version", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread { 
            tvStatus.text = message 
            Log.d(TAG, "Status: $message")
        }
    }
    
    // ==================== 🔄 ACTIVITY LIFECYCLE ====================
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 Activity Resumed")
        cameraManager.onResume()
    }
    
    override fun onPause() {
        Log.d(TAG, "⏸️ Activity Paused")
        cameraManager.onPause()
        super.onPause()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "🗑️ Activity Destroyed")
        mainHandler.removeCallbacksAndMessages(null)  // ✅ Handler cleanup
        cameraManager.onDestroy()
        super.onDestroy()
    }
}
