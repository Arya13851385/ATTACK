package com.localnet.emergency.network

import android.util.Log
import com.localnet.emergency.model.AlertMessage
import com.localnet.emergency.model.ConnectionState
import com.localnet.emergency.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Persistent LAN WebSocket client with automatic reconnection (capped
 * exponential backoff) and an application-level heartbeat, independent of
 * any cloud push service. This is the single source of truth for the
 * connection to the on-premise alert broker.
 */
@Singleton
class AlertWebSocketClient @Inject constructor() {

    companion object {
        private const val TAG = "AlertWebSocketClient"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val HEARTBEAT_TIMEOUT_MS = 25_000L
        private const val BASE_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Short handshake/ping timeouts keep the < 500ms broadcast latency target
    // achievable: we never block the pipe waiting on a slow read.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSockets: no read timeout, rely on our own heartbeat
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var supervisorScope: CoroutineScope? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastPongReceivedAt: Long = 0L
    private var reconnectAttempts: Int = 0
    private var currentHost: String = ""
    private var currentPort: Int = 0
    private var manuallyStopped: Boolean = false
    private var deviceId: String = "unknown-device"
    private var deviceName: String = "Unknown"

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<AlertMessage>(extraBufferCapacity = 16)
    val incomingMessages: SharedFlow<AlertMessage> = _incomingMessages.asSharedFlow()

    fun configureIdentity(deviceId: String, deviceName: String) {
        this.deviceId = deviceId
        this.deviceName = deviceName
    }

    fun connect(host: String, port: Int) {
        manuallyStopped = false
        currentHost = host
        currentPort = port
        reconnectAttempts = 0
        openSocket()
    }

    fun disconnect() {
        manuallyStopped = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Client stopping")
        webSocket = null
        supervisorScope?.cancel()
        supervisorScope = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /** Sends a locally-triggered alert transition to the broker for LAN-wide broadcast. */
    fun sendAlert(level: String, note: String?, room: String?) {
        val message = AlertMessage(
            type = MessageType.ALERT,
            level = level,
            note = note,
            room = room,
            senderId = deviceId,
            senderName = deviceName
        )
        send(message)
    }

    private fun send(message: AlertMessage): Boolean {
        val payload = json.encodeToString(AlertMessage.serializer(), message)
        val sent = webSocket?.send(payload) ?: false
        if (!sent) Log.w(TAG, "send() failed, socket not open")
        return sent
    }

    private fun openSocket() {
        val scope = CoroutineScope(SupervisorJob())
        supervisorScope = scope
        _connectionState.value = ConnectionState.Connecting(currentHost, currentPort)

        val request = Request.Builder()
            .url("ws://$currentHost:$currentPort/alerts")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $currentHost:$currentPort")
                reconnectAttempts = 0
                lastPongReceivedAt = System.currentTimeMillis()
                _connectionState.value = ConnectionState.Connected(currentHost, currentPort)
                send(AlertMessage(type = MessageType.HELLO, senderId = deviceId, senderName = deviceName))
                startHeartbeat(scope)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                lastPongReceivedAt = System.currentTimeMillis()
                runCatching {
                    json.decodeFromString(AlertMessage.serializer(), text)
                }.onSuccess { parsed ->
                    if (parsed.type != MessageType.HEARTBEAT) {
                        _incomingMessages.tryEmit(parsed)
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to parse incoming message: ${it.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: $code $reason")
                heartbeatJob?.cancel()
                if (!manuallyStopped) scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Connection failure: ${t.message}")
                heartbeatJob?.cancel()
                _connectionState.value = ConnectionState.Failed(t.message ?: "unknown error")
                if (!manuallyStopped) scheduleReconnect()
            }
        })
    }

    private fun startHeartbeat(scope: CoroutineScope) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val silence = System.currentTimeMillis() - lastPongReceivedAt
                if (silence > HEARTBEAT_TIMEOUT_MS) {
                    Log.w(TAG, "Heartbeat timeout after ${silence}ms — forcing reconnect")
                    webSocket?.close(1000, null)
                    webSocket = null
                    scheduleReconnect()
                    break
                }
                send(AlertMessage(type = MessageType.HEARTBEAT, senderId = deviceId))
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectAttempts += 1
        val delayMs = min(
            BASE_RECONNECT_DELAY_MS * 2.0.pow(reconnectAttempts - 1).toLong(),
            MAX_RECONNECT_DELAY_MS
        )
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts, delayMs)
        val scope = supervisorScope ?: CoroutineScope(SupervisorJob()).also { supervisorScope = it }
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!manuallyStopped) openSocket()
        }
    }
}
