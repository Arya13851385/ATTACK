package com.localnet.emergency.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localnet.emergency.model.ConnectionState

@Composable
fun ConnectionStatusCard(state: ConnectionState, modifier: Modifier = Modifier) {
    val (dotColor, label) = when (state) {
        is ConnectionState.Connected -> Color(0xFF2E7D32) to "متصل به ${state.host}:${state.port}"
        is ConnectionState.Connecting -> Color(0xFFFBC02D) to "در حال اتصال..."
        is ConnectionState.Discovering -> Color(0xFFFBC02D) to "در حال جستجوی سرور در شبکه..."
        is ConnectionState.Reconnecting -> Color(0xFFFF7043) to "تلاش مجدد (${state.attempt})..."
        is ConnectionState.Failed -> Color(0xFFD32F2F) to "اتصال ناموفق"
        ConnectionState.Disconnected -> Color(0xFF9E9E9E) to "قطع — سرور یافت نشد"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
