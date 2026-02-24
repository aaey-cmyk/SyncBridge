# SyncBridge Android

The Android companion app that turns your phone into a local HTTP + WebSocket server,
allowing the SyncBridge Chrome web app to read your SMS, calls, files, and notifications over LAN.

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Android Device (Phone)                   │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              BridgeServerService (Foreground)        │ │
│  │                                                     │ │
│  │   HTTP Server :8080        WebSocket /ws            │ │
│  │   ┌──────────────┐        ┌──────────────┐          │ │
│  │   │ POST /login  │        │ Live Events  │          │ │
│  │   │ GET  /sms    │        │ - new SMS    │          │ │
│  │   │ GET  /calls  │        │ - call state │          │ │
│  │   │ GET  /files  │        │ - notifs     │          │ │
│  │   │ GET  /download│        │ - battery   │          │ │
│  │   │ GET  /notifs │        └──────────────┘          │ │
│  │   └──────────────┘                                  │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌────────────┐  ┌──────────────┐  ┌───────────────┐   │
│  │SmsReceiver │  │NotifListener │  │DataRepository │   │
│  │(broadcast) │  │(system hook) │  │(ContentResolver│  │
│  └────────────┘  └──────────────┘  └───────────────┘   │
└──────────────────────────────────────────────────────────┘
                          │  WiFi LAN
                          ▼
┌──────────────────────────────────────────────────────────┐
│               Mac / PC – Chrome Browser                   │
│                                                          │
│   http://192.168.x.x:8080   (SyncBridge Web App)        │
└──────────────────────────────────────────────────────────┘
```

## Setup

### 1. Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 34
- Kotlin 1.9.x
- Physical Android device (emulator won't have real SMS/calls)

### 2. Build & Install
```bash
git clone <repo>
cd SyncBridgeAndroid
./gradlew installDebug
```

Or open in Android Studio → Run.

### 3. First Launch
1. Grant all requested permissions (SMS, Calls, Storage, Contacts)
2. Grant Notification Listener access (Settings → Notification access → SyncBridge)
3. Tap **Start Server**
4. Note the displayed IP:port (e.g. `192.168.1.100:8080`)

### 4. Connect Chrome
1. Open Chrome on your Mac (same WiFi network)
2. Open the SyncBridge web app
3. Enter: Server URL = `http://192.168.1.100:8080`
4. Username: `admin`, Password: `syncbridge` (configurable in Settings)
5. Click Connect — live data streams immediately!

## Default Credentials
| Setting  | Default      |
|----------|-------------|
| Username | `admin`      |
| Password | `syncbridge` |
| Port     | `8080`       |

Change these in the app's Settings tab.

## API Reference

### Authentication
```http
POST /api/auth/login
Content-Type: application/json

{"username": "admin", "password": "syncbridge"}
→ {"token": "abc123...", "device": {...}}
```

### SMS
```http
GET /api/sms
Authorization: Bearer <token>
→ {"conversations": [...]}
```

### Calls
```http
GET /api/calls
Authorization: Bearer <token>
→ {"calls": [...]}
```

### Files
```http
GET /api/files?path=DCIM/Camera
Authorization: Bearer <token>
→ {"files": [...]}
```

### Download
```http
GET /api/download?file=/storage/emulated/0/DCIM/Camera/IMG_001.jpg
Authorization: Bearer <token>
→ (binary stream)
```

### Notifications
```http
GET /api/notifications
Authorization: Bearer <token>
→ {"notifications": [...]}
```

### WebSocket
```
WS ws://192.168.x.x:8080/ws

Server pushes events:
{"type":"sms",  "from":"+1555...", "text":"Hello!"}
{"type":"call", "contact":"Mom", "status":"incoming"}
{"type":"notification", "appName":"WhatsApp", "title":"John", "body":"Hey!"}
{"type":"battery", "level":72}
{"type":"ping"}
```

## Permissions Explained
| Permission | Why |
|---|---|
| `READ_SMS` | Read SMS conversations |
| `RECEIVE_SMS` | Get new SMS in real-time |
| `READ_CALL_LOG` | Read call history |
| `READ_CONTACTS` | Resolve phone numbers to names |
| `READ_MEDIA_*` | Browse photos/videos/audio |
| `FOREGROUND_SERVICE` | Keep server running in background |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Capture all notifications |
| `INTERNET` | Run local HTTP server |
| `ACCESS_WIFI_STATE` | Get device IP address |

## Project Structure
```
app/src/main/
├── java/com/syncbridge/app/
│   ├── SyncBridgeApp.kt          # Application class, notification channels
│   ├── MainActivity.kt           # Entry point, permission handling
│   ├── MainViewModel.kt          # UI state
│   ├── api/
│   │   └── DataRepository.kt     # Reads SMS, calls, files via ContentResolver
│   ├── model/
│   │   └── Models.kt             # Data classes for all API types
│   ├── service/
│   │   ├── BridgeServerService.kt    # HTTP + WebSocket server (foreground service)
│   │   ├── SmsReceiver.kt            # Incoming SMS broadcast receiver
│   │   ├── NotificationListenerService.kt  # Captures all notifications
│   │   └── BootReceiver.kt           # Auto-start on device boot
│   └── ui/
│       ├── SyncBridgeApp.kt          # Navigation host
│       ├── dashboard/
│       │   └── DashboardScreen.kt    # Server control, status, how-to
│       ├── settings/
│       │   └── SettingsScreen.kt     # Port, credentials, toggles
│       └── theme/
│           └── Theme.kt              # Dark theme matching web app
└── res/
    ├── values/{colors,strings,themes}.xml
    └── xml/file_paths.xml
```
