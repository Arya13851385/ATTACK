package com.localnet.emergency.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fallback for networks that block multicast/mDNS: lets an administrator
 * type the broker's LAN IP and port directly.
 */
@Composable
fun ManualServerDialog(
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit
) {
    var host by remember { mutableStateOf("192.168.1.") }
    var portText by remember { mutableStateOf("8765") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اتصال دستی به سرور") },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("آدرس IP سرور") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() } },
                    label = { Text("پورت") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val port = portText.toIntOrNull() ?: 8765
                if (host.isNotBlank()) onConnect(host.trim(), port)
            }) { Text("اتصال") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
