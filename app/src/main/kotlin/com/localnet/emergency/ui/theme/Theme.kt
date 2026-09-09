package com.localnet.emergency.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

private val EmergencyDarkColorScheme = darkColorScheme(
    primary = AlertRed,
    secondary = AlertYellow,
    tertiary = AlertWhiteCalm,
    background = SurfaceDark,
    surface = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val EmergencyLightColorScheme = lightColorScheme(
    primary = AlertRed,
    secondary = AlertYellow,
    tertiary = AlertWhiteCalm
)

/**
 * The whole app is authored for Persian/RTL: layout direction is forced to
 * RTL regardless of system locale, since this is a single-purpose, single
 * language internal tool.
 */
@Composable
fun EmergencyAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EmergencyDarkColorScheme else EmergencyLightColorScheme
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EmergencyTypography,
            content = content
        )
    }
}
