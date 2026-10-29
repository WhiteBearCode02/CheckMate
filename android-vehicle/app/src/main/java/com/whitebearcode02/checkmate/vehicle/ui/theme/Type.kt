package com.whitebearcode02.checkmate.vehicle.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun vehicleText(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = height.sp,
    )

val Typography = Typography(
    headlineLarge = vehicleText(34, 42, FontWeight.Bold),
    headlineMedium = vehicleText(28, 36, FontWeight.Bold),
    headlineSmall = vehicleText(24, 32, FontWeight.Bold),
    titleLarge = vehicleText(24, 32, FontWeight.SemiBold),
    titleMedium = vehicleText(22, 30, FontWeight.SemiBold),
    titleSmall = vehicleText(20, 28, FontWeight.SemiBold),
    bodyLarge = vehicleText(22, 32),
    bodyMedium = vehicleText(20, 30),
    bodySmall = vehicleText(18, 26),
    labelLarge = vehicleText(20, 28, FontWeight.SemiBold),
    labelMedium = vehicleText(18, 26, FontWeight.SemiBold),
    labelSmall = vehicleText(18, 26),
)
