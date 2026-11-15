package com.nuclear.soundconnect.wear.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
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
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBorder
import com.nuclear.soundconnect.wear.presentation.theme.NuclearInactiveCircle
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
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val effectiveAccent = if (isConnected) accentColor else Color(0xFF3A3A3C)
    val effectiveActiveTint = if (isConnected) Color.White else NuclearTextSecondary
    val effectiveLabelActive = if (isConnected) accentColor else NuclearTextSecondary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Fila de 3 Botones Circulares estilo QuickActionButton móvil (ANC / Ambiente / Off)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoiseButton(
                iconRes = R.drawable.ic_anc_on,
                label = "ANC",
                isActive = currentMode == NoiseControlMode.ANC,
                activeBgColor = effectiveAccent,
                activeIconTint = effectiveActiveTint,
                activeLabelColor = effectiveLabelActive,
                enabled = !isBusy,
                onClick = { onModeSelect(NoiseControlMode.ANC) }
            )

            NoiseButton(
                iconRes = R.drawable.ic_ambient_sound,
                label = "Amb.",
                isActive = currentMode == NoiseControlMode.AMBIENT,
                activeBgColor = effectiveAccent,
                activeIconTint = effectiveActiveTint,
                activeLabelColor = effectiveLabelActive,
                enabled = !isBusy,
                onClick = { onModeSelect(NoiseControlMode.AMBIENT) }
            )

            NoiseButton(
                iconRes = R.drawable.ic_anc_off,
                label = "Off",
                isActive = currentMode == NoiseControlMode.OFF,
                activeBgColor = effectiveAccent,
                activeIconTint = effectiveActiveTint,
                activeLabelColor = effectiveLabelActive,
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
            val controlAccent = if (isConnected) accentColor else NuclearTextSecondary

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Stepper de Nivel (1..20) con iconos vectoriales puros
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NuclearSurfaceCard)
                        .border(1.dp, NuclearBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NuclearInactiveCircle)
                            .clickable(enabled = !isBusy && ambientLevel > 1) {
                                onAmbientConfigChange((ambientLevel - 1).coerceAtLeast(1), voiceFocus)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_minus),
                            contentDescription = "Reducir nivel ambiente",
                            tint = if (ambientLevel > 1) NuclearTextPrimary else NuclearTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "Nivel $ambientLevel / 20",
                        color = controlAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NuclearInactiveCircle)
                            .clickable(enabled = !isBusy && ambientLevel < 20) {
                                onAmbientConfigChange((ambientLevel + 1).coerceAtMost(20), voiceFocus)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = "Subir nivel ambiente",
                            tint = if (ambientLevel < 20) NuclearTextPrimary else NuclearTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Selector de Enfoque en la voz
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (voiceFocus) controlAccent.copy(alpha = 0.16f) else NuclearSurfaceCard)
                        .border(
                            1.dp,
                            if (voiceFocus) controlAccent else NuclearBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !isBusy) {
                            onAmbientConfigChange(ambientLevel, !voiceFocus)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Enfoque en la voz",
                            color = if (voiceFocus) controlAccent else NuclearTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (voiceFocus) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (voiceFocus) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Activado",
                                tint = controlAccent,
                                modifier = Modifier.size(13.dp)
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
    activeBgColor: Color,
    activeIconTint: Color,
    activeLabelColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgAnim by animateColorAsState(
        targetValue = if (isActive) activeBgColor else NuclearInactiveCircle,
        animationSpec = tween(220),
        label = "noiseBgAnim"
    )
    val tintAnim by animateColorAsState(
        targetValue = if (isActive) activeIconTint else NuclearTextSecondary,
        animationSpec = tween(220),
        label = "noiseTintAnim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bgAnim)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = tintAnim,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) activeLabelColor else NuclearTextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
