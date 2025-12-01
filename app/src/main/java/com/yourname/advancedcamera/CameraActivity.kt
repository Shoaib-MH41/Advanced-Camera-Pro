package com.yourname.advancedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.TextureView  // ✅ یہ شامل کریں
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
    
    // ==================== 📊 APP STATE ====================
    private var currentMode = 0
    private var currentLUT = "CINEMATIC"
    private var isRecording = false
    private var currentFlashMode = "AUTO"

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_camera_pro)

    Log.d(TAG, "🎬 Activity Created")

    initializeUI()           // ✔️ پہلے UI
    initializeManagers()     // ✔️ پھر Managers (اب textureView null نہیں ہوگا)
    initializeAdvancedFeatures()
    setupEventListeners()
    checkPermissions()
}
    
    
    private fun initializeManagers() {
        cameraManager = CameraManager(this, textureView)
        imageProcessor = ImageProcessor()
        fileSaver = FileSaver(this)
    }
    
    private fun initializeUI() {
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
        tvZoomValue = findViewById(R.id.tv_zoom_value)
        tvISOValue = findViewById(R.id.tv_iso_value)
        tvExposureValue = findViewById(R.id.tv_exposure_value)
        tvFocusValue = findViewById(R.id.tv_focus_value)
        tabModes = findViewById(R.id.tab_modes)
        focusIndicator = findViewById(R.id.focus_indicator)
        spinnerLUT = findViewById(R.id.spinner_lut)
        
        setupLUTSpinner()
        updateManualControls()
        updateFlashIcon()
    }
    
    private fun initializeAdvancedFeatures() {
        try {
            val availableFeatures = featureManager.getAvailableFeatures()
            updateStatus("🚀 DSLR Pro - ${availableFeatures.size} Features Active")
            Toast.makeText(this, "✅ ${availableFeatures.size} Advanced Features Loaded", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Advanced features initialization failed: ${e.message}")
            updateStatus("⚠️ Basic Mode - Advanced Features Failed")
        }
    }
    
    // ==================== 🎮 EVENT LISTENERS ====================
    private fun setupEventListeners() {
        textureView.surfaceTextureListener = cameraManager.getSurfaceTextureListener()
        
        // Button click listeners
        btnCapture.setOnClickListener { captureImage() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnSettings.setOnClickListener { showAdvancedSettings() }
        btnGallery.setOnClickListener { openGallery() }
        btnModeSwitch.setOnClickListener { toggleManualMode() }
        btnVideoRecord.setOnClickListener { toggleVideoRecording() }
        btnFlash.setOnClickListener { toggleFlashMode() }
        
        // SeekBar listeners
        seekZoom.setOnSeekBarChangeListener(createSeekBarListener("ZOOM") { progress ->
            val zoomLevel = 1.0f + (progress / 100.0f) * 49.0f
            featureManager.currentZoom = zoomLevel
            tvZoomValue.text = "${String.format("%.1f", zoomLevel)}x"
            cameraManager.applyZoom(zoomLevel)
        })
        
        seekISO.setOnSeekBarChangeListener(createSeekBarListener("ISO") { progress ->
            val iso = 50 + (progress * 63.5).toInt()
            featureManager.currentISO = iso
            tvISOValue.text = iso.toString()
            cameraManager.applyManualSettings()
        })
        
        seekExposure.setOnSeekBarChangeListener(createSeekBarListener("EXPOSURE") { progress ->
            val exposure = progress - 3
            featureManager.currentExposure = exposure
            tvExposureValue.text = if (exposure >= 0) "+$exposure" else exposure.toString()
            cameraManager.applyManualSettings()
        })
        
        seekFocus.setOnSeekBarChangeListener(createSeekBarListener("FOCUS") { progress ->
            val focus = progress / 100.0f
            featureManager.currentFocus = focus
            tvFocusValue.text = "${(focus * 100).toInt()}%"
            cameraManager.applyManualSettings()
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
        if (cameraManager.startVideoRecording()) {
            isRecording = true
            btnVideoRecord.setBackgroundResource(R.drawable.btn_video_recording)
            btnVideoRecord.setImageResource(R.drawable.ic_video_stop)
            recordingIndicator.visibility = View.VISIBLE
            Toast.makeText(this, "🎬 Video Recording Started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Failed to start video recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopVideoRecording() {
        val videoFile = cameraManager.stopVideoRecording()
        if (videoFile != null) {
            fileSaver.saveVideo(videoFile)
            Toast.makeText(this, "✅ Video Saved to Gallery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Failed to save video", Toast.LENGTH_SHORT).show()
        }
        
        isRecording = false
        btnVideoRecord.setBackgroundResource(R.drawable.btn_video_record)
        btnVideoRecord.setImageResource(R.drawable.ic_video_record)
        recordingIndicator.visibility = View.GONE
    }
    
    // ==================== 📷 CAMERA ACTIONS ====================
    private fun captureImage() {
        cameraManager.captureImage { bitmap ->
            val processedBitmap = imageProcessor.applyAdvancedProcessing(
                bitmap, currentMode, currentLUT, featureManager
            )
            fileSaver.saveImage(processedBitmap, currentLUT)
        }
    }
    
    private fun switchCamera() {
        cameraManager.switchCamera()
    }
    
    private fun setFocusArea(x: Float, y: Float) {
        cameraManager.setFocusArea(x, y)
        
        // Show focus indicator
        focusIndicator.x = x - focusIndicator.width / 2
        focusIndicator.y = y - focusIndicator.height / 2
        focusIndicator.visibility = View.VISIBLE
        
        // Hide focus indicator after delay
        Handler().postDelayed({
            runOnUiThread {
                focusIndicator.visibility = View.INVISIBLE
            }
        }, 2000)
    }
    
    // ==================== ⚙️ UI CONTROLS ====================
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
                if (fromUser) onProgressChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
    }
    
    private fun updateManualControls() {
        val settings = featureManager.getManualSettings()
        
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
    
    private fun toggleManualMode() {
        val isManual = currentMode == 1
        controlPanel.visibility = if (isManual) View.VISIBLE else View.GONE
        currentMode = if (isManual) 0 else 1
        
        Toast.makeText(this, 
            if (isManual) "🔘 Auto Mode" else "⚙️ Pro Mode", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun applyModeSettings(mode: Int) {
        when (mode) {
            1 -> {
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

    companion object {
        private const val TAG = "DSLRCameraPro"
        private const val REQUEST_CAMERA_PERMISSION = 200
    }
}
