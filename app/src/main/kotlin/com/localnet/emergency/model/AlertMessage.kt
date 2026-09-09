package com.localnet.emergency.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format exchanged with the local broker over the WebSocket connection.
 * Kept intentionally flat and small to minimize serialization time and keep
 * the end-to-end broadcast latency well under the 500ms budget.
 */
@Serializable
enum class MessageType {
    @SerialName("HELLO") HELLO,          // client -> server, on connect
    @SerialName("HEARTBEAT") HEARTBEAT,  // client <-> server, keep-alive
    @SerialName("ALERT") ALERT,          // client -> server -> broadcast to all
    @SerialName("STATE_SYNC") STATE_SYNC // server -> client, sent on connect
}

@Serializable
data class AlertMessage(
    val type: MessageType,
    val level: String = AlertLevel.WHITE.wireValue,
    val note: String? = null,
    val room: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String = java.util.UUID.randomUUID().toString()
)

/**
 * In-app representation combining the last received wire message with
 * derived, ready-to-render fields.
 */
data class AlertState(
    val level: AlertLevel = AlertLevel.WHITE,
    val note: String? = null,
    val room: String? = null,
    val senderName: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
