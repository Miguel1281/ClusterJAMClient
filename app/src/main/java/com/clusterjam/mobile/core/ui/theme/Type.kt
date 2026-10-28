package com.clusterjam.mobile.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Android's system sans (Roboto): available offline and respects system font scaling.
private fun jamType(size: Int, leading: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = leading.sp,
        letterSpacing = tracking.sp,
    )

val Typography =
    Typography(
        displayLarge = jamType(40, 46, FontWeight.SemiBold, -0.8),
        displayMedium = jamType(36, 42, FontWeight.SemiBold, -0.6),
        displaySmall = jamType(32, 38, FontWeight.SemiBold, -0.5),
        headlineLarge = jamType(30, 36, FontWeight.SemiBold, -0.5),
        headlineMedium = jamType(28, 34, FontWeight.SemiBold, -0.4),
        headlineSmall = jamType(24, 30, FontWeight.SemiBold, -0.3),
        titleLarge = jamType(22, 28, FontWeight.SemiBold, -0.2),
        titleMedium = jamType(16, 22, FontWeight.Medium),
        titleSmall = jamType(14, 20, FontWeight.SemiBold),
        bodyLarge = jamType(16, 24, FontWeight.Normal),
        bodyMedium = jamType(14, 20, FontWeight.Normal),
        bodySmall = jamType(12, 18, FontWeight.Normal, 0.1),
        labelLarge = jamType(14, 20, FontWeight.SemiBold),
        labelMedium = jamType(12, 18, FontWeight.Medium, 0.1),
        labelSmall = jamType(11, 16, FontWeight.Medium, 0.2),
    )
