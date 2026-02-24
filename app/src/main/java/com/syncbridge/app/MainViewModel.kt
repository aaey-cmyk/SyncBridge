package com.syncbridge.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppState(
    val permissionsGranted: Boolean = false,
    val serverRunning: Boolean = false,
    val serverAddress: String = "",
    val connectedClients: Int = 0,
    val notificationAccessGranted: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    fun onPermissionsResult(granted: Boolean) {
        _state.value = _state.value.copy(permissionsGranted = granted)
    }

    fun onServerStarted(address: String) {
        _state.value = _state.value.copy(serverRunning = true, serverAddress = address)
    }

    fun onServerStopped() {
        _state.value = _state.value.copy(serverRunning = false, serverAddress = "")
    }

    fun onClientConnected() {
        _state.value = _state.value.copy(connectedClients = _state.value.connectedClients + 1)
    }

    fun onClientDisconnected() {
        _state.value = _state.value.copy(
            connectedClients = maxOf(0, _state.value.connectedClients - 1)
        )
    }

    fun onNotificationAccessChanged(granted: Boolean) {
        _state.value = _state.value.copy(notificationAccessGranted = granted)
    }
}
