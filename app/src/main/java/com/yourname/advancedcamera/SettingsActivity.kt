package com.yourname.advancedcamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Load settings fragment dynamically
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

            // Load settings.xml
            setPreferencesFromResource(R.xml.settings, rootKey)

            // Example: read preferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Example: apply change listeners
            // findPreference<SwitchPreferenceCompat>("enable_ai")?.setOnPreferenceChangeListener { _, newValue ->
            //     CameraConfig.aiEnabled = newValue as Boolean
            //     true
            // }
        }
    }
}
