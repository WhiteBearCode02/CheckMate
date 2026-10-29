package com.whitebearcode02.checkmate.vehicle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VehicleColorScheme = darkColorScheme(
    primary = CheckMateBlue,
    onPrimary = Color(0xFF082E50),
    primaryContainer = Color(0xFF164B7A),
    onPrimaryContainer = CheckMateText,
    secondary = CheckMateMuted,
    onSecondary = NavyBackground,
    tertiary = CheckMateOrange,
    onTertiary = Color(0xFF3F2100),
    error = CheckMateOrange,
    onError = Color(0xFF3F2100),
    errorContainer = Color(0xFF4C2D10),
    onErrorContainer = Color(0xFFFFDBB3),
    background = NavyBackground,
    onBackground = CheckMateText,
    surface = NavySurface,
    onSurface = CheckMateText,
    surfaceVariant = NavyRaised,
    onSurfaceVariant = CheckMateMuted,
    outline = Color(0xFF7E94AF),
)

/** Fixed high-contrast palette keeps vehicle status colors consistent across devices. */
@Composable
fun CheckMateVehicleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VehicleColorScheme,
        typography = Typography,
        content = content
    )
}
