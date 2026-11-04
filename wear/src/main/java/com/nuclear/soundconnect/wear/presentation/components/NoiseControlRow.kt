package com.nuclear.soundconnect.wear.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBlack
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBorder
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSurfaceCard
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextPrimary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextSecondary

@Composable
fun NoiseControlRow(
    currentMode: NoiseControlMode,
    ambientLevel: Int,
    voiceFocus: Boolean,
    accentColor: Color,
    isBusy: Boolean,
    onModeSelect: (NoiseControlMode) -> Unit,
    onAmbientConfigChange: (level: Int, voiceFocus: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Fila de 3 Botones Principales (ANC / Ambiente / Off)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón ANC
            NoiseButton(
                iconRes = R.drawable.ic_anc_on,
                label = "ANC",
                isActive = currentMode == NoiseControlMode.ANC,
                activeColor = accentColor,
                enabled = !isBusy,
                onClick = { onModeSelect(NoiseControlMode.ANC) }
            )

            // Botón Ambiente
            NoiseButton(
                iconRes = R.drawable.ic_ambient_sound,
                label = "Ambiente",
                isActive = currentMode == NoiseControlMode.AMBIENT,
                activeColor = accentColor,
                enabled = !isBusy,
                onClick = { onModeSelect(NoiseControlMode.AMBIENT) }
            )

            // Botón Off
            NoiseButton(
                iconRes = R.drawable.ic_anc_off,
                label = "Off",
                isActive = currentMode == NoiseControlMode.OFF,
                activeColor = Color(0xFFD0D0D0),
                enabled = !isBusy,
                onClick = { onModeSelect(NoiseControlMode.OFF) }
            )
        }

        // 2. Controles de Nivel de Sonido Ambiente y Enfoque en la voz cuando Ambiente está activo
        AnimatedVisibility(
            visible = currentMode == NoiseControlMode.AMBIENT,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Stepper de Nivel (1..20)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NuclearSurfaceCard)
                        .border(1.dp, NuclearBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Botón Menos
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x20FFFFFF))
                            .clickable(enabled = !isBusy && ambientLevel > 1) {
                                onAmbientConfigChange((ambientLevel - 1).coerceAtLeast(1), voiceFocus)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            color = NuclearTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Nivel $ambientLevel / 20",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Botón Más
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x20FFFFFF))
                            .clickable(enabled = !isBusy && ambientLevel < 20) {
                                onAmbientConfigChange((ambientLevel + 1).coerceAtMost(20), voiceFocus)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = NuclearTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Selector de Enfoque en la voz
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (voiceFocus) accentColor.copy(alpha = 0.2f) else NuclearSurfaceCard)
                        .border(
                            1.dp,
                            if (voiceFocus) accentColor else NuclearBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isBusy) {
                            onAmbientConfigChange(ambientLevel, !voiceFocus)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Enfoque en la voz",
                            color = if (voiceFocus) accentColor else NuclearTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (voiceFocus) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (voiceFocus) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "✓",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoiseButton(
    iconRes: Int,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) activeColor else NuclearSurfaceCard
    val contentColor = if (isActive) NuclearBlack else NuclearTextPrimary
    val borderColor = if (isActive) activeColor else NuclearBorder

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, borderColor, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else NuclearTextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
