package com.localnet.emergency.repository

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.model.AlertMessage
import com.localnet.emergency.model.AlertState
import com.localnet.emergency.model.ConnectionState
import com.localnet.emergency.model.MessageType
import com.localnet.emergency.network.AlertWebSocketClient
import com.localnet.emergency.network.NsdDiscoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "emergency_alert_prefs")

/**
 * Single source of truth for the current alert state and connection status.
 * The foreground service and the Compose UI both observe this repository so
 * they always agree on what is currently happening, regardless of which
 * component (service vs. activity) is alive at any given moment.
 */
@Singleton
class AlertRepository @Inject constructor(
    private val context: Context,
    private val webSocketClient: AlertWebSocketClient,
    private val nsdDiscoveryManager: NsdDiscoveryManager
) {
    companion object {
        private val KEY_MANUAL_HOST = stringPreferencesKey("manual_host")
        private val KEY_MANUAL_PORT = longPreferencesKey("manual_port")
        private const val DISCOVERY_TIMEOUT_MS = 6_000L
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _alertState = MutableStateFlow(AlertState())
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()

    // Re-published locally so we never call asSharedFlow() on an already
    // read-only SharedFlow (that extension only exists on MutableSharedFlow).
    private val _alertEvents = MutableSharedFlow<AlertMessage>(extraBufferCapacity = 16)
    val alertEvents: SharedFlow<AlertMessage> = _alertEvents.asSharedFlow()

    val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device-${System.currentTimeMillis()}"
    }

    init {
        webSocketClient.configureIdentity(deviceId, android.os.Build.MODEL ?: "Android")
        repositoryScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                if (message.type == MessageType.ALERT || message.type == MessageType.STATE_SYNC) {
                    _alertState.value = AlertState(
                        level = AlertLevel.fromWire(message.level),
                        note = message.note,
                        room = message.room,
                        senderName = message.senderName,
                        updatedAtMillis = message.timestamp
                    )
                    _alertEvents.tryEmit(message)
                }
            }
        }
    }

    /**
     * Starts the connection pipeline: try a saved manual override first (for
     * networks that block multicast), otherwise auto-discover the broker via
     * mDNS/NSD with a bounded timeout, then fall back to manual entry.
     */
    suspend fun startConnecting() {
        val manual = getManualOverride()
        if (manual != null) {
            webSocketClient.connect(manual.first, manual.second)
            return
        }

        val discovered = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            nsdDiscoveryManager.discoverBrokers().first()
        }

        if (discovered != null) {
            webSocketClient.connect(discovered.host, discovered.port)
        } else {
            // No broker found automatically; UI should prompt for manual host:port.
            webSocketClient.disconnect()
        }
    }

    fun connectManually(host: String, port: Int) {
        repositoryScope.launch { saveManualOverride(host, port) }
        webSocketClient.connect(host, port)
    }

    fun stopConnecting() {
        webSocketClient.disconnect()
    }

    /** Triggers a new alert level LAN-wide. This is the only write path into the system. */
    fun triggerAlert(level: AlertLevel, note: String?, room: String?) {
        val trimmedNote = note?.trim()?.take(200)
        val trimmedRoom = room?.trim()?.take(80)
        webSocketClient.sendAlert(level.wireValue, trimmedNote, trimmedRoom)
        // Optimistically reflect locally so the triggering device's UI updates
        // immediately even before the broker's broadcast round-trips back.
        _alertState.value = AlertState(
            level = level,
            note = trimmedNote,
            room = trimmedRoom,
            senderName = "شما"
        )
    }

    private suspend fun getManualOverride(): Pair<String, Int>? {
        val prefs = context.dataStore.data.first()
        val host = prefs[KEY_MANUAL_HOST] ?: return null
        val port = prefs[KEY_MANUAL_PORT]?.toInt() ?: return null
        return host to port
    }

    private suspend fun saveManualOverride(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MANUAL_HOST] = host
            prefs[KEY_MANUAL_PORT] = port.toLong()
        }
    }

    suspend fun clearManualOverride() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MANUAL_HOST)
            prefs.remove(KEY_MANUAL_PORT)
        }
    }
}
