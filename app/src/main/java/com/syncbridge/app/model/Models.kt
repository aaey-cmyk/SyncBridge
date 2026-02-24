package com.syncbridge.app.model

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val device: DeviceInfo)

// ── Device ────────────────────────────────────────────
data class DeviceInfo(
    val name: String,
    val os: String,
    val battery: Int,
    val signal: String,
    val carrier: String
)

// ── SMS ───────────────────────────────────────────────
data class SmsConversation(
    val id: Long,
    val contact: String,
    val number: String,
    val avatar: String,
    val color: String,
    val unread: Int,
    val messages: List<SmsMessage>
)

data class SmsMessage(
    val dir: String,        // "incoming" | "outgoing"
    val text: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SmsListResponse(val conversations: List<SmsConversation>)

// ── Calls ─────────────────────────────────────────────
data class CallEntry(
    val type: String,       // "incoming" | "outgoing" | "missed" | "live"
    val contact: String,
    val number: String,
    val duration: String,
    val time: String,
    @SerializedName("isLive") val isLive: Boolean = false
)

data class CallListResponse(val calls: List<CallEntry>)

// ── Files ─────────────────────────────────────────────
data class FileEntry(
    val name: String,
    val type: String,       // "folder" | "image" | "video" | "pdf" | "doc" | "audio" | "apk"
    val size: String? = null,
    val modified: String,
    val items: Int? = null, // for folders
    val path: String = ""
)

data class FileListResponse(val files: List<FileEntry>)

// ── Notifications ─────────────────────────────────────
data class NotifEntry(
    val id: Int,
    val app: String,
    val icon: String,
    val color: String,
    val title: String,
    val body: String,
    val time: String,
    val read: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class NotifListResponse(val notifications: List<NotifEntry>)

// ── WebSocket Events ──────────────────────────────────
data class WsEvent(
    val type: String,        // "sms" | "call" | "notification" | "battery" | "ping"
    val from: String? = null,
    val text: String? = null,
    val contact: String? = null,
    val status: String? = null,
    val duration: String? = null,
    val appName: String? = null,
    val appIcon: String? = null,
    val title: String? = null,
    val body: String? = null,
    val level: Int? = null   // battery level
)

// ── Server Settings ───────────────────────────────────
data class ServerSettings(
    val port: Int = 8080,
    val username: String = "admin",
    val password: String = "syncbridge",
    val autoStart: Boolean = true,
    val wifiOnly: Boolean = true
)
