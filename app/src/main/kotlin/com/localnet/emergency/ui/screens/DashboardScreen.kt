package com.localnet.emergency.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.ui.AlertViewModel
import com.localnet.emergency.ui.components.AlertConfirmationDialog
import com.localnet.emergency.ui.components.ConnectionStatusCard
import com.localnet.emergency.ui.components.ManualServerDialog
import com.localnet.emergency.ui.components.StatusBanner
import com.localnet.emergency.ui.components.TriggerButtons

@Composable
fun DashboardScreen(viewModel: AlertViewModel = hiltViewModel()) {
    val alertState by viewModel.alertState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var pendingLevel by remember { mutableStateOf<AlertLevel?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "سامانه هشدار اضطراری داخلی",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().padding(bottom = 0.dp)
            )

            ConnectionStatusCard(state = connectionState)

            StatusBanner(state = alertState)

            Text(
                text = "با فشار طولانی روی هر دکمه، وضعیت برای همه دستگاه‌های متصل ارسال می‌شود",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            TriggerButtons(onLevelSelected = { pendingLevel = it })

            TextButton(onClick = { showManualDialog = true }) {
                Text("تنظیم دستی آدرس سرور")
            }

            TextButton(onClick = { viewModel.retryDiscovery() }) {
                Text("جستجوی مجدد سرور در شبکه")
            }
        }
    }

    pendingLevel?.let { level ->
        AlertConfirmationDialog(
            level = level,
            onDismiss = { pendingLevel = null },
            onConfirm = { note, room ->
                viewModel.triggerAlert(level, note.ifBlank { null }, room.ifBlank { null })
                pendingLevel = null
            }
        )
    }

    if (showManualDialog) {
        ManualServerDialog(
            onDismiss = { showManualDialog = false },
            onConnect = { host, port ->
                viewModel.connectManually(host, port)
                showManualDialog = false
            }
        )
    }
}
