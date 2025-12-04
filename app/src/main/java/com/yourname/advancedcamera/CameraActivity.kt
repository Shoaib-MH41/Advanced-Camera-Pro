package com.yourname.advancedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourname.advancedcamera.managers.CameraController

class CameraActivity : AppCompatActivity() {
    
    private lateinit var cameraController: CameraController
    private var isCameraInitialized = false
    
    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CAMERA_PERMISSION = 200
    }
    
    // ==================== 🎬 ACTIVITY LIFECYCLE ====================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        
        Log.d(TAG, "🎬 Activity Created")
        
        try {
            // Initialize UI Controller
            CameraUIController.initialize(this)
            
            // Initialize Camera Controller
            initializeCameraController()
            
            // Setup event listeners via UI Controller
            CameraUIController.setupEventListeners(this, cameraController)
            
            // Check permissions
            checkPermissions()
            
            Log.d(TAG, "✅ onCreate completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
            finish()
        }
    }
    
    private fun initializeCameraController() {
        val textureView = CameraUIController.getTextureView()
        cameraController = CameraController(this, textureView)
        
        cameraController.setErrorCallback { error ->
            runOnUiThread {
                CameraUIController.showToast(this, "Camera Error: $error")
                CameraUIController.updateStatus("⚠️ Camera Error")
            }
        }
        
        // Set surface texture listener
        textureView.surfaceTextureListener = cameraController.getSurfaceTextureListener()
        isCameraInitialized = true
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
            onPermissionsGranted()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onPermissionsGranted()
            } else {
                CameraUIController.showToast(this, "Camera permission required")
                finish()
            }
        }
    }
    
    private fun onPermissionsGranted() {
        cameraController.onResume()
        CameraUIController.updateStatus("✅ Camera Ready")
    }
    
    // ==================== 🔄 ACTIVITY LIFECYCLE ====================
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 Activity Resumed")
        if (isCameraInitialized) {
            cameraController.onResume()
        }
    }
    
    override fun onPause() {
        Log.d(TAG, "⏸️ Activity Paused")
        if (isCameraInitialized) {
            cameraController.onPause()
        }
        super.onPause()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "🗑️ Activity Destroyed")
        if (isCameraInitialized) {
            cameraController.onDestroy()
        }
        super.onDestroy()
    }
}
