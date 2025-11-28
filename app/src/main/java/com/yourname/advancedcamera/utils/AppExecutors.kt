package com.yourname.advancedcamera.utils

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class AppExecutors private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: AppExecutors? = null
        
        fun getInstance(): AppExecutors {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppExecutors().also { INSTANCE = it }
            }
        }
        
        fun init() {
            // Initialize singleton
            getInstance()
        }
        
        fun shutdown() {
            INSTANCE?.shutdownAll()
            INSTANCE = null
        }
    }
    
    private val diskIO = Executors.newSingleThreadExecutor()
    private val networkIO = Executors.newFixedThreadPool(3)
    private val mainThreadExecutor = MainThreadExecutor()
    
    fun diskIO(): java.util.concurrent.Executor = diskIO
    fun networkIO(): java.util.concurrent.Executor = networkIO
    fun mainThread(): java.util.concurrent.Executor = mainThreadExecutor
    
    private fun shutdownAll() {
        diskIO.shutdown()
        networkIO.shutdown()
    }
}

// Simple main thread executor for testing
class MainThreadExecutor : java.util.concurrent.Executor {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    override fun execute(command: Runnable) {
        handler.post(command)
    }
}
