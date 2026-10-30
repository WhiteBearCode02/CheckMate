package com.whitebearcode02.checkmate.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MobileColorScheme = darkColorScheme(
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

/** Dark palette shared with the vehicle app; phone screens keep Material default type sizes. */
@Composable
fun CheckMateMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MobileColorScheme,
        content = content
    )
}
