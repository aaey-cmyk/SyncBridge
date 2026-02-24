package com.syncbridge.app.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncbridge.app.MainViewModel
import com.syncbridge.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var port by remember { mutableStateOf(prefs.getInt("port", 8080).toString()) }
    var username by remember { mutableStateOf(prefs.getString("username", "admin") ?: "admin") }
    var password by remember { mutableStateOf(prefs.getString("password", "syncbridge") ?: "syncbridge") }
    var autoStart by remember { mutableStateOf(prefs.getBoolean("autoStart", true)) }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifiOnly", true)) }
    var showPassword by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardDark)
        )

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── Server Settings ───────────────────────────────────────────────
            SettingsSection("Server Configuration", Icons.Default.Dns) {

                SettingsTextField(
                    label = "Port",
                    value = port,
                    onValueChange = { port = it; saved = false },
                    keyboardType = KeyboardType.Number,
                    placeholder = "8080"
                )

                SettingsTextField(
                    label = "Username",
                    value = username,
                    onValueChange = { username = it; saved = false },
                    placeholder = "admin"
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; saved = false },
                    label = { Text("Password") },
                    placeholder = { Text("syncbridge") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
            }

            // ── Behavior ─────────────────────────────────────────────────────
            SettingsSection("Behavior", Icons.Default.Tune) {

                SettingsToggle(
                    title = "Auto-start on boot",
                    subtitle = "Start the bridge server automatically when phone boots",
                    checked = autoStart,
                    onCheckedChange = { autoStart = it; saved = false }
                )

                HorizontalDivider(color = BorderDark)

                SettingsToggle(
                    title = "WiFi only",
                    subtitle = "Only run the server when connected to WiFi",
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it; saved = false }
                )
            }

            // ── Save ──────────────────────────────────────────────────────────
            Button(
                onClick = {
                    prefs.edit()
                        .putInt("port", port.toIntOrNull() ?: 8080)
                        .putString("username", username)
                        .putString("password", password)
                        .putBoolean("autoStart", autoStart)
                        .putBoolean("wifiOnly", wifiOnly)
                        .apply()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (saved) Icons.Default.Check else Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (saved) "Saved!" else "Save Settings", fontWeight = FontWeight.SemiBold)
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection("About", Icons.Default.Info) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", color = TextMuted, fontSize = 14.sp)
                    Text("1.0.0", color = TextPrimary, fontSize = 14.sp)
                }
                HorizontalDivider(color = BorderDark)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Protocol", color = TextMuted, fontSize = 14.sp)
                    Text("HTTP + WebSocket", color = TextPrimary, fontSize = 14.sp)
                }
                HorizontalDivider(color = BorderDark)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Min Android", color = TextMuted, fontSize = 14.sp)
                    Text("Android 8.0 (API 26)", color = TextPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Reusable Composables ──────────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMuted,
                    letterSpacing = 0.5.sp)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors()
    )
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Blue)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Blue,
    unfocusedBorderColor = BorderDark,
    focusedLabelColor = Blue,
    unfocusedLabelColor = TextMuted,
    cursorColor = Blue,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
