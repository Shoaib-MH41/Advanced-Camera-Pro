package com.yourname.advancedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourname.advancedcamera.managers.CameraManager
import com.yourname.advancedcamera.features.LUTManager
import com.yourname.advancedcamera.features.FlashManager
import com.yourname.advancedcamera.features.FocusManager
import com.yourname.advancedcamera.features.ZoomManager
import com.yourname.advancedcamera.utils.CameraUIController

class CameraActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager

    // Features
    private lateinit var lutManager: LUTManager
    private lateinit var flashManager: FlashManager
    private lateinit var focusManager: FocusManager
    private lateinit var zoomManager: ZoomManager
    private lateinit var uiController: CameraUIController

    private val TAG = "📸CameraActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_pro)

        Log.d(TAG, "Activity Created")

        bindUI()
        initFeatures()
        checkPermissions()
    }

    /** ------------------------------
     *  UI BINDING
     *  ------------------------------ */
    private fun bindUI() {
        textureView = findViewById(R.id.textureView)
        uiController = CameraUIController(this)

        findViewById<ImageButton>(R.id.btn_shutter).setOnClickListener {
            cameraManager.capturePhoto()
        }

        findViewById<ImageButton>(R.id.btn_switch_cam).setOnClickListener {
            cameraManager.switchCamera()
        }

        findViewById<ImageButton>(R.id.btn_flash).setOnClickListener {
            val mode = flashManager.toggleFlash()
            cameraManager.setFlash(mode)
            uiController.updateFlashIcon(mode)
        }

        findViewById<ImageButton>(R.id.btn_lut).setOnClickListener {
            val lut = lutManager.nextLUT()
            cameraManager.applyLUT(lut)
            uiController.showLUT(lut)
        }

        // Tap to Focus
        textureView.setOnTouchListener { v, event ->
            focusManager.handleFocus(event, textureView)
            cameraManager.focusAt(event.x, event.y)
            true
        }
    }

    /** ------------------------------
     *  INITIALIZE FEATURES
     *  ------------------------------ */
    private fun initFeatures() {
        lutManager = LUTManager(this)
        flashManager = FlashManager()
        focusManager = FocusManager(this)
        zoomManager = ZoomManager(this)
    }

    /** ------------------------------
     *  PERMISSION
     *  ------------------------------ */
    private fun checkPermissions() {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            finish()
        }
    }

    /** ------------------------------
     *  START CAMERA WHEN READY
     *  ------------------------------ */
    private fun initCamera() {
        Log.d(TAG, "Camera init… waiting texture ready")

        if (textureView.isAvailable) {
            startCamera(textureView.surfaceTexture!!)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    startCamera(st)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    private fun startCamera(surfaceTexture: SurfaceTexture) {
        Log.d(TAG, "📸 Starting Camera")

        cameraManager = CameraManager(this, textureView)
        cameraManager.initialize(surfaceTexture)
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) {
            startCamera(textureView.surfaceTexture!!)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::cameraManager.isInitialized) {
            cameraManager.stopCamera()
        }
    }
}
