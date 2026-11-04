package com.nuclear.soundconnect.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode

@Composable
fun NuclearTrayMenu(
    state: HeadsetState,
    strings: LocalizedStrings,
    accentColor: Color,
    isDark: Boolean,
    isOled: Boolean,
    onOpenMain: () -> Unit,
    onSelectMode: (NoiseControlMode) -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit
) {
    val bgColor = if (isDark) {
        if (isOled) Color(0xFF0A0A0C) else Color(0xFF18181B)
    } else {
        Color(0xFFFAFAFC)
    }

    val cardColor = if (isDark) {
        if (isOled) Color(0xFF141417) else Color(0xFF232326)
    } else {
        Color(0xFFFFFFFF)
    }

    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFF9E9EA4) else Color(0xFF707074)
    val dividerColor = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)
    val borderColor = if (isDark) Color(0x2BFFFFFF) else Color(0x1F000000)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        shape = RoundedCornerShape(22.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Encabezado: Nombre del dispositivo y estado de conexión
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.deviceName.isNotBlank()) state.deviceName else state.modelFamily.displayName,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.isConnected) strings.connected else strings.disconnected,
                        color = if (state.isConnected) accentColor else textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (state.isConnected) accentColor else Color(0xFF8E8E93),
                            shape = CircleShape
                        )
                )
            }

            // 2. Fila de Baterías (si está conectado)
            if (state.isConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.deviceType == DeviceType.OVER_EAR) {
                        BatteryMiniPill(
                            label = "Batería",
                            percent = state.singleBattery.displayPercent,
                            isCharging = state.singleBattery.isCharging,
                            textPrimary = textPrimary,
                            accentColor = accentColor
                        )
                    } else {
                        BatteryMiniPill(
                            label = "L",
                            percent = state.leftBattery.displayPercent,
                            isCharging = state.leftBattery.isCharging,
                            textPrimary = textPrimary,
                            accentColor = accentColor
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(dividerColor))
                        BatteryMiniPill(
                            label = "R",
                            percent = state.rightBattery.displayPercent,
                            isCharging = state.rightBattery.isCharging,
                            textPrimary = textPrimary,
                            accentColor = accentColor
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(dividerColor))
                        BatteryMiniPill(
                            label = "Case",
                            percent = state.caseBattery.displayPercent,
                            isCharging = state.caseBattery.isCharging,
                            textPrimary = textPrimary,
                            accentColor = accentColor
                        )
                    }
                }
            }

            // 3. Selector de Control de Ruido (Píldoras rápidas con accentColor)
            if (state.isConnected && state.modelFamily.hasAnc) {
                Text(
                    text = strings.soundSettingsTitle,
                    color = textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor, RoundedCornerShape(14.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    ModeMiniSegment(
                        label = strings.noiseCancelling,
                        isSelected = state.noiseMode == NoiseControlMode.ANC,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectMode(NoiseControlMode.ANC) }
                    )
                    ModeMiniSegment(
                        label = strings.ambientSound,
                        isSelected = state.noiseMode == NoiseControlMode.AMBIENT,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectMode(NoiseControlMode.AMBIENT) }
                    )
                    ModeMiniSegment(
                        label = strings.noiseOff,
                        isSelected = state.noiseMode == NoiseControlMode.OFF,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectMode(NoiseControlMode.OFF) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))

            // 4. Acciones: Abrir, Actualizar, Salir
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TrayActionRow(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = strings.trayOpen,
                    textPrimary = textPrimary,
                    onClick = onOpenMain
                )

                TrayActionRow(
                    icon = Icons.Rounded.Refresh,
                    label = strings.refreshButton,
                    textPrimary = textPrimary,
                    onClick = onRefresh
                )

                TrayActionRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = strings.trayExit,
                    textPrimary = Color(0xFFFF5252),
                    onClick = onExit
                )
            }
        }
    }
}

@Composable
private fun BatteryMiniPill(
    label: String,
    percent: String,
    isCharging: Boolean,
    textPrimary: Color,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = percent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary
        )
        if (isCharging) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun ModeMiniSegment(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isSelected) accentColor else Color.Transparent
    val textColor = if (isSelected) Color.Black else textPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun TrayActionRow(
    icon: ImageVector,
    label: String,
    textPrimary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary
        )
    }
}
