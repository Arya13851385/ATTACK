package com.localnet.emergency.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.ui.theme.AlertRed
import com.localnet.emergency.ui.theme.AlertWhiteCalm
import com.localnet.emergency.ui.theme.AlertYellow

/**
 * Rapid-trigger buttons. Tapping any of these only OPENS the confirmation
 * dialog (see AlertConfirmationDialog) — nothing is broadcast on tap alone,
 * which is the primary safeguard against accidental activation.
 */
@Composable
fun TriggerButtons(
    onLevelSelected: (AlertLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = { onLevelSelected(AlertLevel.RED) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White)
        ) {
            Text("اعلام وضعیت قرمز — خطر فوری", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        }
        Button(
            onClick = { onLevelSelected(AlertLevel.YELLOW) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlertYellow, contentColor = Color.Black)
        ) {
            Text("اعلام وضعیت زرد — خطر قریب‌الوقوع")
        }
        Button(
            onClick = { onLevelSelected(AlertLevel.WHITE) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlertWhiteCalm, contentColor = Color.White)
        ) {
            Text("اعلام پایان خطر (وضعیت سفید)")
        }
    }
}
