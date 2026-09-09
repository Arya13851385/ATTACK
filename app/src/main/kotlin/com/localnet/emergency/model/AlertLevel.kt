package com.localnet.emergency.model

/**
 * The three-state threat state machine used across the whole system.
 *
 * WHITE -> YELLOW -> RED   (escalation, any order is technically allowed)
 * RED / YELLOW -> WHITE    (resolution, always allowed, always wins)
 *
 * Any authorized client may broadcast a transition; the broker/backend is
 * responsible for fanning it out to every connected device on the subnet.
 */
enum class AlertLevel(
    val wireValue: String,
    val persianLabel: String,
    val persianDescription: String,
    val priority: Int
) {
    WHITE(
        wireValue = "WHITE",
        persianLabel = "وضعیت سفید",
        persianDescription = "پایان خطر / وضعیت عادی",
        priority = 0
    ),
    YELLOW(
        wireValue = "YELLOW",
        persianLabel = "وضعیت زرد",
        persianDescription = "خطر قریب‌الوقوع",
        priority = 1
    ),
    RED(
        wireValue = "RED",
        persianLabel = "وضعیت قرمز",
        persianDescription = "خطر فوری - اقدام آنی",
        priority = 2
    );

    companion object {
        fun fromWire(value: String): AlertLevel =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: WHITE
    }
}
