package com.yourname.advancedcamera

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.yourname.advancedcamera.ai.AIModelManager
import com.yourname.advancedcamera.utils.ImageEngine
import com.yourname.advancedcamera.utils.AppExecutors
import com.yourname.advancedcamera.utils.PreferenceManager
import com.yourname.advancedcamera.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

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
        
        // Phase 2: Dependency Injection
        initializeDependencyInjection()
        
        // Phase 3: Feature Modules
        initializeFeatureModules()
        
        // Phase 4: Performance Optimization
        initializePerformanceTuning()
        
        Log.i(TAG, "🚀 Advanced Camera Pro v${BuildConfig.APP_VERSION} Initialized Successfully")
        Log.d(TAG, "📱 Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}")
    }

    /**
     * 🔧 Phase 1: Core Foundation Setup
     */
    private fun initializeFoundation() {
        Log.d(TAG, "🏗 Initializing Core Foundation...")
        
        // Initialize thread pools and executors
        AppExecutors.init()
        
        // Initialize preference manager
        PreferenceManager.initialize(this)
        
        // Set app theme based on system or user preference
        AppCompatDelegate.setDefaultNightMode(
            PreferenceManager.getThemeMode()
        )
        
        // Initialize analytics (optional)
        initializeAnalytics()
    }

    /**
     * 💉 Phase 2: Dependency Injection Setup
     */
    private fun initializeDependencyInjection() {
        Log.d(TAG, "💉 Initializing Dependency Injection...")
        
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@AdvancedCameraApp)
            modules(AppModule.modules)
        }
    }

    /**
     * 🎯 Phase 3: Feature Modules Initialization
     */
    private fun initializeFeatureModules() {
        Log.d(TAG, "🎯 Initializing Feature Modules...")
        
        // AI Engine with optimized loading strategy
        initializeAIModules()
        
        // Image Processing Engine
        initializeImageEngine()
        
        // Camera Service Pre-warm
        initializeCameraServices()
        
        // Storage Manager
        initializeStorageManager()
    }

    /**
     * ⚡ Phase 4: Performance Optimization
     */
    private fun initializePerformanceTuning() {
        Log.d(TAG, "⚡ Applying Performance Optimizations...")
        
        // Memory optimization based on device capabilities
        if (isLowRamDevice()) {
            Log.w(TAG, "📉 Low RAM Device Detected - Enabling Lite Mode")
            enableLiteMode()
        } else {
            Log.i(TAG, "📈 High Performance Mode Activated")
            enablePerformanceMode()
        }
        
        // Battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations()) {
                Log.w(TAG, "🔋 Consider adding battery optimization whitelist")
            }
        }
    }

    /**
     * 🤖 AI Modules Initialization with Smart Loading
     */
    private fun initializeAIModules() {
        Log.d(TAG, "🧠 Loading AI Intelligence Engine...")
        
        val startTime = System.currentTimeMillis()
        
        AIModelManager.initialize(
            context = this,
            enableLowRamMode = isLowRamDevice(),
            loadEssentialOnly = true, // Load critical models first
            onProgress = { model, progress ->
                Log.d(TAG, "📦 AI Model Loading: $model - $progress%")
            }
        )
        
        val loadTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "✅ AI Engine Ready in ${loadTime}ms")
    }

    /**
     * 🖼 Image Processing Engine Setup
     */
    private fun initializeImageEngine() {
        Log.d(TAG, "🎨 Initializing Image Processing Engine...")
        
        ImageEngine.initialize(
            enableHardwareAcceleration = !isLowRamDevice(),
            cacheSize = if (isLowRamDevice()) 0.3 else 0.6, // % of max memory
            backgroundProcessing = true
        )
    }

    /**
     * 📷 Camera Services Pre-warming
     */
    private fun initializeCameraServices() {
        if (PreferenceManager.isCameraPreWarmEnabled() && !isLowRamDevice()) {
            Log.d(TAG, "📸 Pre-warming Camera Services...")
            // CameraManager.preWarm() - You can implement this
        }
    }

    /**
     * 💾 Storage Manager Initialization
     */
    private fun initializeStorageManager() {
        Log.d(TAG, "💾 Initializing Storage Manager...")
        // StorageManager.initialize(this) - You can implement this
    }

    /**
     * 📊 Analytics Setup (Optional)
     */
    private fun initializeAnalytics() {
        if (PreferenceManager.isAnalyticsEnabled()) {
            Log.d(TAG, "📊 Initializing Analytics...")
            // Firebase.initialize(this) - Add if needed
        }
    }

    /**
     * 🛡 Advanced Exception Handling & Crash Prevention
     */
    private fun setupExceptionHandling() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the crash with detailed context
            Log.e(TAG, "💥 Critical Exception in ${thread.name}:", throwable)
            
            // Attempt graceful recovery for non-fatal errors
            if (isRecoverableError(throwable)) {
                Log.w(TAG, "🔄 Attempting graceful recovery...")
                // You can add recovery logic here
            }
            
            // Call original handler (will crash app)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 📉 Lite Mode for Low-RAM Devices
     */
    private fun enableLiteMode() {
        // Reduce cache sizes
        System.gc()
        
        // Disable heavy features
        PreferenceManager.setAIModeEnabled(false)
        PreferenceManager.setHDREnaled(false)
        
        Log.i(TAG, "🔄 Lite Mode: AI and HDR features disabled for better performance")
    }

    /**
     * 🚀 Performance Mode for High-End Devices
     */
    private fun enablePerformanceMode() {
        // Enable all features
        PreferenceManager.setAIModeEnabled(true)
        PreferenceManager.setHDREnabled(true)
        PreferenceManager.set4KRecordingEnabled(true)
        
        Log.i(TAG, "🎯 Performance Mode: All premium features enabled")
    }

    /**
     * 🔍 Device Capability Checks
     */
    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.isLowRamDevice || Runtime.getRuntime().maxMemory() < 2L * 1024 * 1024 * 1024 // 2GB
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is OutOfMemoryError -> true
            is NullPointerException -> true
            else -> false
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "🚨 Low Memory Warning - Cleaning caches")
        
        // Clear image caches
        ImageEngine.clearCache()
        
        // Reduce AI model memory footprint
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
                ImageEngine.reduceCacheSize(0.5) // Reduce to 50%
            }
        }
    }

    override fun onTerminate() {
        Log.i(TAG, "🛑 Application Termination Started...")
        
        // Phase 1: Stop background operations
        AppExecutors.shutdown()
        
        // Phase 2: Release AI resources
        AIModelManager.shutdown()
        
        // Phase 3: Clear image caches
        ImageEngine.shutdown()
        
        // Phase 4: Unregister lifecycle callbacks
        unregisterActivityLifecycleCallbacks(AppLifecycleTracker)
        
        _instance = null
        
        super.onTerminate()
        Log.i(TAG, "✅ Application Cleanup Completed")
    }
}

/**
 * 📊 App Lifecycle Tracker for Monitoring
 */
object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    private var foregroundActivities = 0
    val isAppInForeground: Boolean get() = foregroundActivities > 0
    
    override fun onActivityStarted(activity: Activity) {
        foregroundActivities++
        if (foregroundActivities == 1) {
            Log.d("AppLifecycle", "🟢 App entered foreground")
        }
    }
    
    override fun onActivityStopped(activity: Activity) {
        foregroundActivities--
        if (foregroundActivities == 0) {
            Log.d("AppLifecycle", "🔴 App entered background")
        }
    }
    
    // Other lifecycle methods with empty implementations
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
