package com.localnet.emergency.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.model.AlertState
import com.localnet.emergency.ui.theme.AlertRed
import com.localnet.emergency.ui.theme.EmergencyAlertTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen takeover shown for RED alerts, including over the lock screen.
 * Uses window-level flags (rather than relying solely on the manifest
 * attributes) for correct behavior across OEM skins and API levels.
 */
@AndroidEntryPoint
class FullScreenAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()

        setContent {
            EmergencyAlertTheme(darkTheme = true) {
                FullScreenAlertScreen(onAcknowledge = { finish() })
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun FullScreenAlertScreen(
    viewModel: AlertViewModel = hiltViewModel(),
    onAcknowledge: () -> Unit
) {
    val alertState: AlertState by viewModel.alertState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = AlertRed) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = alertState.level.persianLabel,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = alertState.level.persianDescription,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            val room = alertState.room
            room?.let {
                if (it.isNotBlank()) {
                    Text(
                        text = "موقعیت: $it",
                        color = Color.White,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
            val note = alertState.note
            note?.let {
                if (it.isNotBlank()) {
                    Text(
                        text = it,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .padding(top = 40.dp)
                    .height(56.dp)
            ) {
                Text(
                    if (alertState.level == AlertLevel.RED) "متوجه شدم — بستن هشدار"
                    else "بستن"
                )
            }
        }
    }
}
