package com.yourname.advancedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
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
    
    // UI Components
    private lateinit var textureView: TextureView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnModeSwitch: ImageButton
    private lateinit var btnVideoRecord: ImageButton  // ✅ نیا video record بٹن
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
    
    // Managers
    private lateinit var cameraManager: CameraManager
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var fileSaver: FileSaver
    private val featureManager = FeatureManager.getInstance()
    
    // State
    private var currentMode = 0
    private var currentLUT = "CINEMATIC"
    private var isRecording = false  // ✅ Video recording state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_pro)
        
        initializeManagers()
        initializeUI()
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
        btnVideoRecord = findViewById(R.id.btn_video_record)  // ✅ نیا بٹن
        
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
    }

    private fun setupEventListeners() {
        // Button clicks
        btnCapture.setOnClickListener { 
            cameraManager.captureImage { bitmap ->
                val processedBitmap = imageProcessor.applyAdvancedProcessing(
                    bitmap, currentMode, currentLUT, featureManager
                )
                fileSaver.saveImage(processedBitmap, currentLUT)
            }
        }
        
        btnSwitchCamera.setOnClickListener { 
            cameraManager.switchCamera() 
        }
        
        btnVideoRecord.setOnClickListener {  // ✅ نیا video record event
            if (!isRecording) {
                startVideoRecording()
            } else {
                stopVideoRecording()
            }
        }
        
        btnSettings.setOnClickListener { showAdvancedSettings() }
        btnGallery.setOnClickListener { openGallery() }
        btnModeSwitch.setOnClickListener { toggleManualMode() }

        // Manual controls
        seekZoom.setOnSeekBarChangeListener(createSeekBarListener("ZOOM") { progress ->
            val zoomLevel = 1.0f + (progress / 100.0f) * 49.0f
            featureManager.currentZoom = zoomLevel
            tvZoomValue.text = "${String.format("%.1f", zoomLevel)}x"
            cameraManager.applyZoom(zoomLevel)
        })
        
        // ... باقی seekbar listeners
    }

    // ✅ VIDEO RECORDING FUNCTIONS
    private fun startVideoRecording() {
        if (cameraManager.startVideoRecording()) {
            isRecording = true
            btnVideoRecord.setBackgroundColor(Color.RED)
            recordingIndicator.visibility = View.VISIBLE
            Toast.makeText(this, "🎬 Video Recording Started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVideoRecording() {
        val videoFile = cameraManager.stopVideoRecording()
        if (videoFile != null) {
            fileSaver.saveVideo(videoFile)
            Toast.makeText(this, "✅ Video Saved to Gallery", Toast.LENGTH_SHORT).show()
        }
        isRecording = false
        btnVideoRecord.setBackgroundColor(Color.TRANSPARENT)
        recordingIndicator.visibility = View.GONE
    }

    // ... باقی UI related functions
    private fun showAdvancedSettings() { /* UI only */ }
    private fun openGallery() { /* UI only */ }
    private fun toggleManualMode() { /* UI only */ }
    private fun updateManualControls() { /* UI only */ }
    private fun setupLUTSpinner() { /* UI only */ }
    private fun createSeekBarListener(controlName: String, onProgressChanged: (Int) -> Unit) { /* UI only */ }

    // Permissions
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO  // ✅ Video recording permission
        )
        
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 200)
        } else {
            cameraManager.startBackgroundThread()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraManager.startBackgroundThread()
        }
    }

    override fun onResume() {
        super.onResume()
        cameraManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        cameraManager.onPause()
    }
}
