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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.nuclear.soundconnect.bluetooth.BatteryInfo
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBorder
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSonyGold
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSurfaceCard
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextPrimary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextSecondary
import com.nuclear.soundconnect.wear.presentation.util.SonyModelDrawables

@Composable
fun BatteryArcsDisplay(
    state: HeadsetState,
    modifier: Modifier = Modifier
) {
    if (state.deviceType == DeviceType.OVER_EAR) {
        SingleBatteryDisplay(state, modifier)
    } else {
        TwsBatteryDisplay(state, modifier)
    }
}

@Composable
private fun TwsBatteryDisplay(
    state: HeadsetState,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(state.accentColor)
    val drawables = SonyModelDrawables.getDrawablesForFamily(state.modelFamily)

    val leftLevel = state.leftBattery.level.coerceIn(0, 100)
    val rightLevel = state.rightBattery.level.coerceIn(0, 100)
    val caseLevel = state.caseBattery.level.coerceIn(0, 100)

    val leftSweep by animateFloatAsState(
        targetValue = if (state.leftBattery.isAvailable) (leftLevel / 100f) * 80f else 0f,
        animationSpec = tween(600),
        label = "leftSweep"
    )
    val rightSweep by animateFloatAsState(
        targetValue = if (state.rightBattery.isAvailable) (rightLevel / 100f) * 80f else 0f,
        animationSpec = tween(600),
        label = "rightSweep"
    )
    val caseSweep by animateFloatAsState(
        targetValue = if (state.caseBattery.isAvailable) (caseLevel / 100f) * 80f else 0f,
        animationSpec = tween(600),
        label = "caseSweep"
    )

    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Arcos de Batería con colores de acento sincronizados
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val trackColor = Color(0x22FFFFFF)

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
                    color = NuclearSonyGold,
                    startAngle = 50f,
                    sweepAngle = caseSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 2. Centro: Iconos vectoriales del modelo prominentes sobre porcentajes L y R, y debajo del Estuche
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Fila superior: L y R con sus iconos vectoriales de tamaño claro y nítido
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
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BatteryPill(
                        label = "L",
                        info = state.leftBattery,
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
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BatteryPill(
                        label = "R",
                        info = state.rightBattery,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila inferior: Estuche con su píldora y su icono vectorial nítido
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BatteryPill(
                    label = "Estuche",
                    info = state.caseBattery,
                    color = NuclearSonyGold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Image(
                    painter = painterResource(id = drawables.case),
                    contentDescription = "Estuche",
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

@Composable
private fun SingleBatteryDisplay(
    state: HeadsetState,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(state.accentColor)
    val drawables = SonyModelDrawables.getDrawablesForFamily(state.modelFamily)
    val level = state.singleBattery.level.coerceIn(0, 100)

    val sweep by animateFloatAsState(
        targetValue = if (state.singleBattery.isAvailable) (level / 100f) * 260f else 0f,
        animationSpec = tween(600),
        label = "singleSweep"
    )

    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val trackColor = Color(0x22FFFFFF)

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
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.singleBattery.displayPercent,
                color = NuclearTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.singleBattery.isCharging) {
                Text(
                    text = "⚡ Cargando",
                    color = accentColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun BatteryPill(
    label: String,
    info: BatteryInfo,
    color: Color,
    modifier: Modifier = Modifier
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
                text = info.displayPercent,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (info.isCharging) {
                Text(
                    text = " ⚡",
                    color = color,
                    fontSize = 9.sp
                )
            }
        }
    }
}
