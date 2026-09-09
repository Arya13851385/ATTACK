package com.localnet.emergency.model

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Discovering : ConnectionState()
    data class Connecting(val host: String, val port: Int) : ConnectionState()
    data class Connected(val host: String, val port: Int) : ConnectionState()
    data class Reconnecting(val attempt: Int, val nextRetryInMillis: Long) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}
