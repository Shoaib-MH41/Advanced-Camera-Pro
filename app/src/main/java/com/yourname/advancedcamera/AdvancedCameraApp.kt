package com.yourname.advancedcamera

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

class AdvancedCameraApp : Application() {

    companion object {
        private const val TAG = "AdvancedCameraApp"
        
        @Volatile
        private var _instance: AdvancedCameraApp? = null
        
        val instance: AdvancedCameraApp
            get() = _instance ?: throw IllegalStateException("Application not initialized")
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize singleton instance
        _instance = this
        
        // Setup crash prevention
        setupExceptionHandling()
        
        // Initialize core components
        initializeFoundation()
        
        // Initialize feature modules
        initializeFeatureModules()
        
        Log.i(TAG, "🚀 Advanced Camera Pro Initialized Successfully")
    }

    /**
     * Core Foundation Setup
     */
    private fun initializeFoundation() {
        Log.d(TAG, "🏗 Initializing Core Foundation...")
        
        // Initialize thread pools
        AppExecutors.init()
        
        // Set app theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    /**
     * Feature Modules Initialization
     */
    private fun initializeFeatureModules() {
        Log.d(TAG, "🎯 Initializing Feature Modules...")
        
        // AI Engine with basic initialization
        initializeAIModules()
        
        // Image Processing Engine
        initializeImageEngine()
        
        // Apply performance optimizations
        initializePerformanceTuning()
    }

    /**
     * AI Modules Initialization - SIMPLIFIED
     */
    private fun initializeAIModules() {
        Log.d(TAG, "🧠 Loading AI Engine...")
        
        // Basic initialization without extra parameters
        AIModelManager.initialize(context = this)
    }

    /**
     * Image Processing Engine Setup - SIMPLIFIED
     */
    private fun initializeImageEngine() {
        Log.d(TAG, "🎨 Initializing Image Processing Engine...")
        
        // Basic initialization
        ImageEngine.initialize()
    }

    /**
     * Performance Optimization
     */
    private fun initializePerformanceTuning() {
        Log.d(TAG, "⚡ Applying Performance Optimizations...")
        
        // Memory optimization based on device capabilities
        if (isLowRamDevice()) {
            Log.w(TAG, "📉 Low RAM Device Detected - Enabling Lite Mode")
            enableLiteMode()
        } else {
            Log.i(TAG, "📈 High Performance Mode Activated")
        }
    }

    /**
     * Advanced Exception Handling & Crash Prevention
     */
    private fun setupExceptionHandling() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the crash with detailed context
            Log.e(TAG, "💥 Critical Exception in ${thread.name}:", throwable)
            
            // Call original handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Lite Mode for Low-RAM Devices
     */
    private fun enableLiteMode() {
        // Reduce memory usage
        System.gc()
        Log.i(TAG, "🔄 Lite Mode: Optimized for low memory")
    }

    /**
     * Device Capability Check
     */
    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.isLowRamDevice
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "🚨 Low Memory Warning - Cleaning caches")
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "🧹 TRIM_MEMORY_COMPLETE - Aggressive cleanup")
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.d(TAG, "🧹 TRIM_MEMORY_MODERATE - Moderate cleanup")
            }
        }
    }

    override fun onTerminate() {
        Log.i(TAG, "🛑 Application Termination Started...")
        
        // Release resources
        AppExecutors.shutdown()
        
        _instance = null
        
        super.onTerminate()
        Log.i(TAG, "✅ Application Cleanup Completed")
    }
}
