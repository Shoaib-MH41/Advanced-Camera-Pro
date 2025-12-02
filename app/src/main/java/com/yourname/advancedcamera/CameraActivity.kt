// CameraActivity.kt
package com.eagleeye.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.eagleeye.camera.controllers.ProCameraController
import com.eagleeye.camera.features.FeatureManager
import com.google.android.material.tabs.TabLayout

class CameraActivity : AppCompatActivity() {

    // ==================== UI Components ====================
    private lateinit var textureView: TextureView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnVideo: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tabModes: TabLayout
    private lateinit var controlPanel: LinearLayout
    private lateinit var seekZoom: SeekBar
    private lateinit var seekISO: SeekBar
    private lateinit var seekExposure: SeekBar
    private lateinit var seekFocus: SeekBar
    private lateinit var tvZoom: TextView
    private lateinit var tvISO: TextView
    private lateinit var tvExposure: TextView
    private lateinit var tvFocus: TextView
    private lateinit var spinnerLUT: Spinner
    private lateinit var focusRing: ImageView
    private lateinit var recordingDot: View

    // ==================== Controllers ====================
    private lateinit var cameraController: ProCameraController
    private val featureManager = FeatureManager.getInstance()

    private var currentMode = 0
    private var currentLUT = "Cinematic"
    private var isRecording = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_pro)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initUI()
        initCameraController()
        setupListeners()
        requestPermissions()
    }

    private fun initUI() {
        textureView = findViewById(R.id.texture_view)
        btnCapture = findViewById(R.id.btn_capture)
        btnSwitchCamera = findViewById(R.id.btn_switch_camera)
        btnFlash = findViewById(R.id.btn_flash)
        btnVideo = findViewById(R.id.btn_video_record)
        btnSettings = findViewById(R.id.btn_settings)
        tabModes = findViewById(R.id.tab_modes)
        controlPanel = findViewById(R.id.control_panel)
        seekZoom = findViewById(R.id.seek_zoom)
        seekISO = findViewById(R.id.seek_iso)
        seekExposure = findViewById(R.id.seek_exposure)
        seekFocus = findViewById(R.id.seek_focus)
        tvZoom = findViewById(R.id.tv_zoom_value)
        tvISO = findViewById(R.id.tv_iso_value)
        tvExposure = findViewById(R.id.tv_exposure_value)
        tvFocus = findViewById(R.id.tv_focus_value)
        spinnerLUT = findViewById(R.id.spinner_lut)
        focusRing = findViewById(R.id.focus_indicator)
        recordingDot = findViewById(R.id.recording_indicator)

        setupLUTSpinner()
        setupTabModes()
    }

    private fun initCameraController() {
        cameraController = ProCameraController(this, textureView)
        cameraController.onCameraReady = { updateStatus("DSLR Pro Ready • ${featureManager.getAvailableFeatures().size} Features") }
        cameraController.onCameraError = { Toast.makeText(this, "Camera Error: $it", Toast.LENGTH_LONG).show() }
    }

    private fun setupListeners() {
        btnCapture.setOnClickListener { capturePhoto() }
        btnSwitchCamera.setOnClickListener { cameraController.switchCamera() }
        btnFlash.setOnClickListener { toggleFlash() }
        btnVideo.setOnClickListener { toggleRecording() }
        btnSettings.setOnClickListener { showAdvancedSettings() }

        textureView.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) showFocusRing(e.x, e.y)
            cameraController.setFocusPoint(e.x, e.y)
            true
        }

        tabModes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentMode = tab.position
                updateModeUI()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupLUTSpinner() {
        val luts = featureManager.getLUTTypes()
        spinnerLUT.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, luts)
        spinnerLUT.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentLUT = luts[pos]
                cameraController.applyLUT(currentLUT)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupTabModes() {
        tabModes.addTab(tabModes.newTab().setText("Auto"))
        tabModes.addTab(tabModes.newTab().setText("Pro"))
        tabModes.addTab(tabModes.newTab().setText("Night"))
        tabModes.addTab(tabModes.newTab().setText("Portrait"))
        tabModes.addTab(tabModes.newTab().setText("Video"))
    }

    private fun updateModeUI() {
        controlPanel.visibility = if (currentMode == 1) View.VISIBLE else View.GONE
        when (currentMode) {
            2 -> cameraController.enableNightMode()
            3 -> cameraController.enablePortraitMode()
            else -> cameraController.disableSpecialModes()
        }
    }

    private fun capturePhoto() {
        cameraController.capturePhoto { bitmap ->
            val processed = featureManager.applyProcessing(bitmap, currentMode, currentLUT)
            featureManager.saveImage(processed)
            Toast.makeText(this, "Photo Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlash() {
        val modes = listOf("AUTO", "ON", "OFF", "TORCH")
        val next = modes[(modes.indexOf(cameraController.currentFlash) + 1) % 4]
        cameraController.setFlash(next)
        btnFlash.setImageResource(when (next) {
            "AUTO" -> R.drawable.ic_flash_auto
            "ON" -> R.drawable.ic_flash_on
            "OFF" -> R.drawable.ic_flash_off
            else -> R.drawable.ic_flash_torch
        })
    }

    private fun toggleRecording() {
        if (isRecording) cameraController.stopRecording() else cameraController.startRecording()
        isRecording = !isRecording
        recordingDot.visibility = if (isRecording) View.VISIBLE else View.GONE
        btnVideo.setImageResource(if (isRecording) R.drawable.ic_stop else R.drawable.ic_video)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.x = x - focusRing.width / 2
        focusRing.y = y - focusRing.height / 2
        focusRing.visibility = View.VISIBLE
        handler.postDelayed({ focusRing.visibility = View.INVISIBLE }, 1500)
    }

    private fun showAdvancedSettings() {
        // تمہارا وہی خوبصورت ڈائیلاگ یہاں آئے گا
        featureManager.showSettingsDialog(this)
    }

    private fun updateStatus(msg: String) = runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg }

    // Permissions
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) cameraController.start()
        else finish()
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            cameraController.start()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    override fun onResume() {
        super.onResume()
        cameraController.start()
    }

    override fun onPause() {
        cameraController.stop()
        super.onPause()
    }
}
