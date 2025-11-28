package com.yourname.advancedcamera

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Back button handler
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Load settings fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(), 
        SharedPreferences.OnSharedPreferenceChangeListener {

        private lateinit var prefs: SharedPreferences

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Load settings from XML
            setPreferencesFromResource(R.xml.settings, rootKey)

            // Initialize preferences
            prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            
            // Setup preference summaries
            updateSummaries()
        }

        override fun onResume() {
            super.onResume()
            // Register preference change listener
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            // Unregister preference change listener
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key != null) {
                updatePreferenceSummary(key)
                handlePreferenceChange(key)
            }
        }

        private fun updateSummaries() {
            // Update all preference summaries
            for (i in 0 until preferenceScreen.preferenceCount) {
                val preference = preferenceScreen.getPreference(i)
                updatePreferenceSummary(preference.key)
            }
        }

        private fun updatePreferenceSummary(key: String?) {
            if (key == null) return

            val preference = findPreference<Preference>(key)
            when (preference) {
                is ListPreference -> {
                    // For ListPreference, show selected value as summary
                    val value = preference.value ?: ""
                    val entry = preference.entries[preference.findIndexOfValue(value)]
                    preference.summary = entry.toString()
                }
                is SwitchPreferenceCompat -> {
                    // For SwitchPreference, show status
                    val status = if (preference.isChecked) "Enabled" else "Disabled"
                    preference.summary = "Currently $status"
                }
            }
        }

        private fun handlePreferenceChange(key: String) {
            when (key) {
                "video_quality" -> {
                    val quality = prefs.getString(key, "1080p")
                    showToast("Video quality set to: $quality")
                    // Update camera configuration
                    CameraConfig.videoQuality = quality ?: "1080p"
                }
                "photo_resolution" -> {
                    val resolution = prefs.getString(key, "12MP")
                    showToast("Photo resolution set to: $resolution")
                    CameraConfig.photoResolution = resolution ?: "12MP"
                }
                "auto_flash" -> {
                    val isAutoFlash = prefs.getBoolean(key, true)
                    showToast("Auto flash ${if (isAutoFlash) "enabled" else "disabled"}")
                    CameraConfig.autoFlash = isAutoFlash
                }
                "timer_delay" -> {
                    val timer = prefs.getString(key, "0")
                    showToast("Timer delay set to: ${timer}s")
                    CameraConfig.timerDelay = timer?.toIntOrNull() ?: 0
                }
                "burst_mode" -> {
                    val burstMode = prefs.getBoolean(key, false)
                    showToast("Burst mode ${if (burstMode) "enabled" else "disabled"}")
                    CameraConfig.burstMode = burstMode
                }
                "grid_lines" -> {
                    val gridLines = prefs.getBoolean(key, true)
                    showToast("Grid lines ${if (gridLines) "enabled" else "disabled"}")
                    CameraConfig.showGridLines = gridLines
                }
                "storage_location" -> {
                    val storage = prefs.getString(key, "internal")
                    showToast("Storage location: $storage")
                    CameraConfig.storageLocation = storage ?: "internal"
                }
                "image_format" -> {
                    val format = prefs.getString(key, "jpeg")
                    showToast("Image format: ${format?.uppercase()}")
                    CameraConfig.imageFormat = format ?: "jpeg"
                }
            }
        }

        private fun showToast(message: String) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}

// Camera Configuration Object
object CameraConfig {
    var videoQuality: String = "1080p"
    var photoResolution: String = "12MP"
    var autoFlash: Boolean = true
    var timerDelay: Int = 0
    var burstMode: Boolean = false
    var showGridLines: Boolean = true
    var storageLocation: String = "internal"
    var imageFormat: String = "jpeg"
    
    // Additional camera settings can be added here
    var aiEnabled: Boolean = true
    var hdrEnabled: Boolean = false
    var stabilizationEnabled: Boolean = true
}
