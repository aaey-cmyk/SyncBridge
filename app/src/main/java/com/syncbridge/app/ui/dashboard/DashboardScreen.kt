package com.syncbridge.app.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbridge.app.AppState
import com.syncbridge.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appState: AppState,
    onRequestPermissions: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Bar ───────────────────────────────────────────────────────────
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text("SyncBridge", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardDark)
        )

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── Server Status Card ────────────────────────────────────────────
            ServerStatusCard(
                appState = appState,
                onStart = onStartServer,
                onStop = onStopServer,
                onCopyAddress = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("SyncBridge URL", "http://${appState.serverAddress}"))
                },
                onOpenBrowser = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("http://${appState.serverAddress}"))
                    )
                }
            )

            // ── Permissions Card ─────────────────────────────────────────────
            if (!appState.permissionsGranted) {
                PermissionCard(
                    onRequest = onRequestPermissions
                )
            }

            // ── Notification Access Card ─────────────────────────────────────
            if (!appState.notificationAccessGranted) {
                NotifAccessCard(onRequest = onRequestNotificationAccess)
            }

            // ── Feature Grid ─────────────────────────────────────────────────
            Text("What's Synced", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 12.sp,
                letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureChip(Icons.Default.Sms, "SMS Messages", Blue, Modifier.weight(1f))
                    FeatureChip(Icons.Default.Phone, "Call Logs", Green, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureChip(Icons.Default.Folder, "File Browser", Orange, Modifier.weight(1f))
                    FeatureChip(Icons.Default.Notifications, "Notifications", Purple, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureChip(Icons.Default.Wifi, "WebSocket Live", Red, Modifier.weight(1f))
                    FeatureChip(Icons.Default.Download, "File Download", Blue, Modifier.weight(1f))
                }
            }

            // ── How to Connect ────────────────────────────────────────────────
            HowToConnectCard()
        }
    }
}

// ── Server Status Card ────────────────────────────────────────────────────────
@Composable
fun ServerStatusCard(
    appState: AppState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (appState.serverRunning) Green.copy(alpha = pulseAlpha)
                            else Red
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (appState.serverRunning) "Server Running" else "Server Stopped",
                    fontWeight = FontWeight.SemiBold,
                    color = if (appState.serverRunning) Green else Red,
                    fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${appState.connectedClients} client${if (appState.connectedClients != 1) "s" else ""}",
                    color = TextMuted, fontSize = 12.sp
                )
            }

            if (appState.serverRunning && appState.serverAddress.isNotBlank()) {
                // Address display
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connect your browser to:", color = TextMuted, fontSize = 12.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgDark,
                        border = BorderStroke(1.dp, Blue.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "http://${appState.serverAddress}",
                                color = Blue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onCopyAddress, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (appState.serverRunning) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Red),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop Server")
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Server")
                    }
                }
            }
        }
    }
}

// ── Permission Card ───────────────────────────────────────────────────────────
@Composable
fun PermissionCard(onRequest: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Orange, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Permissions Required", fontWeight = FontWeight.SemiBold, color = Orange)
                Text("SMS, Calls, Storage access needed to sync data", color = TextMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onRequest) { Text("Grant", color = Blue) }
        }
    }
}

// ── Notification Access Card ──────────────────────────────────────────────────
@Composable
fun NotifAccessCard(onRequest: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Orange.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Orange.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Orange, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Notification Access", fontWeight = FontWeight.SemiBold, color = Orange)
                Text("Enable to sync live notifications to your browser", color = TextMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onRequest) { Text("Enable", color = Blue) }
        }
    }
}

// ── Feature Chip ──────────────────────────────────────────────────────────────
@Composable
fun FeatureChip(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── How to Connect ────────────────────────────────────────────────────────────
@Composable
fun HowToConnectCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How to Connect", fontWeight = FontWeight.Bold, fontSize = 15.sp)

            val steps = listOf(
                "1" to "Make sure your phone and Mac are on the same WiFi network",
                "2" to "Tap Start Server above — note the IP address shown",
                "3" to "Open Chrome on your Mac and navigate to the displayed URL",
                "4" to "Enter your credentials (default: admin / syncbridge)",
                "5" to "Your phone data will stream live to the browser!"
            )

            steps.forEach { (num, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(num, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text, color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
