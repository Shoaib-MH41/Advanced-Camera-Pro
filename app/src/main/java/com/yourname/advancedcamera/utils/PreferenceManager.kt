package com.yourname.advancedcamera.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object PreferenceManager {
    
    private lateinit var prefs: android.content.SharedPreferences
    
    fun initialize(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }
    
    // Theme Methods
    fun getThemeMode(): Int = when (prefs.getString("theme_mode", "system")) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    
    // Feature Toggles
    fun isAIModeEnabled(): Boolean = prefs.getBoolean("ai_mode", true)
    fun setAIModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("ai_mode", enabled).apply()
    
    fun isHDREnabled(): Boolean = prefs.getBoolean("hdr_mode", true)
    fun setHDREnabled(enabled: Boolean) = prefs.edit().putBoolean("hdr_mode", enabled).apply()
    
    fun isCameraPreWarmEnabled(): Boolean = prefs.getBoolean("camera_prewarm", true)
    fun isAnalyticsEnabled(): Boolean = prefs.getBoolean("analytics", false)
}
