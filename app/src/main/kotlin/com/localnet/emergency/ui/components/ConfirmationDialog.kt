package com.localnet.emergency.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.localnet.emergency.model.AlertLevel
import kotlinx.coroutines.delay

/**
 * A trigger-confirmation dialog that requires a deliberate press-and-hold
 * (not a simple tap) before an alert is actually sent, to prevent accidental
 * activation from a stray tap.
 */
@Composable
fun AlertConfirmationDialog(
    level: AlertLevel,
    onDismiss: () -> Unit,
    onConfirm: (note: String, room: String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    val holdDurationMs = 1600

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val steps = 32
            val stepDelay = holdDurationMs / steps
            for (i in 1..steps) {
                delay(stepDelay.toLong())
                if (!isHolding) return@LaunchedEffect
                holdProgress = i / steps.toFloat()
            }
            if (isHolding) {
                onConfirm(note, room)
            }
        } else {
            holdProgress = 0f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "holdProgress"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأیید اعلام ${level.persianLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("این وضعیت بلافاصله برای همه دستگاه‌های متصل در شبکه محلی ارسال می‌شود.")
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("اتاق / موقعیت (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("یادداشت کوتاه (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "برای ارسال، دکمه زیر را نگه دارید",
                    style = MaterialTheme.typography.bodyMedium
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    val released = tryAwaitRelease()
                                    isHolding = false
                                }
                            )
                        }
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            text = "نگه دارید برای تأیید نهایی",
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
