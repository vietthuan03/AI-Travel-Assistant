package com.example.ai_travel_assistant.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val BrandBlue = Color(0xFF075BAD)
private val SkyBlue = Color(0xFF9ACBFF)
private val Ink = Color(0xFF142849)
private val PaleSky = Color(0xFFE2F2FF)
private val Midnight = Color(0xFF0D1728)
private val BrightText = Color(0xFFEBF3FF)
private val DeepBlue = Color(0xFF1B3B60)

internal val TravelLightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = PaleSky,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF3D5270),
    onSecondary = Color.White,
    secondaryContainer = PaleSky,
    onSecondaryContainer = Ink,
    background = Color(0xFFF2F7FD),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF3D5270),
    surfaceVariant = Color(0xFFEAF4FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F9FF),
    surfaceContainer = Color(0xFFEAF4FF),
    surfaceContainerHigh = Color(0xFFDDEDFD),
    surfaceContainerHighest = Color(0xFFD2E5FA),
    outline = Color(0xFF65809B),
    outlineVariant = Color(0xFFCCDBED),
    error = Color(0xFFA32C24),
    errorContainer = Color(0xFFFBE9E5),
    onErrorContainer = Color(0xFF842F28),
)

internal val TravelDarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF092649),
    primaryContainer = DeepBlue,
    onPrimaryContainer = BrightText,
    secondary = Color(0xFFB7CAE4),
    onSecondary = Color(0xFF092649),
    secondaryContainer = DeepBlue,
    onSecondaryContainer = BrightText,
    background = Midnight,
    onBackground = BrightText,
    surface = Color(0xFF14243D),
    onSurface = BrightText,
    onSurfaceVariant = Color(0xFFB7CAE4),
    surfaceVariant = Color(0xFF203652),
    surfaceContainerLowest = Color(0xFF0A1424),
    surfaceContainerLow = Color(0xFF101F35),
    surfaceContainer = Color(0xFF172A44),
    surfaceContainerHigh = Color(0xFF203652),
    surfaceContainerHighest = Color(0xFF29405F),
    outline = Color(0xFF7895B4),
    outlineVariant = Color(0xFF3C5879),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF522821),
    onErrorContainer = Color(0xFFFFDAD5),
)
