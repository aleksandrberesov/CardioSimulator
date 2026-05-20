package com.example.cardiosimulator.network

sealed class TcpConnectionState {
    object Disconnected : TcpConnectionState()
    object Connecting : TcpConnectionState()
    object Connected : TcpConnectionState()
    data class Error(val message: String) : TcpConnectionState()
}
