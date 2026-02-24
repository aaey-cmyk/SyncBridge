package com.syncbridge.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.syncbridge.app.MainActivity
import com.syncbridge.app.R
import com.syncbridge.app.SyncBridgeApp
import com.syncbridge.app.api.DataRepository
import com.syncbridge.app.model.*
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Foreground service that runs a lightweight HTTP + WebSocket server directly
 * on the Android device. The Chrome web app connects to this server over LAN.
 *
 * Endpoints:
 *   POST /api/auth/login
 *   GET  /api/sms
 *   GET  /api/calls
 *   GET  /api/files?path=...
 *   GET  /api/download?file=...
 *   GET  /api/notifications
 *   WS   /ws  (WebSocket upgrade)
 */
class BridgeServerService : Service() {

    companion object {
        private const val TAG = "BridgeServer"
        const val ACTION_PUSH_EVENT = "com.syncbridge.PUSH_EVENT"
        const val EXTRA_EVENT_JSON = "event_json"
        private const val NOTIF_ID = 1001

        // Broadcast from this service so the UI can read the IP:port
        const val ACTION_SERVER_STARTED = "com.syncbridge.SERVER_STARTED"
        const val EXTRA_ADDRESS = "address"

        fun pushEvent(ctx: Context, event: WsEvent) {
            val gson = Gson()
            ctx.startService(Intent(ctx, BridgeServerService::class.java).apply {
                action = ACTION_PUSH_EVENT
                putExtra(EXTRA_EVENT_JSON, gson.toJson(event))
            })
        }
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val wsClients = CopyOnWriteArrayList<WebSocketClient>()
    private lateinit var repo: DataRepository
    private var serverSocket: ServerSocket? = null
    private var port = 8080
    private var authToken: String? = null

    // Stored notification list (populated by NotificationListenerService)
    val notifications = CopyOnWriteArrayList<NotifEntry>()

    override fun onCreate() {
        super.onCreate()
        repo = DataRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PUSH_EVENT -> {
                val json = intent.getStringExtra(EXTRA_EVENT_JSON) ?: return START_STICKY
                broadcastToWsClients(json)
            }
            else -> {
                startForeground(NOTIF_ID, buildForegroundNotification("Starting…"))
                scope.launch { startServer() }
            }
        }
        return START_STICKY
    }

