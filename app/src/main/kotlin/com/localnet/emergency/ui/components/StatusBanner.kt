package com.localnet.emergency.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.model.AlertState
import com.localnet.emergency.ui.theme.AlertRed
import com.localnet.emergency.ui.theme.AlertWhiteCalm
import com.localnet.emergency.ui.theme.AlertYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusBanner(state: AlertState, modifier: Modifier = Modifier) {
    val color = when (state.level) {
        AlertLevel.RED -> AlertRed
        AlertLevel.YELLOW -> AlertYellow
        AlertLevel.WHITE -> AlertWhiteCalm
    }
    val textColor = if (state.level == AlertLevel.YELLOW) Color.Black else Color.White

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = state.level.persianLabel,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = state.level.persianDescription,
            color = textColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (!state.room.isNullOrBlank()) {
            Text(
                text = "موقعیت: ${state.room}",
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
        if (!state.note.isNullOrBlank()) {
            Text(
                text = state.note,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
        if (!state.senderName.isNullOrBlank()) {
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            Text(
                text = "توسط ${state.senderName} در ${timeFormat.format(Date(state.updatedAtMillis))}",
                color = textColor.copy(alpha = 0.85f),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
