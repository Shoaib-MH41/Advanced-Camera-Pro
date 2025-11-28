package com.yourname.advancedcamera.utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Log

object AppExecutors {

    private const val TAG = "AppExecutors"

    lateinit var ioExecutor: ExecutorService
    lateinit var cpuExecutor: ExecutorService
    lateinit var cameraExecutor: ExecutorService

    fun init() {
        Log.d(TAG, "⚙ Initializing Global Thread Executors…")

        // For heavy CPU AI tasks (Denoise, Fusion, Deblur)
        cpuExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        )

        // For file writes, saving images
        ioExecutor = Executors.newSingleThreadExecutor()

        // For camera operations / preview frame processing
        cameraExecutor = Executors.newFixedThreadPool(2)
    }

    fun shutdown() {
        try {
            Log.d(TAG, "🛑 Shutting down AppExecutors…")
            ioExecutor.shutdown()
            cpuExecutor.shutdown()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error shutting down executors: ${e.message}")
        }
    }
}
