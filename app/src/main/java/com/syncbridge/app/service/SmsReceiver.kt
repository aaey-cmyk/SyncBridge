package com.syncbridge.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.syncbridge.app.model.WsEvent

/**
 * Listens for incoming SMS and immediately pushes a WebSocket event
 * to all connected Chrome clients.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group PDUs by sender
        val grouped = mutableMapOf<String, StringBuilder>()
        messages.forEach { sms ->
            val from = sms.displayOriginatingAddress ?: return@forEach
            grouped.getOrPut(from) { StringBuilder() }.append(sms.messageBody)
        }

        grouped.forEach { (from, body) ->
            val event = WsEvent(
                type = "sms",
                from = from,
                text = body.toString()
            )
            BridgeServerService.pushEvent(context, event)
        }
    }
}
