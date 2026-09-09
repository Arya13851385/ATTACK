package com.localnet.emergency.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.localnet.emergency.R
import com.localnet.emergency.model.AlertLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays alert tones using AudioAttributes.USAGE_ALARM, which is the one
 * usage class the Android audio policy routes through even when the ringer
 * is in silent or vibrate-only mode, and which Do Not Disturb treats as an
 * alarm rather than a suppressible notification.
 *
 * RED    -> continuous looping siren, at max alarm-stream volume
 * YELLOW -> intermittent chime, repeated on a timer rather than looped
 * WHITE  -> single calm resolution tone, also stops any RED/YELLOW playback
 */
@Singleton
class AlertAudioManager @Inject constructor(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var intermittentRunnable: Runnable? = null

    private val alarmAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun playForLevel(level: AlertLevel) {
        stop()
        when (level) {
            AlertLevel.RED -> playLooping(R.raw.alert_red_siren)
            AlertLevel.YELLOW -> playIntermittent(R.raw.alert_yellow_chime)
            AlertLevel.WHITE -> playOnce(R.raw.alert_white_calm)
        }
    }

    private fun playLooping(resId: Int) {
        maximizeAlarmVolume()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(alarmAudioAttributes)
            val afd = context.resources.openRawResourceFd(resId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            isLooping = true
            prepare()
            start()
        }
    }

    private fun playIntermittent(resId: Int) {
        maximizeAlarmVolume()
        val runnable = object : Runnable {
            override fun run() {
                playOnceInternal(resId)
                handler.postDelayed(this, INTERMITTENT_PERIOD_MS)
            }
        }
        intermittentRunnable = runnable
        handler.post(runnable)
    }

    private fun playOnce(resId: Int) {
        maximizeAlarmVolume()
        playOnceInternal(resId)
    }

    private fun playOnceInternal(resId: Int) {
        val player = MediaPlayer().apply {
            setAudioAttributes(alarmAudioAttributes)
            val afd = context.resources.openRawResourceFd(resId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            isLooping = false
            setOnCompletionListener { it.release() }
            prepare()
            start()
        }
        mediaPlayer = player
    }

    private fun maximizeAlarmVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
    }

    fun stop() {
        intermittentRunnable?.let { handler.removeCallbacks(it) }
        intermittentRunnable = null
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    companion object {
        private const val INTERMITTENT_PERIOD_MS = 4_000L
    }
}
