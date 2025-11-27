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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourname.advancedcamera.features.FeatureManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.ArrayList

class CameraActivity : AppCompatActivity() {
    
    private lateinit var textureView: TextureView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var controlPanel: LinearLayout
    private lateinit var seekZoom: SeekBar
    
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var sensorOrientation: Int? = null
    
    private val featureManager = FeatureManager.getInstance()
    private var currentMode = 0 // 0: Auto, 1: Pro, 2: Night, 3: Portrait
    
    private val ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }
    
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
    
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
            updateStatus("DSLR Camera Ready - ${featureManager.getAvailableFeatures().size} Features Loaded")
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
            updateStatus("Camera Disconnected")
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
            updateStatus("Camera Error: $error")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_pro)
        
        initializeUI()
        checkPermissions()
    }
    
    private fun initializeUI() {
        textureView = findViewById(R.id.texture_view)
        btnCapture = findViewById(R.id.btn_capture)
        btnSwitchCamera = findViewById(R.id.btn_switch_camera)
        btnSettings = findViewById(R.id.btn_settings)
        tvStatus = findViewById(R.id.tv_status)
        controlPanel = findViewById(R.id.control_panel)
        seekZoom = findViewById(R.id.seek_zoom)
        
        setupEventListeners()
    }
    
    private fun setupEventListeners() {
        textureView.surfaceTextureListener = surfaceTextureListener
        
        btnCapture.setOnClickListener { captureImage() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnSettings.setOnClickListener { showAdvancedSettings() }
        
        seekZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) applyZoom(progress.toFloat() / 100)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
    
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
    
    @SuppressLint("MissingPermission")
    private fun setupCamera(width: Int, height: Int) {
        try {
            cameraManager = getSystemService(CameraManager::class.java)
            val cameraList = cameraManager!!.cameraIdList
            
            if (cameraList.isEmpty()) {
                updateStatus("No camera found")
                return
            }
            
            // Find back camera
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
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            previewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            val previewRequest = previewRequestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(previewRequest, null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start preview: ${e.message}")
                        }
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        updateStatus("Failed to configure camera")
                    }
                }, backgroundHandler)
                
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
            // Fallback
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
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
                
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length)
                
                // Apply advanced processing based on current mode
                bitmap = applyAdvancedProcessing(bitmap)
                
                saveImage(bitmap)
                
                runOnUiThread {
                    Toast.makeText(this, "Photo saved with DSLR processing!", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            image?.close()
        }
    }
    
    private fun applyAdvancedProcessing(originalBitmap: Bitmap): Bitmap {
        var processedBitmap = originalBitmap
        
        try {
            when (currentMode) {
                2 -> { // Night Mode
                    if (featureManager.isNightVisionEnabled) {
                        val frames = ArrayList<Bitmap>()
                        frames.add(originalBitmap)
                        processedBitmap = featureManager.processNightVision(frames)
                        Log.d(TAG, "Night vision processing applied")
                    }
                }
                3 -> { // Portrait Mode
                    if (featureManager.isColorLUTsEnabled) {
                        processedBitmap = featureManager.applyColorLUT(originalBitmap, "CINEMATIC")
                        Log.d(TAG, "Cinematic LUT applied")
                    }
                }
                1 -> { // Pro Mode
                    if (featureManager.isColorLUTsEnabled) {
                        processedBitmap = featureManager.applyColorLUT(originalBitmap, "VINTAGE")
                        Log.d(TAG, "Vintage LUT applied")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Advanced processing failed: ${e.message}")
        }
        
        return processedBitmap
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
        var featureInfo = "Basic Capture"
        
        when {
            currentMode == 2 && featureManager.isNightVisionEnabled -> featureInfo = "Night Vision Processing"
            currentMode == 3 && featureManager.isColorLUTsEnabled -> featureInfo = "Cinematic LUT Applied"
            currentMode == 1 && featureManager.isColorLUTsEnabled -> featureInfo = "Vintage LUT Applied"
        }
        
        Toast.makeText(this, featureInfo, Toast.LENGTH_SHORT).show()
    }
    
    private fun switchCamera() {
        cameraDevice?.close()
        captureSession?.close()
        
        cameraId = if (cameraId == "0") "1" else "0" // Simple switch logic
        
        openCamera()
    }
    
    private fun applyZoom(zoomLevel: Float) {
        try {
            previewRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, null)
            captureSession!!.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Zoom failed: ${e.message}")
        }
    }
    
    private fun showAdvancedSettings() {
        val features = featureManager.getAvailableFeatures()
        val message = "Active DSLR Features:\n\n• ${features.joinToString("\n• ")}"
        
        android.app.AlertDialog.Builder(this)
            .setTitle("🎯 Advanced Camera Features")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset Settings") { _, _ ->
                featureManager.resetToDefaults()
                Toast.makeText(this, "Settings Reset to Default", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread { tvStatus.text = message }
    }
    
    private fun getOrientation(): Int {
        val rotation = windowManager.defaultDisplay.rotation
        return (ORIENTATIONS.get(rotation) + sensorOrientation!! + 270) % 360
    }
    
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        val scaleX = viewWidth.toFloat() / previewSize!!.width
        val scaleY = viewHeight.toFloat() / previewSize!!.height
        
        val rotation = windowManager.defaultDisplay.rotation
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            matrix.setScale(scaleX, scaleY)
        } else {
            matrix.setScale(scaleY, scaleX)
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
            choices[0]
        }
    }
    
    private fun saveImage(bitmap: Bitmap) {
        val filename = "DSLR_${System.currentTimeMillis()}.jpg"
        val file = File(getExternalFilesDir(null), filename)
        
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                Log.d(TAG, "DSLR Image saved: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image: ${e.message}")
        }
    }
    
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
    
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
    
    companion object {
        private const val TAG = "DSLRCamera"
        private const val REQUEST_CAMERA_PERMISSION = 200
    }
}
