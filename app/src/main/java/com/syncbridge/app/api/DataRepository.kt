package com.syncbridge.app.api

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.syncbridge.app.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads real on-device data: SMS, call logs, files, battery, etc.
 * All methods are safe to call from a background thread.
 */
class DataRepository(private val context: Context) {

    // ── Device Info ───────────────────────────────────────────────────────────
    fun getDeviceInfo(): DeviceInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrier = tm.networkOperatorName.ifBlank { "Unknown" }

        return DeviceInfo(
            name = "${Build.MANUFACTURER} ${Build.MODEL}",
            os = "Android ${Build.VERSION.RELEASE}",
            battery = battery.coerceIn(0, 100),
            signal = "LTE",
            carrier = carrier
        )
    }

    // ── SMS ───────────────────────────────────────────────────────────────────
    fun getSmsConversations(): List<SmsConversation> {
        val cr: ContentResolver = context.contentResolver
        val conversations = mutableMapOf<String, SmsConversation>()

        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        val cursor: Cursor? = cr.query(
            uri, projection, null, null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use { c ->
            val idxAddress = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = c.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = c.getColumnIndex(Telephony.Sms.DATE)
            val idxType = c.getColumnIndex(Telephony.Sms.TYPE)
            val idxRead = c.getColumnIndex(Telephony.Sms.READ)
            val idxId = c.getColumnIndex(Telephony.Sms._ID)

            while (c.moveToNext()) {
                val address = c.getString(idxAddress) ?: continue
                val body = c.getString(idxBody) ?: ""
                val date = c.getLong(idxDate)
                val type = c.getInt(idxType)
                val read = c.getInt(idxRead)
                val id = c.getLong(idxId)

                val dir = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "incoming" else "outgoing"
                val msg = SmsMessage(dir, body, formatDate(date), date)
                val contactName = resolveContact(cr, address)

                if (conversations.containsKey(address)) {
                    val existing = conversations[address]!!
                    val newMessages = existing.messages.toMutableList().also { it.add(msg) }
                    conversations[address] = existing.copy(
                        messages = newMessages,
                        unread = if (read == 0 && dir == "incoming") existing.unread + 1 else existing.unread
                    )
                } else {
                    conversations[address] = SmsConversation(
                        id = id,
                        contact = contactName,
                        number = address,
                        avatar = contactName.first().uppercase(),
                        color = colorFor(address),
                        unread = if (read == 0 && dir == "incoming") 1 else 0,
                        messages = listOf(msg)
                    )
                }
            }
        }

        return conversations.values
            .sortedByDescending { it.messages.maxOf { m -> m.timestamp } }
            .take(50)
    }

    // ── Call Logs ────────────────────────────────────────────────────────────
    fun getCallLogs(): List<CallEntry> {
        val cr = context.contentResolver
        val calls = mutableListOf<CallEntry>()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )

        val cursor = cr.query(
            CallLog.Calls.CONTENT_URI, projection,
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT 100"
        )

        cursor?.use { c ->
            val idxNumber = c.getColumnIndex(CallLog.Calls.NUMBER)
            val idxName = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val idxType = c.getColumnIndex(CallLog.Calls.TYPE)
            val idxDuration = c.getColumnIndex(CallLog.Calls.DURATION)
            val idxDate = c.getColumnIndex(CallLog.Calls.DATE)

            while (c.moveToNext()) {
                val number = c.getString(idxNumber) ?: ""
                val name = c.getString(idxName)?.takeIf { it.isNotBlank() } ?: number
                val type = c.getInt(idxType)
                val duration = c.getLong(idxDuration)
                val date = c.getLong(idxDate)

                val callType = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    else -> "incoming"
                }

                calls.add(
                    CallEntry(
                        type = callType,
                        contact = name,
                        number = number,
                        duration = if (callType == "missed") "—" else formatDuration(duration),
                        time = formatDate(date)
                    )
                )
            }
        }

        return calls
    }

    // ── Files ─────────────────────────────────────────────────────────────────
    fun getFiles(relativePath: String): List<FileEntry> {
        val base = if (relativePath.isBlank() || relativePath == "Internal Storage") {
            Environment.getExternalStorageDirectory()
        } else {
            File(Environment.getExternalStorageDirectory(), relativePath)
        }

        if (!base.exists() || !base.isDirectory) return emptyList()

        return base.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
            ?.map { f ->
                if (f.isDirectory) {
                    FileEntry(
                        name = f.name,
                        type = "folder",
                        modified = formatDate(f.lastModified()),
                        items = f.listFiles()?.size ?: 0,
                        path = f.absolutePath
                    )
                } else {
                    FileEntry(
                        name = f.name,
                        type = mimeTypeFor(f.extension),
                        size = formatSize(f.length()),
                        modified = formatDate(f.lastModified()),
                        path = f.absolutePath
                    )
                }
            } ?: emptyList()
    }

    fun getFileByPath(absolutePath: String): File? {
        val f = File(absolutePath)
        return if (f.exists() && f.isFile) f else null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun resolveContact(cr: ContentResolver, number: String): String {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val cursor = cr.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) c.getString(0) else number
            } ?: number
        } catch (e: Exception) { number }
    }

    private fun formatDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val sdf = when {
            diff < 60_000 -> return "Just now"
            diff < 3_600_000 -> return "${diff / 60_000} min ago"
            diff < 86_400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault())
            diff < 7 * 86_400_000 -> SimpleDateFormat("EEE h:mm a", Locale.getDefault())
            else -> SimpleDateFormat("MMM d", Locale.getDefault())
        }
        return sdf.format(Date(timestamp))
    }

    private fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }

    private fun mimeTypeFor(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> "image"
        "mp4", "mkv", "avi", "mov", "3gp" -> "video"
        "mp3", "aac", "flac", "ogg", "wav", "m4a" -> "audio"
        "pdf" -> "pdf"
        "doc", "docx" -> "doc"
        "xls", "xlsx", "csv" -> "spreadsheet"
        "ppt", "pptx" -> "presentation"
        "apk" -> "apk"
        "zip", "tar", "gz", "rar" -> "archive"
        "txt", "md" -> "text"
        else -> "file"
    }

    private val COLORS = listOf(
        "#ec4899", "#3b82f6", "#10b981", "#f59e0b",
        "#8b5cf6", "#ef4444", "#06b6d4", "#6366f1"
    )

    private fun colorFor(key: String): String = COLORS[Math.abs(key.hashCode()) % COLORS.size]
}