    private suspend fun startServer() {
        try {
            serverSocket = ServerSocket(port)
            val address = getLocalIp()
            Log.i(TAG, "Server started on $address:$port")

            sendBroadcast(Intent(ACTION_SERVER_STARTED).apply {
                putExtra(EXTRA_ADDRESS, "$address:$port")
                setPackage(packageName)
            })

            updateNotification("Running on http://$address:$port")

            while (!serverSocket!!.isClosed) {
                val socket = serverSocket!!.accept()
                scope.launch { handleConnection(socket) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server error: ${e.message}")
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) { socket.close(); return }

            val method = parts[0]
            val rawPath = parts[1]
            val (path, queryString) = parsePathQuery(rawPath)
            val params = parseQuery(queryString)

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = input.readLine()
            while (!line.isNullOrBlank()) {
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                line = input.readLine()
            }

            // WebSocket upgrade?
            if (headers["upgrade"]?.lowercase() == "websocket") {
                handleWebSocket(socket, headers)
                return
            }

            // Read body for POST
            var body = ""
            if (method == "POST") {
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val buf = CharArray(len)
                input.read(buf, 0, len)
                body = String(buf)
            }

            // Auth check (skip for login endpoint)
            val token = headers["authorization"]?.removePrefix("Bearer ")
            val needsAuth = path != "/api/auth/login"
            if (needsAuth && token != authToken && authToken != null) {
                sendJson(socket, 401, """{"error":"Unauthorized"}""")
                return
            }

            // Route
            when {
                method == "POST" && path == "/api/auth/login" -> handleLogin(socket, body)
                method == "GET"  && path == "/api/sms"        -> handleSms(socket)
                method == "GET"  && path == "/api/calls"      -> handleCalls(socket)
                method == "GET"  && path == "/api/files"      -> handleFiles(socket, params["path"] ?: "")
                method == "GET"  && path == "/api/download"   -> handleDownload(socket, params["file"] ?: "")
                method == "GET"  && path == "/api/notifications" -> handleNotifications(socket)
                method == "GET"  && path == "/api/device"     -> handleDevice(socket)
                else -> sendJson(socket, 404, """{"error":"Not found"}""")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleLogin(socket: Socket, body: String) {
        val req = runCatching { gson.fromJson(body, LoginRequest::class.java) }.getOrNull()
            ?: run { sendJson(socket, 400, """{"error":"Bad request"}"""); return }

        // Simple credential check (configured in settings)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "admin")
        val pass = prefs.getString("password", "syncbridge")

        if (req.username == user && req.password == pass) {
            authToken = generateToken()
            val device = repo.getDeviceInfo()
            val resp = LoginResponse(authToken!!, device)
            sendJson(socket, 200, gson.toJson(resp))
        } else {
            sendJson(socket, 401, """{"error":"Invalid credentials"}""")
        }
    }

    private fun handleDevice(socket: Socket) {
        sendJson(socket, 200, gson.toJson(repo.getDeviceInfo()))
    }

    private fun handleSms(socket: Socket) {
        val convos = repo.getSmsConversations()
        sendJson(socket, 200, gson.toJson(SmsListResponse(convos)))
    }

    private fun handleCalls(socket: Socket) {
        val calls = repo.getCallLogs()
        sendJson(socket, 200, gson.toJson(CallListResponse(calls)))
    }

    private fun handleFiles(socket: Socket, path: String) {
        val files = repo.getFiles(URLDecoder.decode(path, "UTF-8"))
        sendJson(socket, 200, gson.toJson(FileListResponse(files)))
    }

    private fun handleDownload(socket: Socket, filePath: String) {
        val decoded = URLDecoder.decode(filePath, "UTF-8")
        val file = repo.getFileByPath(decoded)
        if (file == null) {
            sendJson(socket, 404, """{"error":"File not found"}""")
            return
        }

        val out = socket.getOutputStream()
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Content-Disposition: attachment; filename=\"${file.name}\"\r\n")
            append("Content-Length: ${file.length()}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.flush()
    }

    private fun handleNotifications(socket: Socket) {
        sendJson(socket, 200, gson.toJson(NotifListResponse(notifications.toList())))
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private suspend fun handleWebSocket(socket: Socket, headers: Map<String, String>) {
        val key = headers["sec-websocket-key"] ?: return
        val acceptKey = Base64.encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
            Base64.NO_WRAP
        )

        val out = socket.getOutputStream()
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"
        out.write(response.toByteArray())
        out.flush()

        val client = WebSocketClient(socket, out)
        wsClients.add(client)
        Log.i(TAG, "WS client connected. Total: ${wsClients.size}")

        // Send initial device info
        val ping = gson.toJson(WsEvent(type = "ping"))
        client.send(ping)

        try {
            val input = socket.getInputStream()
            while (!socket.isClosed) {
                val frame = readWsFrame(input) ?: break
                Log.d(TAG, "WS received: $frame")
                // Echo ping back as pong
                if (frame.startsWith("{\"type\":\"ping")) client.send(ping)
            }
        } catch (e: Exception) {
            Log.d(TAG, "WS client disconnected: ${e.message}")
        } finally {
            wsClients.remove(client)
            runCatching { socket.close() }
        }
    }

    private fun readWsFrame(input: InputStream): String? {
        return try {
            val b0 = input.read(); if (b0 < 0) return null
            val b1 = input.read(); if (b1 < 0) return null
            val masked = (b1 and 0x80) != 0
            var payloadLen = (b1 and 0x7F).toLong()
            if (payloadLen == 126L) {
                payloadLen = ((input.read() shl 8) or input.read()).toLong()
            } else if (payloadLen == 127L) {
                payloadLen = 0
                repeat(8) { payloadLen = (payloadLen shl 8) or input.read().toLong() }
            }
            val mask = if (masked) ByteArray(4) { input.read().toByte() } else ByteArray(0)
            val payload = ByteArray(payloadLen.toInt())
            var read = 0
            while (read < payloadLen) read += input.read(payload, read, (payloadLen - read).toInt())
            if (masked) payload.indices.forEach { i -> payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
            String(payload)
        } catch (e: Exception) { null }
    }

    fun broadcastToWsClients(json: String) {
        val dead = mutableListOf<WebSocketClient>()
        wsClients.forEach { client ->
            if (!client.send(json)) dead.add(client)
        }
        wsClients.removeAll(dead.toSet())
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private fun sendJson(socket: Socket, code: Int, json: String) {
        val status = when (code) {
            200 -> "200 OK"; 401 -> "401 Unauthorized"
            404 -> "404 Not Found"; else -> "$code Error"
        }
        val bytes = json.toByteArray()
        val response = "HTTP/1.1 $status\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Authorization, Content-Type\r\n" +
                "\r\n"
        try {
            socket.getOutputStream().write(response.toByteArray())
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        } catch (_: Exception) {}
    }

    private fun parsePathQuery(raw: String): Pair<String, String> {
        val qi = raw.indexOf('?')
        return if (qi < 0) Pair(raw, "") else Pair(raw.substring(0, qi), raw.substring(qi + 1))
    }

    private fun parseQuery(qs: String): Map<String, String> {
        if (qs.isBlank()) return emptyMap()
        return qs.split("&").mapNotNull {
            val eq = it.indexOf('='); if (eq < 0) null
            else Pair(it.substring(0, eq), URLDecoder.decode(it.substring(eq + 1), "UTF-8"))
        }.toMap()
    }

    private fun getLocalIp(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ip = wm.connectionInfo.ipAddress
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    private fun generateToken() = UUID.randomUUID().toString().replace("-", "")

    // ── Notification ─────────────────────────────────────────────────────────

    private fun buildForegroundNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, SyncBridgeApp.CHANNEL_SERVER)
            .setContentTitle("SyncBridge Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildForegroundNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        wsClients.forEach { runCatching { it.socket.close() } }
        wsClients.clear()
        runCatching { serverSocket?.close() }
        super.onDestroy()
    }
}

// ── WebSocket Client wrapper ──────────────────────────────────────────────────
class WebSocketClient(val socket: Socket, private val out: OutputStream) {
    fun send(text: String): Boolean {
        return try {
            val payload = text.toByteArray()
            val frame = buildWsFrame(payload)
            synchronized(out) { out.write(frame); out.flush() }
            true
        } catch (e: Exception) { false }
    }

    private fun buildWsFrame(payload: ByteArray): ByteArray {
        val len = payload.size
        val frame = ByteArrayOutputStream()
        frame.write(0x81) // FIN + text opcode
        when {
            len <= 125 -> frame.write(len)
            len <= 65535 -> { frame.write(126); frame.write(len shr 8); frame.write(len and 0xFF) }
            else -> {
                frame.write(127)
                repeat(8) { i -> frame.write((len shr (56 - 8 * i)) and 0xFF) }
            }
        }
        frame.write(payload)
        return frame.toByteArray()
    }
}
