package com.yourname.advancedcamera.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat

class CameraForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "camera_record_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null  // No binding

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Foreground notification for stable recording
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Recording")
            .setContentText("Recording in progress…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        // Start Foreground Service
        startForeground(1, notification)

        // 👉 Start camera or AI tracking logic here
        startRecordingOrTracking()

        return START_STICKY
    }

    private fun startRecordingOrTracking() {
        // TODO: Add your camera / AI logic here
        // Example:
        // CameraEngine.getInstance().startRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop recording if running
        // CameraEngine.getInstance().stopRecording()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
