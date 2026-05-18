package com.paste.clipboard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PasteApp : Application() {
    companion object {
        const val CHANNEL_ID = "paste_service_channel"
        lateinit var instance: PasteApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
