package com.yourname.advancedcamera.managers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraManager(private val context: Context, private val textureView: TextureView) {

    // Camera components
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSize: Size? = null
    private var cameraId: String? = null

    // Background thread
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Image reader for still capture
    private var imageReader: ImageReader? = null

    // Media recorder for video
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null

    // State
    private var isRecording = false
    private var currentFlashMode = "AUTO" // AUTO / ON / OFF / TORCH
    private val appActivity: Activity? = (context as? Activity)

    // Public callback for captured image bitmaps
    private var onImageCaptured: ((bitmap: Bitmap) -> Unit)? = null

    // CameraManager service (lazy)
    private val sysCameraManager: android.hardware.camera2.CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    }

    companion object {
        private const val TAG = "CameraManager"
    }

    init {
        // Ensure textureView has a listener attached by Activity:
        // textureView.surfaceTextureListener = cameraManager.getSurfaceTextureListener()
    }

    // ------------------------- SurfaceTexture Listener -------------------------
    fun getSurfaceTextureListener(): TextureView.SurfaceTextureListener {
        return object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "SurfaceTexture available: $width x $height")
                startBackgroundThread()
                setupCamera(width, height)
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                configureTransform(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                closeCamera()
                stopBackgroundThread()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }
    }

    // ------------------------- Camera Lifecycle -------------------------
    fun onResume() {
        startBackgroundThread()
        if (textureView.isAvailable) {
            setupCamera(textureView.width, textureView.height)
            openCamera()
        } else {
            textureView.surfaceTextureListener = getSurfaceTextureListener()
        }
    }

    fun onPause() {
        closeCamera()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
            Log.d(TAG, "Background thread started")
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
            Log.d(TAG, "Background thread stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, "stopBackgroundThread interrupted: ${e.message}")
        }
    }

    // ------------------------- Setup / Open / Close -------------------------
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            if (cameraId == null) {
                Log.e(TAG, "openCamera: cameraId is null")
                return
            }
            Log.d(TAG, "Opening camera: $cameraId")
            sysCameraManager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera failed: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "openCamera security: ${e.message}")
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            mediaRecorder?.release()
            mediaRecorder = null
            Log.d(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "closeCamera: ${e.message}")
        }
    }

    // ------------------------- State Callbacks -------------------------
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            cameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error $error")
            camera.close()
            cameraDevice = null
        }
    }

    // ------------------------- Camera Setup -------------------------
    private fun setupCamera(viewWidth: Int, viewHeight: Int) {
        try {
            val cameraList = sysCameraManager.cameraIdList
            if (cameraList.isEmpty()) {
                Log.e(TAG, "No cameras available")
                return
            }

            // Prefer back camera
            for (id in cameraList) {
                val chars = sysCameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
            if (cameraId == null) cameraId = cameraList[0]

            val characteristics = sysCameraManager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return

            // choose preview size
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            previewSize = chooseOptimalSize(sizes, viewWidth, viewHeight)
            Log.d(TAG, "Chosen preview size: ${previewSize?.width}x${previewSize?.height}")

            // prepare imageReader for still capture (JPEG)
            imageReader?.close()
            imageReader = ImageReader.newInstance(previewSize!!.width, previewSize!!.height, android.graphics.ImageFormat.JPEG, 2)
            imageReader!!.setOnImageAvailableListener(imageReaderListener, backgroundHandler)

            configureTransform(viewWidth, viewHeight)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "setupCamera failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "setupCamera unknown: ${e.message}")
        }
    }

    private val imageReaderListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            onImageCaptured?.invoke(bmp)
        } catch (e: Exception) {
            Log.e(TAG, "imageReaderListener: ${e.message}")
        } finally {
            image.close()
        }
    }

    // ------------------------- Preview Session -------------------------
    private fun createPreviewSession() {
        try {
            val texture = textureView.surfaceTexture ?: run {
                Log.e(TAG, "Texture is null")
                return
            }

            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            val previewSurface = Surface(texture)

            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(previewSurface)

            // we don't attach mediaRecorder or imageReader here permanently — sessions are rebuilt per mode
            cameraDevice!!.createCaptureSession(
                listOf(previewSurface, imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            applyFlashToBuilder(previewRequestBuilder!!)
                            val request = previewRequestBuilder!!.build()
                            captureSession?.setRepeatingRequest(request, null, backgroundHandler)
                            Log.d(TAG, "Preview session started")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "start preview failed: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Preview session configure failed")
                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createPreviewSession: ${e.message}")
        }
    }

    // ------------------------- Still Capture -------------------------
    /**
     * Public method used by Activity:
     * cameraManager.captureImage { bitmap -> /* save via FileSaver */ }
     */
    fun captureImage(callback: (Bitmap) -> Unit) {
        onImageCaptured = callback
        captureStillPicture()
    }

    private fun captureStillPicture() {
        try {
            val camera = cameraDevice ?: run {
                Log.e(TAG, "captureStillPicture: cameraDevice null")
                return
            }
            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)

            // Use same AF/AE settings as preview
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            applyFlashToBuilder(captureBuilder)

            // Orientation
            val orientation = getJpegOrientation()
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation)

            // Stop repeating and capture
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            captureSession?.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    try {
                        // resume preview
                        previewRequestBuilder?.let {
                            captureSession?.setRepeatingRequest(it.build(), null, backgroundHandler)
                        }
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "resume preview failed: ${e.message}")
                    }
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "captureStillPicture failed: ${e.message}")
        }
    }

    private fun getJpegOrientation(): Int {
        val sensorOrientation = sysCameraManager.getCameraCharacteristics(cameraId!!).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val deviceRotation = appActivity?.windowManager?.defaultDisplay?.rotation ?: 0
        val rotationComp = when (deviceRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        // convert to degrees and compute orientation
        val jpegOrientation = (sensorOrientation + rotationComp + 360) % 360
        return jpegOrientation
    }

    // ------------------------- Video Recording -------------------------
    fun startVideoRecording(): Boolean {
        try {
            if (isRecording) return false
            prepareMediaRecorder()
            // Build a session that includes preview + mediaRecorder surface
            val texture = textureView.surfaceTexture ?: return false
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder!!.surface

            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            previewRequestBuilder!!.addTarget(previewSurface)
            previewRequestBuilder!!.addTarget(recorderSurface)
            applyFlashToBuilder(previewRequestBuilder!!)

            cameraDevice!!.createCaptureSession(listOf(previewSurface, recorderSurface, imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
                            mediaRecorder?.start()
                            isRecording = true
                            Log.d(TAG, "Video recording started")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "startVideoRecording session error: ${e.message}")
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, "mediaRecorder start error: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Video session configure failed")
                    }
                }, backgroundHandler)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "startVideoRecording failed: ${e.message}")
            return false
        }
    }

    fun stopVideoRecording(): File? {
        return try {
            if (!isRecording) return null
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isRecording = false
            Log.d(TAG, "Video recording stopped")
            // after stopping, recreate preview session
            createPreviewSession()
            videoFile
        } catch (e: Exception) {
            Log.e(TAG, "stopVideoRecording failed: ${e.message}")
            null
        }
    }

    private fun prepareMediaRecorder() {
        if (mediaRecorder == null) mediaRecorder = MediaRecorder()
        mediaRecorder?.apply {
            reset()
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(30)
            setVideoSize(previewSize!!.width, previewSize!!.height)

            videoFile = File(context.getExternalFilesDir(null), "VID_${System.currentTimeMillis()}.mp4")
            setOutputFile(videoFile!!.absolutePath)
            try {
                prepare()
            } catch (e: Exception) {
                Log.e(TAG, "mediaRecorder prepare failed: ${e.message}")
            }
        }
    }

    // ------------------------- Focus / Metering / Zoom -------------------------
    fun setFocusArea(x: Float, y: Float) {
        try {
            val chars = sysCameraManager.getCameraCharacteristics(cameraId!!)
            val maxRegions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            if (maxRegions <= 0) {
                Log.d(TAG, "Focus regions not supported")
                return
            }

            val area = calculateTapArea(x, y, 200f)
            val metering = MeteringRectangle(area, MeteringRectangle.METERCURSOR_DEFAULT)

            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val tex = textureView.surfaceTexture ?: return
            captureBuilder.addTarget(Surface(tex))
            captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

            captureSession?.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    // nothing
                }
            }, backgroundHandler)
            Log.d(TAG, "Focus requested at $x, $y")
        } catch (e: Exception) {
            Log.e(TAG, "setFocusArea failed: ${e.message}")
        }
    }

    private fun calculateTapArea(x: Float, y: Float, areaSize: Float): Rect {
        val width = textureView.width
        val height = textureView.height
        val left = ((x / width) * 2000 - 1000 - areaSize / 2).toInt().coerceIn(-1000, 1000)
        val top = ((y / height) * 2000 - 1000 - areaSize / 2).toInt().coerceIn(-1000, 1000)
        val right = (left + areaSize.toInt()).coerceIn(-1000, 1000)
        val bottom = (top + areaSize.toInt()).coerceIn(-1000, 1000)
        return Rect(left, top, right, bottom)
    }

    fun applyZoom(zoomFactor: Float) {
        try {
            val cameraChar = sysCameraManager.getCameraCharacteristics(cameraId!!)
            val activeRect = cameraChar.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
            val minW = (activeRect.width() / zoomFactor).toInt()
            val minH = (activeRect.height() / zoomFactor).toInt()
            val left = (activeRect.centerX() - minW / 2).coerceAtLeast(activeRect.left)
            val top = (activeRect.centerY() - minH / 2).coerceAtLeast(activeRect.top)
            val zoomRect = Rect(left, top, left + minW, top + minH)
            previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
            Log.d(TAG, "Applied zoom: $zoomFactor")
        } catch (e: Exception) {
            Log.e(TAG, "applyZoom failed: ${e.message}")
        }
    }

    fun applyFlashMode(mode: String) {
        currentFlashMode = mode
        previewRequestBuilder?.let {
            applyFlashToBuilder(it)
            try {
                captureSession?.setRepeatingRequest(it.build(), null, backgroundHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "applyFlashMode setRepeatingRequest failed: ${e.message}")
            }
        }
    }

    private fun applyFlashToBuilder(builder: CaptureRequest.Builder) {
        when (currentFlashMode) {
            "AUTO" -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            "ON" -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
            }
            "OFF" -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            "TORCH" -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        }
    }

    // ------------------------- UI Transform / Util -------------------------
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = appActivity?.windowManager?.defaultDisplay?.rotation ?: 0
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize?.height?.toFloat() ?: 0f, previewSize?.width?.toFloat() ?: 0f)
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(viewHeight.toFloat() / (previewSize?.height ?: 1), viewWidth.toFloat() / (previewSize?.width ?: 1))
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        val bigEnough = ArrayList<Size>()
        for (option in choices) {
            if (option.width <= width && option.height <= height) {
                bigEnough.add(option)
            }
        }
        return if (bigEnough.isNotEmpty()) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else {
            choices[0]
        }
    }

    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(a: Size, b: Size): Int {
            return java.lang.Long.signum(a.width.toLong() * a.height - b.width.toLong() * b.height)
        }
    }
}
