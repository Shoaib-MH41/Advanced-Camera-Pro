package com.yourname.advancedcamera

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context  // ✅ IMPORT ADDED
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.yourname.advancedcamera.ai.AIModelManager
import com.yourname.advancedcamera.utils.AppExecutors
import com.yourname.advancedcamera.utils.ImageEngine

class AdvancedCameraApp : Application() {

    companion object {
        private const val TAG = "AdvancedCameraApp"
        
        @Volatile
        private var _instance: AdvancedCameraApp? = null
        
        val instance: AdvancedCameraApp
            get() = _instance ?: throw IllegalStateException("Application not initialized")
            
        val appContext: Context
            get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize singleton instance
        _instance = this
        
        // Initialize app lifecycle tracker
        registerActivityLifecycleCallbacks(AppLifecycleTracker)
        
        // Setup crash prevention and monitoring
        setupExceptionHandling()
        
        // Phase 1: Core Foundation
        initializeFoundation()
        
        // Phase 2: Feature Modules
        initializeFeatureModules()
        
        // Phase 3: Performance Optimization
        initializePerformanceTuning()
        
        Log.i(TAG, "🚀 Advanced Camera Pro v${BuildConfig.APP_VERSION} Initialized Successfully")
        Log.d(TAG, "📱 Build Type: ${BuildConfig.BUILD_TYPE} | Debug: ${BuildConfig.IS_DEBUG}")
    }

    /**
     * 🔧 Phase 1: Core Foundation Setup
     */
    private fun initializeFoundation() {
        Log.d(TAG, "🏗 Initializing Core Foundation...")
        
        // Initialize thread pools and executors
        AppExecutors.init()
        
        // Set app theme based on system
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        Log.d(TAG, "✅ Core Foundation Ready")
    }

    /**
     * 🎯 Phase 2: Feature Modules Initialization
     */
    private fun initializeFeatureModules() {
        Log.d(TAG, "🎯 Initializing Feature Modules...")
        
        // AI Engine with optimized loading
        initializeAIModules()
        
        // Image Processing Engine
        initializeImageEngine()
        
        Log.d(TAG, "✅ Feature Modules Ready")
    }

    /**
     * ⚡ Phase 3: Performance Optimization
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
        
        Log.d(TAG, "✅ Performance Optimization Complete")
    }

    /**
     * 🤖 AI Modules Initialization
     */
    private fun initializeAIModules() {
        Log.d(TAG, "🧠 Loading AI Intelligence Engine...")
        
        AIModelManager.initialize(
            context = this,
            enableLowRamMode = isLowRamDevice(),
            loadEssentialOnly = true,
            onProgress = { model, progress ->
                Log.d(TAG, "📦 AI Model Loading: $model - $progress%")
            }
        )
        
        Log.i(TAG, "✅ AI Engine Ready")
    }

    /**
     * 🖼 Image Processing Engine Setup
     */
    private fun initializeImageEngine() {
        Log.d(TAG, "🎨 Initializing Image Processing Engine...")
        
        ImageEngine.initialize()
        Log.i(TAG, "✅ Image Engine Ready")
    }

    /**
     * 🛡 Advanced Exception Handling & Crash Prevention
     */
    private fun setupExceptionHandling() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "💥 Critical Exception in ${thread.name}:", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 📉 Lite Mode for Low-RAM Devices
     */
    private fun enableLiteMode() {
        System.gc()
        Log.i(TAG, "🔄 Lite Mode: Optimized for low memory devices")
    }

    /**
     * 🔍 Device Capability Check
     */
    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.isLowRamDevice
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "🚨 Low Memory Warning - Cleaning caches")
        ImageEngine.clearCache()
        AIModelManager.reduceMemoryUsage()
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "🧹 TRIM_MEMORY_COMPLETE - Aggressive cleanup")
                ImageEngine.clearCache()
                AIModelManager.unloadNonEssentialModels()
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.d(TAG, "🧹 TRIM_MEMORY_MODERATE - Moderate cleanup")
                ImageEngine.reduceCacheSize(0.5)
            }
        }
    }

    override fun onTerminate() {
        Log.i(TAG, "🛑 Application Termination Started...")
        AppExecutors.shutdown()
        AIModelManager.shutdown()
        ImageEngine.shutdown()
        unregisterActivityLifecycleCallbacks(AppLifecycleTracker)
        _instance = null
        super.onTerminate()
        Log.i(TAG, "✅ Application Cleanup Completed")
    }
}

/**
 * 📊 App Lifecycle Tracker
 */
object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    private var foregroundActivities = 0
    val isAppInForeground: Boolean get() = foregroundActivities > 0
    
    override fun onActivityStarted(activity: android.app.Activity) {
        foregroundActivities++
        if (foregroundActivities == 1) {
            Log.d("AppLifecycle", "🟢 App entered foreground")
        }
    }
    
    override fun onActivityStopped(activity: android.app.Activity) {
        foregroundActivities--
        if (foregroundActivities == 0) {
            Log.d("AppLifecycle", "🔴 App entered background")
        }
    }
    
    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityResumed(activity: android.app.Activity) {}
    override fun onActivityPaused(activity: android.app.Activity) {}
    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: android.app.Activity) {}
}
