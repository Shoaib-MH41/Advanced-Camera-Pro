package com.yourname.advancedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
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

    // ==================== 🚀 MANAGERS (nullable-safe) ====================
    private var cameraManager: CameraManager? = null
    private var imageProcessor: ImageProcessor? = null
    private var fileSaver: FileSaver? = null
    private val featureManager = FeatureManager.getInstance()

    // ==================== 📊 APP STATE ====================
    private var currentMode = 0
    private var currentLUT = "CINEMATIC"
    private var isRecording = false
    private var currentFlashMode = "AUTO"

    companion object {
        private const val TAG = "DSLRCameraPro"
        private const val REQUEST_CAMERA_PERMISSION = 200
    }

    // ---------------------- lifecycle ----------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_pro)

        Log.d(TAG, "🎬 Activity Created")

        // 1. UI first (textureView must exist before CameraManager uses it)
        initializeUI()

        // 2. Then managers (cameraManager needs textureView)
        cameraManager = CameraManager(this, textureView)
        imageProcessor = ImageProcessor()
        fileSaver = FileSaver(this)

        // 3. Event listeners (safe because UI + managers exist)
        setupEventListeners()

        // 4. Load optional features (non-blocking)
        initializeAdvancedFeatures()

        // 5. Permissions -> start camera background if allowed
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 Activity Resumed")
        cameraManager?.onResume()
    }

    override fun onPause() {
        Log.d(TAG, "⏸️ Activity Paused")
        cameraManager?.onPause()
        super.onPause()
    }

    // ---------------------- UI init ----------------------
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

    // ---------------------- Features ----------------------
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

    // ---------------------- Events ----------------------
    private fun setupEventListeners() {
        // Surface listener -> camera manager
        textureView.surfaceTextureListener = cameraManager?.getSurfaceTextureListener()

        // Buttons
        btnCapture.setOnClickListener { captureImage() }
        btnSwitchCamera.setOnClickListener { cameraManager?.switchCamera() }
        btnSettings.setOnClickListener { showAdvancedSettings() }
        btnGallery.setOnClickListener { openGallery() }
        btnModeSwitch.setOnClickListener { toggleManualMode() }
        btnVideoRecord.setOnClickListener { toggleVideoRecording() }
        btnFlash.setOnClickListener { toggleFlashMode() }

        // SeekBars
        seekZoom.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val zoomLevel = 1.0f + (progress / 100.0f) * 49.0f
            featureManager.currentZoom = zoomLevel
            tvZoomValue.text = "${String.format("%.1f", zoomLevel)}x"
            cameraManager?.applyZoom(zoomLevel)
        })

        seekISO.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val iso = 50 + (progress * 63.5).toInt()
            featureManager.currentISO = iso
            tvISOValue.text = iso.toString()
            cameraManager?.applyManualSettings()
        })

        seekExposure.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val exposure = progress - 3
            featureManager.currentExposure = exposure
            tvExposureValue.text = if (exposure >= 0) "+$exposure" else exposure.toString()
            cameraManager?.applyManualSettings()
        })

        seekFocus.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val focus = progress / 100.0f
            featureManager.currentFocus = focus
            tvFocusValue.text = "${(focus * 100).toInt()}%"
            cameraManager?.applyManualSettings()
        })

        // Tabs
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
                setFocusArea(event.x, event.y)
            }
            true
        }
    }

    // ---------------------- Flash / Recording / Capture ----------------------
    private fun toggleFlashMode() {
        currentFlashMode = when (currentFlashMode) {
            "AUTO" -> "ON"
            "ON" -> "OFF"
            "OFF" -> "TORCH"
            "TORCH" -> "AUTO"
            else -> "AUTO"
        }
        updateFlashIcon()
        cameraManager?.applyFlashMode(currentFlashMode)
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

    private fun toggleVideoRecording() {
        if (!isRecording) {
            startVideoRecording()
        } else {
            stopVideoRecording()
        }
    }

    private fun startVideoRecording() {
        val started = cameraManager?.startVideoRecording() ?: false
        if (started) {
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
        val videoFile = cameraManager?.stopVideoRecording()
        if (videoFile != null) {
            fileSaver?.saveVideo(videoFile)
            Toast.makeText(this, "✅ Video Saved to Gallery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Failed to save video", Toast.LENGTH_SHORT).show()
        }
        isRecording = false
        btnVideoRecord.setBackgroundResource(R.drawable.btn_video_record)
        btnVideoRecord.setImageResource(R.drawable.ic_video_record)
        recordingIndicator.visibility = View.GONE
    }

    private fun captureImage() {
        cameraManager?.captureImage { bitmap ->
            try {
                val processed = imageProcessor?.applyAdvancedProcessing(bitmap, currentMode, currentLUT, featureManager)
                    ?: bitmap
                fileSaver?.saveImage(processed, currentLUT)
                runOnUiThread {
                    Toast.makeText(this, "📸 Photo processed & saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureImage failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "❌ Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------- Focus / UI helpers ----------------------
    private fun setFocusArea(x: Float, y: Float) {
        cameraManager?.setFocusArea(x, y)
        // show indicator
        focusIndicator.x = x - focusIndicator.width / 2
        focusIndicator.y = y - focusIndicator.height / 2
        focusIndicator.visibility = View.VISIBLE
        Handler().postDelayed({ runOnUiThread { focusIndicator.visibility = View.INVISIBLE } }, 2000)
    }

    private fun setupLUTSpinner() {
        val lutTypes = featureManager.getLUTTypes()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, lutTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLUT.adapter = adapter
        spinnerLUT.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLUT = lutTypes[position]
                Toast.makeText(this@CameraActivity, "🎨 $currentLUT selected", Toast.LENGTH_SHORT).show()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun createSeekBarListener(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(progress)
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
        Toast.makeText(this, if (isManual) "🔘 Auto Mode" else "⚙️ Pro Mode", Toast.LENGTH_SHORT).show()
    }

    private fun applyModeSettings(mode: Int) {
        if (mode == 1) {
            controlPanel.visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.lut_panel).visibility = View.VISIBLE
        } else {
            controlPanel.visibility = View.GONE
            findViewById<LinearLayout>(R.id.lut_panel).visibility = View.GONE
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
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
    }

    // ---------------------- Permissions ----------------------
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CAMERA_PERMISSION)
        } else {
            // start camera background thread / camera manager
            cameraManager?.startBackgroundThread()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                cameraManager?.startBackgroundThread()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // ---------------------- UI / Settings helpers ----------------------
    private fun showAdvancedSettings() {
        val features = featureManager.getAvailableFeatures()
        val stats = featureManager.getFeatureStats()
        val manual = featureManager.getManualSettings()
        val lutTypes = featureManager.getLUTTypes()
        val message = """
            🚀 DSLR Camera Pro
            • Total: ${stats["TotalFeatures"]}
            • Active: ${stats["ActiveFeatures"]}
            • ISO: ${manual["ISO"]}
            • Exposure: ${manual["Exposure"]}
            • LUTs: ${lutTypes.joinToString(", ")}
        """.trimIndent()
        android.app.AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openGallery() {
        // Placeholder: implement gallery intent when ready
        Toast.makeText(this, "Gallery - coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus(message: String) {
        runOnUiThread { tvStatus.text = message }
    }
}
