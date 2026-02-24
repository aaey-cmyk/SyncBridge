package com.syncbridge.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Auto-starts BridgeServerService after device reboot
 * (if autoStart is enabled in settings).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autoStart", true)
        if (autoStart) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BridgeServerService::class.java)
            )
        }
    }
}
