package com.syncbridge.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SyncBridgeApp : Application() {

    companion object {
        const val CHANNEL_SERVER = "syncbridge_server"
        const val CHANNEL_ALERTS = "syncbridge_alerts"
        lateinit var instance: SyncBridgeApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVER,
                    "Bridge Server",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "SyncBridge server running indicator" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "SyncBridge connection alerts" }
            )
        }
    }
}
