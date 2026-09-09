package com.localnet.emergency.vibration

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.localnet.emergency.model.AlertLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Distinct vibration patterns per alert level. RED uses a maximal, near
 * continuous pattern; YELLOW uses a lighter pulse; WHITE cancels vibration
 * entirely (a single short confirmation buzz).
 */
@Singleton
class AlertVibrationManager @Inject constructor(
    private val context: Context
) {
    private val legacyVibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else null
    }

    private val vibratorManager: VibratorManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        } else null
    }

    fun vibrateForLevel(level: AlertLevel) {
        when (level) {
            AlertLevel.RED -> vibrate(RED_PATTERN, repeatIndex = 0)
            AlertLevel.YELLOW -> vibrate(YELLOW_PATTERN, repeatIndex = 0)
            AlertLevel.WHITE -> vibrate(WHITE_PATTERN, repeatIndex = -1)
        }
    }

    fun cancel() {
        legacyVibrator?.cancel()
        vibratorManager?.defaultVibrator?.cancel()
    }

    private fun vibrate(pattern: LongArray, repeatIndex: Int) {
        val effect = VibrationEffect.createWaveform(pattern, repeatIndex)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
        } else {
            legacyVibrator?.vibrate(effect)
        }
    }

    companion object {
        // timings in ms: off, on, off, on, ...
        private val RED_PATTERN = longArrayOf(0, 800, 200, 800, 200, 800, 200)
        private val YELLOW_PATTERN = longArrayOf(0, 300, 700, 300, 700)
        private val WHITE_PATTERN = longArrayOf(0, 150)
    }
}
