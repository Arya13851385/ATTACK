package com.localnet.emergency.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.localnet.emergency.audio.AlertAudioManager
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.model.ConnectionState
import com.localnet.emergency.notification.NotificationHelper
import com.localnet.emergency.repository.AlertRepository
import com.localnet.emergency.vibration.AlertVibrationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the LAN WebSocket connection alive whether
 * the app UI is backgrounded, minimized, or the task has been swiped away.
 * It is the only long-lived component in the app; MainActivity and
 * FullScreenAlertActivity are just views onto AlertRepository's state.
 */
@AndroidEntryPoint
class AlertConnectionService : Service() {

    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var audioManager: AlertAudioManager
    @Inject lateinit var vibrationManager: AlertVibrationManager

    private var serviceJob: Job? = null
    private lateinit var serviceScope: CoroutineScope
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        serviceJob = SupervisorJob()
        serviceScope = CoroutineScope(serviceJob!!)
        notificationHelper.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            notificationHelper.buildServiceNotification("در حال اتصال به سرور محلی...")
        )

        serviceScope.launch { alertRepository.startConnecting() }

        alertRepository.connectionState
            .onEach { state -> notificationHelper.buildServiceNotification(describeConnection(state)) }
            .launchIn(serviceScope)

        alertRepository.alertEvents
            .onEach { message -> handleIncomingAlert(AlertLevel.fromWire(message.level)) }
            .launchIn(serviceScope)

        return START_STICKY
    }

    private fun handleIncomingAlert(level: AlertLevel) {
        val state = alertRepository.alertState.value
        notificationHelper.postAlertNotification(state)
        audioManager.playForLevel(level)
        vibrationManager.vibrateForLevel(level)

        if (level == AlertLevel.RED) {
            acquireWakeLockBriefly()
        } else if (level == AlertLevel.WHITE) {
            audioManager.stop()
            vibrationManager.cancel()
            notificationHelper.clearAlertNotification()
        }
    }

    /** Turns the screen on for RED alerts. Held only briefly; the full-screen
     * activity itself keeps the screen on afterward via window flags. */
    private fun acquireWakeLockBriefly() {
        wakeLock?.let { if (it.isHeld) it.release() }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "EmergencyAlert::RedAlertWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10_000L)
        }
    }

    private fun describeConnection(state: ConnectionState): String = when (state) {
        is ConnectionState.Connected -> "متصل به ${state.host}:${state.port}"
        is ConnectionState.Connecting -> "در حال اتصال به ${state.host}:${state.port}"
        is ConnectionState.Discovering -> "در حال جستجوی سرور در شبکه محلی..."
        is ConnectionState.Reconnecting -> "تلاش مجدد برای اتصال (${state.attempt})..."
        is ConnectionState.Failed -> "اتصال ناموفق: ${state.reason}"
        ConnectionState.Disconnected -> "قطع شده — سرور یافت نشد"
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        audioManager.stop()
        vibrationManager.cancel()
        alertRepository.stopConnecting()
        serviceJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
