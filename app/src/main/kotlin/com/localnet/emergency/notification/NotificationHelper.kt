package com.localnet.emergency.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.localnet.emergency.R
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.model.AlertState
import com.localnet.emergency.ui.FullScreenAlertActivity
import com.localnet.emergency.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_RED = "alert_channel_red"
        const val CHANNEL_YELLOW = "alert_channel_yellow"
        const val CHANNEL_WHITE = "alert_channel_white"
        const val CHANNEL_SERVICE = "alert_service_channel"

        const val NOTIFICATION_ID_ALERT = 1001
        const val NOTIFICATION_ID_SERVICE = 1002
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun createChannels() {
        val red = NotificationChannel(
            CHANNEL_RED, "هشدار قرمز (خطر فوری)", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "هشدارهای بحرانی که نیاز به واکنش فوری دارند"
            enableVibration(true)
            enableLights(true)
            setSound(rawUri(R.raw.alert_red_siren), alarmAttributes)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val yellow = NotificationChannel(
            CHANNEL_YELLOW, "هشدار زرد (خطر قریب‌الوقوع)", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "هشدار نسبت به تهدید نزدیک‌شونده"
            enableVibration(true)
            setSound(rawUri(R.raw.alert_yellow_chime), alarmAttributes)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val white = NotificationChannel(
            CHANNEL_WHITE, "پایان خطر", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "اعلام پایان وضعیت اضطراری"
            setSound(rawUri(R.raw.alert_white_calm), alarmAttributes)
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE, "اتصال به شبکه محلی", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نمایش وضعیت اتصال به سرور هشدار داخلی"
        }

        notificationManager.createNotificationChannels(listOf(red, yellow, white, service))
    }

    private fun rawUri(resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun buildServiceNotification(statusText: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("سامانه هشدار اضطراری متصل است")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Posts (or updates) the alert notification and, for RED, requests a full-screen intent. */
    fun postAlertNotification(state: AlertState) {
        val channel = when (state.level) {
            AlertLevel.RED -> CHANNEL_RED
            AlertLevel.YELLOW -> CHANNEL_YELLOW
            AlertLevel.WHITE -> CHANNEL_WHITE
        }

        val contentIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(state.level.persianLabel)
            .setContentText(buildString {
                append(state.level.persianDescription)
                if (!state.room.isNullOrBlank()) append(" — اتاق/موقعیت: ${state.room}")
                if (!state.note.isNullOrBlank()) append(" — ${state.note}")
            })
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(state.level == AlertLevel.WHITE)
            .setContentIntent(contentIntent)

        if (state.level == AlertLevel.RED) {
            val fullScreenIntent = Intent(context, FullScreenAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 2, fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            builder.setOngoing(true)
        }

        notificationManager.notify(NOTIFICATION_ID_ALERT, builder.build())
    }

    fun clearAlertNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ALERT)
    }
}
