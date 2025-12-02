package com.yourname.advancedcamera.managers

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraMetadata.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView

class ProCameraController(
    private val context: Context,
    private val textureView: TextureView
) {

    private val TAG = "ProCameraController"

    private var cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraId = "0"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    /** Background Thread */
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    /** Current Camera Settings */
    private var isoValue = 100
    private var exposureValue = 0
    private var focusValue = 50
    private var zoomValue = 1.0f

    /** Preview Request */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /** Listener: Camera Active → Ready */
    var onCameraReady: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null

    // ---------------------------------------------------------
    // INIT
    // ---------------------------------------------------------
    fun start() {
        startBackgroundThread()

        if (textureView.isAvailable)
            openCamera()
        else
            textureView.surfaceTextureListener = textureListener
    }

    // ---------------------------------------------------------
    // STOP CAMERA
    // ---------------------------------------------------------
    fun stop() {
        captureSession?.close()
        cameraDevice?.close()
        stopBackgroundThread()
    }

    // ---------------------------------------------------------
    // TEXTURE LISTENER
    // ---------------------------------------------------------
    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    // ---------------------------------------------------------
    // OPEN CAMERA SAFE
    // ---------------------------------------------------------
    private fun openCamera() {
        try {
            selectBackCamera()
            cameraManager.openCamera(cameraId, stateCallback, bgHandler)
        } catch (e: Exception) {
            onCameraError?.invoke("Camera open failed: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // SELECT BACK CAMERA
    // ---------------------------------------------------------
    private fun selectBackCamera() {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = id
                return
            }
        }
    }

    // ---------------------------------------------------------
    // CAMERA DEVICE CALLBACK
    // ---------------------------------------------------------
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            createPreviewSession()
            onCameraReady?.invoke()
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
        }

        override fun onError(device: CameraDevice, error: Int) {
            onCameraError?.invoke("Camera Error: $error")
            device.close()
        }
    }

    // ---------------------------------------------------------
    // PREVIEW SESSION
    // ---------------------------------------------------------
    private fun createPreviewSession() {
        val st = textureView.surfaceTexture ?: return
        st.setDefaultBufferSize(1920, 1080)

        val surface = Surface(st)

        try {
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        applySettings()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onCameraError?.invoke("Preview configuration failed")
                    }
                }, bgHandler
            )

        } catch (e: Exception) {
            onCameraError?.invoke("Preview failed: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // APPLY ALL SETTINGS
    // ---------------------------------------------------------
    private fun applySettings() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CONTROL_MODE_OFF)
        previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusValue / 100f)

        previewRequestBuilder.set(
            CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
            exposureValue
        )

        captureSession?.setRepeatingRequest(
            previewRequestBuilder.build(),
            null,
            bgHandler
        )
    }

    // ---------------------------------------------------------
    // MANUAL CONTROLS
    // ---------------------------------------------------------
    fun setISO(value: Int) {
        isoValue = value
        applySettings()
    }

    fun setExposure(value: Int) {
        exposureValue = value
        applySettings()
    }

    fun setFocus(value: Int) {
        focusValue = value
        applySettings()
    }

    fun setZoom(value: Float) {
        zoomValue = value
        applySettings()
    }

    // ---------------------------------------------------------
    // BACKGROUND THREAD
    // ---------------------------------------------------------
    private fun startBackgroundThread() {
        bgThread = HandlerThread("CameraBG").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    private fun stopBackgroundThread() {
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
    }
}
