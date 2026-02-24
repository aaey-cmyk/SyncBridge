package com.syncbridge.app.service

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.syncbridge.app.model.NotifEntry
import com.syncbridge.app.model.WsEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Captures ALL Android notifications and:
 * 1. Stores them in BridgeServerService.notifications list (for GET /api/notifications)
 * 2. Pushes a WsEvent to all connected Chrome clients immediately
 */
class NotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SyncNotifListener"
        private val idCounter = AtomicInteger(0)
        private val IGNORED_PACKAGES = setOf(
            "com.syncbridge.app",
            "android",
            "com.android.systemui"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg in IGNORED_PACKAGES) return
        if (sbn.isOngoing) return // skip persistent notifications

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val appName = getAppLabel(pkg)

        val entry = NotifEntry(
            id = idCounter.incrementAndGet(),
            app = appName,
            icon = "🔔",
            color = "#3b82f6",
            title = title,
            body = text,
            time = "Just now",
            read = false,
            timestamp = System.currentTimeMillis()
        )

        // Store in server
        getBridgeService()?.notifications?.add(0, entry)

        // Push WebSocket event
        val event = WsEvent(
            type = "notification",
            appName = appName,
            appIcon = "🔔",
            title = title,
            body = text
        )
        BridgeServerService.pushEvent(this, event)
        Log.d(TAG, "Notification from $appName: $title")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optionally remove from list
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { packageName }
    }

    private fun getBridgeService(): BridgeServerService? {
        // Use a static reference set by BridgeServerService
        return BridgeServerService.instance
    }
}

// Add to BridgeServerService companion object for reference
private val BridgeServerService.Companion.instance: BridgeServerService?
    get() = _instance

private var _instance: BridgeServerService? = null
