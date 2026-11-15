package com.nuclear.soundconnect.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val NuclearBlack = Color(0xFF000000)
val NuclearSurface = Color(0xFF0B0B0D)
val NuclearSurfaceCard = Color(0xFF111113)
val NuclearSurfaceElevated = Color(0xFF1F1F23)
val NuclearInactiveCircle = Color(0x148E8E93)
val NuclearDisconnectedTint = Color(0x73505054)
val NuclearCaseSilver = Color(0xFFE5E5EA)
val NuclearGreen = Color(0xFF00E676)
val NuclearBatteryGreen = Color(0xFF34C759)
val NuclearAmbientBlue = Color(0xFF00A3FF)
val NuclearSonyGold = Color(0xFFD4AF37)
val NuclearTextPrimary = Color(0xFFFFFFFF)
val NuclearTextSecondary = Color(0xFF8E8E93)
val NuclearBorder = Color(0x1AFFFFFF)

fun createWearColors(accentColor: Color = NuclearGreen) = Colors(
    primary = accentColor,
    primaryVariant = NuclearBatteryGreen,
    secondary = NuclearAmbientBlue,
    background = NuclearBlack,
    surface = NuclearSurfaceCard,
    onPrimary = NuclearTextPrimary,
    onSecondary = NuclearBlack,
    onBackground = NuclearTextPrimary,
    onSurface = NuclearTextPrimary,
    onError = Color(0xFFFF453A)
)

val NuclearWearColors = createWearColors()

@Composable
fun NuclearWearTheme(
    accentColor: Color = NuclearGreen,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = createWearColors(accentColor),
        content = content
    )
}
