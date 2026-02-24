package com.syncbridge.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.syncbridge.app.service.BridgeServerService
import com.syncbridge.app.ui.SyncBridgeApp
import com.syncbridge.app.ui.theme.SyncBridgeTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // Required permissions
    private val PERMISSIONS = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        viewModel.onPermissionsResult(granted)
        if (granted) startBridgeService()
    }

    // Receiver for server address broadcast
    private val serverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BridgeServerService.EXTRA_ADDRESS) ?: return
            viewModel.onServerStarted(address)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Register broadcast receiver for server address
        registerReceiver(
            serverReceiver,
            IntentFilter(BridgeServerService.ACTION_SERVER_STARTED),
            Context.RECEIVER_NOT_EXPORTED
        )

        setContent {
            SyncBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SyncBridgeApp(
                        viewModel = viewModel,
                        onRequestPermissions = { requestPermissions() },
                        onRequestNotificationAccess = { openNotificationAccess() },
                        onStartServer = { startBridgeService() },
                        onStopServer = { stopBridgeService() }
                    )
                }
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.onPermissionsResult(true)
            startBridgeService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(PERMISSIONS)
    }

    private fun startBridgeService() {
        ContextCompat.startForegroundService(
            this, Intent(this, BridgeServerService::class.java)
        )
    }

    private fun stopBridgeService() {
        stopService(Intent(this, BridgeServerService::class.java))
        viewModel.onServerStopped()
    }

    private fun openNotificationAccess() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(serverReceiver) }
        super.onDestroy()
    }
}
