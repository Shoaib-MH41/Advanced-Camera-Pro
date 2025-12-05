package com.yourname.advancedcamera

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.yourname.advancedcamera.ai.AIModelManager
import com.yourname.advancedcamera.utils.AppExecutors
import com.yourname.advancedcamera.utils.ImageEngine
import java.io.File

class AdvancedCameraApp : Application() {

    companion object {
        private const val TAG = "AdvancedCameraApp"

        @Volatile
        private var instanceRef: AdvancedCameraApp? = null

        /** Global App Instance */
        val instance: AdvancedCameraApp
            get() = instanceRef ?: error("❌ Application not initialized")

        /** Global Safe Context */
        val appContext: Context
            get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()

        instanceRef = this
        registerActivityLifecycleCallbacks(AppLifecycleTracker)

        Log.i(TAG, "🚀 Booting Advanced Camera Pro...")

        setupGlobalExceptionHandler()
        initializeCore()
        initializeModules()
        optimizePerformance()

        Log.i(
            TAG,
            "✅ Initialization Complete | Version ${BuildConfig.APP_VERSION} | ${BuildConfig.BUILD_TYPE}"
        )
    }

    // ---------------------------------------------------------
    // CORE INITIALIZATION
    // ---------------------------------------------------------

    private fun initializeCore() {
        Log.d(TAG, "🏗 Initializing Core Foundation…")

        AppExecutors.init()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        Log.d(TAG, "✔ Core Foundation Ready")
    }

    // ---------------------------------------------------------
    // MODULES
    // ---------------------------------------------------------

    private fun initializeModules() {
        Log.d(TAG, "🎯 Initializing Feature Modules…")

        initAI()
        initImageEngine()

        Log.d(TAG, "✔ Feature Modules Ready")
    }

    private fun initAI() {
        Log.d(TAG, "🤖 Loading AI Models…")

        AIModelManager.initialize(
            context = this,
            enableLowRamMode = isLowRamDevice(),
            loadEssentialOnly = true,
            onProgress = { name, progress ->
                Log.d(TAG, "⚙ Loading Model: $name — $progress%")
            }
        )

        Log.i(TAG, "✔ AI Engine Ready")
    }

    private fun initImageEngine() {
        Log.d(TAG, "🎨 Initializing Image Engine…")

        ImageEngine.initialize()

        Log.i(TAG, "✔ Image Engine Ready")
    }

    // ---------------------------------------------------------
    // PERFORMANCE
    // ---------------------------------------------------------

    private fun optimizePerformance() {
        Log.d(TAG, "⚡ Optimizing Performance…")

        if (isLowRamDevice()) {
            enableLiteMode()
            Log.w(TAG, "📉 Low RAM Mode Enabled")
        } else {
            Log.i(TAG, "📈 High Performance Mode Enabled")
        }

        Log.d(TAG, "✔ Performance Optimization Complete")
    }

    private fun enableLiteMode() {
        System.gc()
        AIModelManager.reduceMemoryUsage()
        ImageEngine.reduceCacheSize(0.5)
    }

    // ---------------------------------------------------------
    // CRASH LOGGER (FULL VERSION)
    // ---------------------------------------------------------

    private fun setupGlobalExceptionHandler() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "💥 FATAL EXCEPTION in ${thread.name}", error)

            try {
                val logText = """
                THREAD: ${thread.name}
                TIME: ${System.currentTimeMillis()}
                ERROR: ${error.message}
                
                STACKTRACE:
                ${error.stackTraceToString()}
                """.trimIndent()

                val file = File(getExternalFilesDir(null), "crash_log.txt")
                file.writeText(logText)

                Log.e(TAG, "📄 Crash Log Saved: ${file.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to write crash log: ${e.message}")
            }

            originalHandler?.uncaughtException(thread, error)
        }
    }

    // ---------------------------------------------------------
    // MEMORY HANDLERS
    // ---------------------------------------------------------

    private fun isLowRamDevice(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "🚨 LOW MEMORY — Cleaning Cache…")
        ImageEngine.clearCache()
        AIModelManager.reduceMemoryUsage()
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "🧹 Aggressive Cleanup Triggered")
                ImageEngine.clearCache()
                AIModelManager.unloadNonEssentialModels()
            }

            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.d(TAG, "♻ Moderate Memory Trim")
                ImageEngine.reduceCacheSize(0.5)
            }
        }
    }

    override fun onTerminate() {
        Log.i(TAG, "🛑 Shutting Down Application…")

        AppExecutors.shutdown()
        AIModelManager.shutdown()
        ImageEngine.shutdown()
        unregisterActivityLifecycleCallbacks(AppLifecycleTracker)

        instanceRef = null
        super.onTerminate()

        Log.i(TAG, "✔ Application Terminated Cleanly")
    }
}

// ---------------------------------------------------------
// GLOBAL LIFECYCLE TRACKER
// ---------------------------------------------------------

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private var foregroundCount = 0
    val isForeground: Boolean get() = foregroundCount > 0

    override fun onActivityStarted(activity: Activity) {
        foregroundCount++
        if (foregroundCount == 1)
            Log.d("AppLifecycle", "🟢 App is now in FOREGROUND")
    }

    override fun onActivityStopped(activity: Activity) {
        foregroundCount--
        if (foregroundCount == 0)
            Log.d("AppLifecycle", "🔴 App moved to BACKGROUND")
    }

    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityResumed(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}
