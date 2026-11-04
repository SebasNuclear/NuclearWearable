package com.nuclear.soundconnect.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val NuclearBlack = Color(0xFF000000)
val NuclearSurface = Color(0xFF101013)
val NuclearSurfaceCard = Color(0xFF181A22)
val NuclearGreen = Color(0xFF00E676)
val NuclearBatteryGreen = Color(0xFF34C759)
val NuclearAmbientBlue = Color(0xFF00A3FF)
val NuclearSonyGold = Color(0xFFD4AF37)
val NuclearTextPrimary = Color(0xFFFFFFFF)
val NuclearTextSecondary = Color(0xFFA0A0B0)
val NuclearBorder = Color(0x1FFFFFFF)

fun createWearColors(accentColor: Color = NuclearGreen) = Colors(
    primary = accentColor,
    primaryVariant = NuclearBatteryGreen,
    secondary = NuclearAmbientBlue,
    background = NuclearBlack,
    surface = NuclearSurfaceCard,
    onPrimary = NuclearBlack,
    onSecondary = NuclearBlack,
    onBackground = NuclearTextPrimary,
    onSurface = NuclearTextPrimary,
    onError = Color(0xFFFF5252)
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
