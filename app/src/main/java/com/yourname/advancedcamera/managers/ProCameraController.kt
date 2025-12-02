// ProCameraController.kt
package com.eagleeye.camera.controllers

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.view.Surface
import android.view.TextureView
import android.graphics.Bitmap

class ProCameraController(
    private val context: Context,
    private val textureView: TextureView
) {

    var onCameraReady: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null
    var currentFlash = "OFF"

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    private var cameraId = "0"

    fun start() {
        if (textureView.isAvailable) openCamera()
        else textureView.surfaceTextureListener = textureListener
    }

    fun stop() {
        captureSession?.close()
        cameraDevice?.close()
        if (isRecording) stopRecording()
    }

    fun switchCamera() { /* implement front/back switch */ }
    fun setFlash(mode: String) { currentFlash = mode; updateFlash() }
    fun setFocusPoint(x: Float, y: Float) { /* implement tap to focus */ }
    fun enableNightMode() { /* low light enhancement */ }
    fun enablePortraitMode() { /* bokeh simulation */ }
    fun disableSpecialModes() { /* reset */ }
    fun applyLUT(name: String) { /* pass to processor */ }

    fun capturePhoto(callback: (Bitmap) -> Unit) {
        // implement photo capture + return bitmap
    }

    fun startRecording() {
        // implement video recording
        isRecording = true
    }

    fun stopRecording() {
        mediaRecorder?.stop()
        mediaRecorder?.release()
        isRecording = false
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openCamera() }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    private fun openCamera() {
        try {
            cameraId = getBackCameraId()
            cameraManager.openCamera(cameraId, stateCallback, null)
        } catch (e: Exception) {
            onCameraError?.invoke(e.message ?: "Camera failed")
        }
    }

    private fun getBackCameraId(): String {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return "0"
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            createPreviewSession()
            onCameraReady?.invoke()
        }
        override fun onDisconnected(device: CameraDevice) { device.close() }
        override fun onError(device: CameraDevice, error: Int) { onCameraError?.invoke("Error $error") }
    }

    private fun createPreviewSession() {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1920, 1080)
        val surface = Surface(texture)

        previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder!!.addTarget(surface)

        cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                updatePreview()
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)
    }

    private fun updatePreview() {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        updateFlash()
        captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
    }

    private fun updateFlash() {
        val mode = when (currentFlash) {
            "ON" -> CaptureRequest.FLASH_MODE_TORCH
            "OFF" -> CaptureRequest.FLASH_MODE_OFF
            else -> CaptureRequest.FLASH_MODE_OFF
        }
        previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, mode)
        captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
    }
}
