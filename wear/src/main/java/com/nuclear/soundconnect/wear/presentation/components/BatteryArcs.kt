package com.nuclear.soundconnect.wear.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.nuclear.soundconnect.bluetooth.BatteryInfo
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBorder
import com.nuclear.soundconnect.wear.presentation.theme.NuclearCaseSilver
import com.nuclear.soundconnect.wear.presentation.theme.NuclearDisconnectedTint
import com.nuclear.soundconnect.wear.presentation.theme.NuclearInactiveCircle
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSurfaceCard
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextPrimary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextSecondary
import com.nuclear.soundconnect.wear.presentation.util.SonyModelDrawables

@Composable
fun BatteryArcsDisplay(
    state: HeadsetState,
    accentColor: Color = Color(state.accentColor),
    modifier: Modifier = Modifier
) {
    if (state.deviceType == DeviceType.OVER_EAR) {
        SingleBatteryDisplay(state, accentColor, modifier)
    } else {
        TwsBatteryDisplay(state, accentColor, modifier)
    }
}

@Composable
private fun TwsBatteryDisplay(
    state: HeadsetState,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val drawables = SonyModelDrawables.getDrawablesForFamily(state.modelFamily)

    val isLeftActive = state.isConnected && state.leftBattery.isAvailable
    val isRightActive = state.isConnected && state.rightBattery.isAvailable
    val isCaseActive = state.isConnected && state.caseBattery.isAvailable

    val leftLevel = state.leftBattery.level.coerceIn(0, 100)
    val rightLevel = state.rightBattery.level.coerceIn(0, 100)
    val caseLevel = state.caseBattery.level.coerceIn(0, 100)

    val leftSweep by animateFloatAsState(
        targetValue = if (isLeftActive) (leftLevel / 100f) * 80f else 0f,
        animationSpec = tween(600),
        label = "leftSweep"
    )
    val rightSweep by animateFloatAsState(
        targetValue = if (isRightActive) (rightLevel / 100f) * 80f else 0f,
        animationSpec = tween(600),
        label = "rightSweep"
    )
    val caseSweep by animateFloatAsState(
        targetValue = if (isCaseActive) (caseLevel / 100f) * 80f else 0f,
        animationSpec = tween(600),
        label = "caseSweep"
    )

    val leftTint = ColorFilter.tint(if (isLeftActive) Color.White else NuclearDisconnectedTint)
    val rightTint = ColorFilter.tint(if (isRightActive) Color.White else NuclearDisconnectedTint)
    val caseTint = ColorFilter.tint(if (isCaseActive) Color.White else NuclearDisconnectedTint)

    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Arcos de Batería OLED (atenuados si no están conectados)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val trackColor = if (state.isConnected) Color(0x22FFFFFF) else NuclearInactiveCircle

            // Arco Izquierdo (L): desde 140° hasta 220° (80°)
            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 80f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (leftSweep > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = 140f,
                    sweepAngle = leftSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Arco Derecho (R): desde 320° hasta 40° (80°)
            drawArc(
                color = trackColor,
                startAngle = 320f,
                sweepAngle = 80f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (rightSweep > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = 320f,
                    sweepAngle = rightSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Arco Estuche (Inferior): desde 50° hasta 130° (80°)
            drawArc(
                color = trackColor,
                startAngle = 50f,
                sweepAngle = 80f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (caseSweep > 0f) {
                drawArc(
                    color = NuclearCaseSilver,
                    startAngle = 50f,
                    sweepAngle = caseSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 2. Centro: Siluetas vectoriales del modelo con atenuación gris al desconectar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Auricular Izquierdo (L)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = drawables.leftBud),
                        contentDescription = "Auricular Izquierdo",
                        modifier = Modifier.size(36.dp),
                        colorFilter = leftTint
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BatteryPill(
                        label = "L",
                        info = state.leftBattery,
                        isAvailable = isLeftActive,
                        color = accentColor
                    )
                }

                // Auricular Derecho (R)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = drawables.rightBud),
                        contentDescription = "Auricular Derecho",
                        modifier = Modifier.size(36.dp),
                        colorFilter = rightTint
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BatteryPill(
                        label = "R",
                        info = state.rightBattery,
                        isAvailable = isRightActive,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Estuche con píldora e icono vectorial armonizado
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BatteryPill(
                    label = "Estuche",
                    info = state.caseBattery,
                    isAvailable = isCaseActive,
                    color = NuclearCaseSilver
                )
                Spacer(modifier = Modifier.height(2.dp))
                Image(
                    painter = painterResource(id = drawables.case),
                    contentDescription = "Estuche",
                    modifier = Modifier.size(38.dp),
                    colorFilter = caseTint
                )
            }
        }
    }
}

@Composable
private fun SingleBatteryDisplay(
    state: HeadsetState,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val drawables = SonyModelDrawables.getDrawablesForFamily(state.modelFamily)
    val isSingleActive = state.isConnected && state.singleBattery.isAvailable
    val level = state.singleBattery.level.coerceIn(0, 100)

    val sweep by animateFloatAsState(
        targetValue = if (isSingleActive) (level / 100f) * 260f else 0f,
        animationSpec = tween(600),
        label = "singleSweep"
    )

    val headphoneTint = ColorFilter.tint(if (isSingleActive) Color.White else NuclearDisconnectedTint)

    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val trackColor = if (state.isConnected) Color(0x22FFFFFF) else NuclearInactiveCircle

            // Arco grande superior de 260°
            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (sweep > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = 140f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = drawables.headphone),
                contentDescription = state.deviceName,
                modifier = Modifier.size(52.dp),
                colorFilter = headphoneTint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSingleActive) state.singleBattery.displayPercent else "--%",
                color = if (isSingleActive) NuclearTextPrimary else NuclearTextSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSingleActive && state.singleBattery.isCharging) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_charging_bolt),
                        contentDescription = "Cargando",
                        tint = accentColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Cargando",
                        color = accentColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryPill(
    label: String,
    info: BatteryInfo,
    color: Color,
    modifier: Modifier = Modifier,
    isAvailable: Boolean = info.isAvailable
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NuclearSurfaceCard)
            .border(1.dp, NuclearBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label ",
                color = NuclearTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = if (isAvailable) info.displayPercent else "--%",
                color = if (isAvailable) color else NuclearTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isAvailable && info.isCharging) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_charging_bolt),
                    contentDescription = "Cargando",
                    tint = color,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}
