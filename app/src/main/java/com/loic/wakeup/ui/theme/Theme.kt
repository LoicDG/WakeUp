package com.loic.wakeup.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Pre-dawn palette
val Amber = Color(0xFFFFA726)
val Midnight = Color(0xFF08101E)
val DeepNavy = Color(0xFF0F1C30)
val NavyVariant = Color(0xFF192844)
val NavyOutline = Color(0xFF253654)
val StarWhite = Color(0xFFE2EAFF)
val SlateBlue = Color(0xFF7893B8)
val MorningBlue = Color(0xFF5B8FB9)

private val WakeUpColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1C0A00),
    primaryContainer = Color(0xFF3D2100),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = MorningBlue,
    onSecondary = Color(0xFF003257),
    secondaryContainer = Color(0xFF004A7F),
    onSecondaryContainer = Color(0xFFD1E4FF),
    background = Midnight,
    onBackground = StarWhite,
    surface = DeepNavy,
    onSurface = StarWhite,
    surfaceVariant = NavyVariant,
    onSurfaceVariant = SlateBlue,
    outline = NavyOutline,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF690005),
)

val WakeUpTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = 80.sp,
        lineHeight = 88.sp,
        letterSpacing = (-2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
    ),
)

@Composable
fun WakeUpTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WakeUpColors,
        typography = WakeUpTypography,
        content = content,
    )
}
