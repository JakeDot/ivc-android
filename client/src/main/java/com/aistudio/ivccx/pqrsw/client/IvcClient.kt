package com.aistudio.ivccx.pqrsw.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IvcClient {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun connect(serverUrl: String = "https://IVC.cx", fallbackUrl: String = "irc://IVC.cx") {
        // TODO: Implement WebRTC and IRC connection logic
        _connectionState.value = ConnectionState.CONNECTING
        // Simulated connection
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
