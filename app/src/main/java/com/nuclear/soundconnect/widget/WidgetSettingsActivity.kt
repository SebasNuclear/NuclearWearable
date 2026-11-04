package com.nuclear.soundconnect.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.nuclear.soundconnect.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class WidgetSettingsActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancel por defecto en caso de salir sin guardar
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val initialAlpha = WidgetPrefsHelper.getAlphaPercent(this, appWidgetId)
        val initialTheme = WidgetPrefsHelper.getTheme(this, appWidgetId)
        val initialStyle = WidgetPrefsHelper.getWidgetStyle(this, appWidgetId)
        val accentColor = Color(WidgetPrefsHelper.getAccentColor(this))

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            ) {
                WidgetSettingsContent(
                    initialAlpha = initialAlpha,
                    initialTheme = initialTheme,
                    initialStyle = initialStyle,
                    accentColor = accentColor,
                    onSave = { theme, alpha, style ->
                        WidgetPrefsHelper.setTheme(this@WidgetSettingsActivity, appWidgetId, theme)
                        WidgetPrefsHelper.setAlphaPercent(this@WidgetSettingsActivity, appWidgetId, alpha)
                        WidgetPrefsHelper.setWidgetStyle(this@WidgetSettingsActivity, appWidgetId, style)
                        NuclearWidgetManager.updateAllWidgets(this@WidgetSettingsActivity)

                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun WidgetSettingsContent(
    initialAlpha: Int,
    initialTheme: Int,
    initialStyle: Int,
    accentColor: Color,
    onSave: (Int, Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    var alphaPercent by remember { mutableFloatStateOf(initialAlpha.toFloat()) }
    var selectedTheme by remember { mutableIntStateOf(initialTheme) }
    var selectedStyle by remember { mutableIntStateOf(initialStyle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Ajustes del Widget",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Personaliza estilo, colores y nivel de transparencia sobre tu fondo de pantalla.",
                fontSize = 13.sp,
                color = Color(0xFF8E8E93),
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // Live Preview Card
            Text(
                text = "VISTA PREVIA",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E8E93),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            val isThemeDark = when (selectedTheme) {
                WidgetPrefsHelper.THEME_LIGHT -> false
                WidgetPrefsHelper.THEME_OLED_DARK -> true
                else -> true
            }

            // Regla One UI: si el fondo tiene 40% o menos, el contenido se vuelve blanco
            val isContentWhite = alphaPercent <= 40 || isThemeDark

            val previewBgBase = if (!isThemeDark) Color.White else Color.Black
            val previewColor = previewBgBase.copy(alpha = alphaPercent / 100f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(previewColor)
                    .border(1.dp, if (isContentWhite) Color(0x33FFFFFF) else Color(0x1F000000), RoundedCornerShape(26.dp))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Nombre centrado
                    Text(
                        text = "WF-1000XM6",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isContentWhite) Color.White else Color(0xFF1C1C1E)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3 Columnas según el estilo seleccionado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (selectedStyle) {
                            WidgetPrefsHelper.STYLE_MINIMAL -> {
                                // 1. Estilo Minimalista (Sin anillos)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_bud_left),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                    )
                                    Text("100%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                    Text("L", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_bud_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                    )
                                    Text("100%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                    Text("R", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_case),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                    )
                                    Text("100%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                    Text("Estuche", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }
                            }

                            WidgetPrefsHelper.STYLE_HALF_RING -> {
                                // 2. Estilo Medio Anillo (Arco abierto inferior con extremos redondeados)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawArc(
                                                color = accentColor,
                                                startAngle = 145f,
                                                sweepAngle = 250f,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 2.8.dp.toPx(),
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_bud_left),
                                                contentDescription = null,
                                                modifier = Modifier.size(23.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                            )
                                            Text("100%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("L", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawArc(
                                                color = accentColor,
                                                startAngle = 145f,
                                                sweepAngle = 250f,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 2.8.dp.toPx(),
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_bud_right),
                                                contentDescription = null,
                                                modifier = Modifier.size(23.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                            )
                                            Text("100%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("R", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawArc(
                                                color = accentColor,
                                                startAngle = 145f,
                                                sweepAngle = 250f,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 2.8.dp.toPx(),
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_case),
                                                contentDescription = null,
                                                modifier = Modifier.size(23.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                            )
                                            Text("100%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Estuche", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }
                            }

                            else -> {
                                // 3. Estilo Anillo Completo (360° simétrico)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .border(2.5.dp, accentColor, RoundedCornerShape(100.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_bud_left),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                            )
                                            Text("100%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("L", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .border(2.5.dp, accentColor, RoundedCornerShape(100.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_bud_right),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                            )
                                            Text("100%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("R", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .border(2.5.dp, accentColor, RoundedCornerShape(100.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wf1000xm6_case),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                            )
                                            Text("100%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Estuche", fontSize = 10.sp, color = if (isContentWhite) Color(0xFFD1D1D6) else Color(0xFF707070))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Barra segmentada con color de acento sincronizado
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isContentWhite) Color(0x26FFFFFF) else Color(0x1A000000)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ANC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("Off", fontSize = 10.sp, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("Ambiente", fontSize = 10.sp, color = if (isContentWhite) Color.White else Color(0xFF1C1C1E))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Selector de Estilo (Minimalista vs Con Anillos)
            Text(
                text = "ESTILO VISUAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E8E93),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedStyle == WidgetPrefsHelper.STYLE_MINIMAL) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
                        .border(
                            width = if (selectedStyle == WidgetPrefsHelper.STYLE_MINIMAL) 2.dp else 1.dp,
                            color = if (selectedStyle == WidgetPrefsHelper.STYLE_MINIMAL) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedStyle = WidgetPrefsHelper.STYLE_MINIMAL },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Minimalista", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedStyle == WidgetPrefsHelper.STYLE_HALF_RING) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
                        .border(
                            width = if (selectedStyle == WidgetPrefsHelper.STYLE_HALF_RING) 2.dp else 1.dp,
                            color = if (selectedStyle == WidgetPrefsHelper.STYLE_HALF_RING) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedStyle = WidgetPrefsHelper.STYLE_HALF_RING },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Medio anillo", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedStyle == WidgetPrefsHelper.STYLE_FULL_RING) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
                        .border(
                            width = if (selectedStyle == WidgetPrefsHelper.STYLE_FULL_RING) 2.dp else 1.dp,
                            color = if (selectedStyle == WidgetPrefsHelper.STYLE_FULL_RING) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedStyle = WidgetPrefsHelper.STYLE_FULL_RING },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Anillo completo", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Selector de Tema (3 Opciones: Claro, Oscuro OLED, Sistema)
            Text(
                text = "TEMA DE FONDO",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E8E93),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Claro
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedTheme == WidgetPrefsHelper.THEME_LIGHT) Color(0xFFF2F2F7) else Color(0xFF1C1C1E))
                        .border(
                            width = if (selectedTheme == WidgetPrefsHelper.THEME_LIGHT) 2.dp else 1.dp,
                            color = if (selectedTheme == WidgetPrefsHelper.THEME_LIGHT) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedTheme = WidgetPrefsHelper.THEME_LIGHT },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Claro", color = if (selectedTheme == WidgetPrefsHelper.THEME_LIGHT) Color.Black else Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }

                // 2. Oscuro (OLED)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedTheme == WidgetPrefsHelper.THEME_OLED_DARK) Color.Black else Color(0xFF1C1C1E))
                        .border(
                            width = if (selectedTheme == WidgetPrefsHelper.THEME_OLED_DARK) 2.dp else 1.dp,
                            color = if (selectedTheme == WidgetPrefsHelper.THEME_OLED_DARK) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedTheme = WidgetPrefsHelper.THEME_OLED_DARK },
                    contentAlignment = Alignment.Center
                ) {
                    Text("OLED", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }

                // 3. Sistema
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedTheme == WidgetPrefsHelper.THEME_SYSTEM) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
                        .border(
                            width = if (selectedTheme == WidgetPrefsHelper.THEME_SYSTEM) 2.dp else 1.dp,
                            color = if (selectedTheme == WidgetPrefsHelper.THEME_SYSTEM) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedTheme = WidgetPrefsHelper.THEME_SYSTEM },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sistema", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Slider de Transparencia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSPARENCIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8E8E93),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${alphaPercent.toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(2.dp))

            Slider(
                value = alphaPercent,
                onValueChange = { alphaPercent = it },
                valueRange = 0f..100f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
        }

        // Botones inferiores
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Text("Cancelar", color = Color.White)
            }

            Button(
                onClick = { onSave(selectedTheme, alphaPercent.toInt(), selectedStyle) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
