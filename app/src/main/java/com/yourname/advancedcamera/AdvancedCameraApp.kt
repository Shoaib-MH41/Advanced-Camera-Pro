package com.yourname.advancedcamera

import android.app.Application
import android.os.Build
import android.util.Log
import com.yourname.advancedcamera.ai.AIModelManager
import com.yourname.advancedcamera.utils.ImageEngine
import com.yourname.advancedcamera.utils.AppExecutors

class AdvancedCameraApp : Application() {

    companion object {
        private const val TAG = "AdvancedCameraApp"
        lateinit var instance: AdvancedCameraApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "🔥 Advanced Camera Pro Application Started")

        // Global Initialization
        initializeCore()
        initializeAIModules()
        initializeImageEngine()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "📘 Debug Mode: Extra logging enabled")
        }
    }

    /**
     * Initialize core components and thread pools
     */
    private fun initializeCore() {
        Log.d(TAG, "⚙ Initializing Core Components…")

        // Optimized thread pools for image processing / AI
        AppExecutors.init()

        // Crash Handler (Prevents app from crashing instantly)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "❌ Crash Detected: ${error.message}", error)
        }

        // Low RAM device adjustments
        if (isLowRamDevice()) {
            Log.w(TAG, "⚠ Low RAM mode enabled → Reduced AI load")
        }
    }

    /**
     * Initialize the AI Engine (Night Vision, HDR Fusion, Denoise, LUT Engine)
     */
    private fun initializeAIModules() {
        Log.d(TAG, "🤖 Loading AI Engine…")

        AIModelManager.initialize(
            context = this,
            enableLowRamMode = isLowRamDevice()
        )
    }

    /**
     * Initialize Image Processing Engine
     */
    private fun initializeImageEngine() {
        Log.d(TAG, "🖥 Initializing Image Processing Engine…")
        ImageEngine.initialize()
    }

    override fun onTerminate() {
        super.onTerminate()

        Log.d(TAG, "🛑 Application Terminating… Cleaning up engines")

        // Stop background threads
        AppExecutors.shutdown()

        // Unload AI / Images
        AIModelManager.shutdown()
        ImageEngine.shutdown()
    }

    /**
     * Detect low-spec devices to adjust features
     */
    private fun isLowRamDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        return maxMemory < 2000 // less than 2GB RAM
    }
}
