package com.nuclear.soundconnect.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.nuclear.soundconnect.R
import com.nuclear.soundconnect.bluetooth.BatteryInfo
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState

/**
 * Generador en Canvas de alta fidelidad para los anillos de batería estilo Samsung Galaxy Buds / One UI.
 * Produce un Bitmap nítido con StrokeCap.Round, tintado condicional y renderizado vectorial directo.
 */
object BatteryRingCanvas {

    // Paleta oficial Samsung One UI
    private const val COLOR_ONEUI_GREEN = 0xFF34C759.toInt()  // Verde estándar Samsung (> 20%)
    private const val COLOR_ONEUI_AMBER = 0xFFFF9500.toInt()  // Ámbar advertencia (11% - 20%)
    private const val COLOR_ONEUI_RED = 0xFFFF3B30.toInt()    // Rojo crítico (<= 10%)
    private const val COLOR_TRACK_DARK = 0x24FFFFFF           // Pista circular sutil modo oscuro
    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF8E8E93.toInt()

    fun getBatteryColor(level: Int): Int {
        return when {
            level > 20 -> COLOR_ONEUI_GREEN
            level > 10 -> COLOR_ONEUI_AMBER
            else -> COLOR_ONEUI_RED
        }
    }

    data class ModelDrawables(
        val leftBud: Int,
        val rightBud: Int,
        val case: Int,
        val headphone: Int
    )

    fun getDrawablesForFamily(family: com.nuclear.soundconnect.bluetooth.SonyModelFamily): ModelDrawables {
        return when (family) {
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_1000XM6 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_1000XM5 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm5_bud_left,
                rightBud = R.drawable.ic_wf1000xm5_bud_right,
                case = R.drawable.ic_wf1000xm5_case,
                headphone = R.drawable.ic_wh1000xm5_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_1000XM4 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm4_bud_left,
                rightBud = R.drawable.ic_wf1000xm4_bud_right,
                case = R.drawable.ic_wf1000xm4_case,
                headphone = R.drawable.ic_wh1000xm4_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_1000XM3 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm3_bud_left,
                rightBud = R.drawable.ic_wf1000xm3_bud_right,
                case = R.drawable.ic_wf1000xm3_case,
                headphone = R.drawable.ic_wh1000xm3_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_1000XM6 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_1000XM5 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm5_bud_left,
                rightBud = R.drawable.ic_wf1000xm5_bud_right,
                case = R.drawable.ic_wf1000xm5_case,
                headphone = R.drawable.ic_wh1000xm5_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_1000XM4 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm4_bud_left,
                rightBud = R.drawable.ic_wf1000xm4_bud_right,
                case = R.drawable.ic_wf1000xm4_case,
                headphone = R.drawable.ic_wh1000xm4_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_1000XM3 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm3_bud_left,
                rightBud = R.drawable.ic_wf1000xm3_bud_right,
                case = R.drawable.ic_wf1000xm3_case,
                headphone = R.drawable.ic_wh1000xm3_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.LINKBUDS_OPEN -> ModelDrawables(
                leftBud = R.drawable.ic_linkbuds_open_bud_left,
                rightBud = R.drawable.ic_linkbuds_open_bud_right,
                case = R.drawable.ic_linkbuds_open_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.LINKBUDS_S -> ModelDrawables(
                leftBud = R.drawable.ic_linkbuds_s_bud_left,
                rightBud = R.drawable.ic_linkbuds_s_bud_right,
                case = R.drawable.ic_linkbuds_s_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_ULT900N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_ultwear_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_CH720N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whch720n_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_CH520 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whch520_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_XB910N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whxb910n_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_C700N -> ModelDrawables(
                leftBud = R.drawable.ic_wfc700n_bud_left,
                rightBud = R.drawable.ic_wfc700n_bud_right,
                case = R.drawable.ic_wfc700n_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_C500 -> ModelDrawables(
                leftBud = R.drawable.ic_wfc500_bud_left,
                rightBud = R.drawable.ic_wfc500_bud_right,
                case = R.drawable.ic_wfc500_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WF_SP800N -> ModelDrawables(
                leftBud = R.drawable.ic_wfsp800n_bud_left,
                rightBud = R.drawable.ic_wfsp800n_bud_right,
                case = R.drawable.ic_wfsp800n_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WI_1000X -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wi1000x_neckband
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WI_C100 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wic100_neckband
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.INZONE_BUDS -> ModelDrawables(
                leftBud = R.drawable.ic_inzone_buds_bud_left,
                rightBud = R.drawable.ic_inzone_buds_bud_right,
                case = R.drawable.ic_inzone_buds_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.MDR_XB950 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_mdrxb950_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.WH_H910N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whh910n_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.GENERIC_OVER_EAR -> ModelDrawables(
                leftBud = R.drawable.ic_generic_bud_left,
                rightBud = R.drawable.ic_generic_bud_right,
                case = R.drawable.ic_generic_case,
                headphone = R.drawable.ic_generic_headphone
            )
            com.nuclear.soundconnect.bluetooth.SonyModelFamily.GENERIC_TWS -> ModelDrawables(
                leftBud = R.drawable.ic_generic_bud_left,
                rightBud = R.drawable.ic_generic_bud_right,
                case = R.drawable.ic_generic_case,
                headphone = R.drawable.ic_generic_headphone
            )
        }
    }

    private fun drawBoldDrawable(
        canvas: Canvas,
        drawable: android.graphics.drawable.Drawable,
        left: Int,
        top: Int,
        size: Int,
        @Suppress("UNUSED_PARAMETER") isBold: Boolean
    ) {
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }

    /**
     * Renderiza un Bitmap cuadrado para una columna individual (L, R, Estuche o Diadema).
     * Soporta tres estilos configurables:
     * - STYLE_MINIMAL (0): Diseño moderno sin anillos. Icono destacado arriba, porcentaje directo en negrita abajo.
     * - STYLE_HALF_RING (1): Medio anillo con extremos redondeados, icono espacioso y porcentaje en la abertura inferior.
     * - STYLE_FULL_RING (2): Anillo completo circular de 360°, con icono y porcentaje simétricos.
     *
     * Regla de widgets: Tanto conectados como desconectados mantienen exactamente el mismo brillo (sin atenuar)
     * y el mismo grosor limpio; la conexión se indica por la aparición del porcentaje y/o del anillo.
     */
    fun renderColumnBitmap(
        context: Context,
        battery: BatteryInfo,
        iconRes: Int,
        isConnected: Boolean,
        isDark: Boolean = true,
        sizePx: Int = 240,
        isOverEar: Boolean = false,
        style: Int = WidgetPrefsHelper.STYLE_MINIMAL,
        accentColor: Int = COLOR_ONEUI_GREEN
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val textPrimary = if (isDark) Color.WHITE else 0xFF1C1C1E.toInt()
        val iconTint = if (isDark) Color.WHITE else 0xFF1C1C1E.toInt()
        val trackColor = if (isDark) COLOR_TRACK_DARK else 0x1A000000

        val level = battery.level.coerceIn(0, 100)
        val isItemConnected = isConnected && battery.isAvailable

        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (drawable != null) {
            drawable.setTint(iconTint)
            drawable.alpha = 255
        }

        when (style) {
            WidgetPrefsHelper.STYLE_MINIMAL -> {
                // ==========================================
                // ESTILO 0: MINIMALISTA (SIN ANILLOS)
                // ==========================================
                val iconScale = if (isOverEar) 0.72f else 0.66f
                val iconSize = (sizePx * iconScale).toInt()
                val iconLeft = (cx - iconSize / 2f).toInt()
                val iconTop = (sizePx * 0.04f).toInt()

                if (drawable != null) {
                    drawBoldDrawable(canvas, drawable, iconLeft, iconTop, iconSize, isItemConnected)
                }

                if (isItemConnected) {
                    val percentText = "$level%"
                    val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textPrimary
                        textSize = sizePx * 0.19f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val textY = sizePx * 0.92f
                    canvas.drawText(percentText, cx, textY, percentPaint)

                    if (battery.isCharging) {
                        val boltDrawable = ContextCompat.getDrawable(context, R.drawable.ic_charging_bolt)?.mutate()
                        if (boltDrawable != null) {
                            val boltSize = (sizePx * 0.15f).toInt()
                            val textBounds = Rect()
                            percentPaint.getTextBounds(percentText, 0, percentText.length, textBounds)
                            val boltLeft = (cx + textBounds.width() / 2f + 4f).toInt()
                            val boltTop = (textY - boltSize * 0.92f).toInt()
                            boltDrawable.setTint(accentColor)
                            boltDrawable.setBounds(boltLeft, boltTop, boltLeft + boltSize, boltTop + boltSize)
                            boltDrawable.draw(canvas)
                        }
                    }
                }
            }

            WidgetPrefsHelper.STYLE_HALF_RING -> {
                // ==========================================
                // ESTILO 1: MEDIO ANILLO (ARCO CON EXTREMOS REDONDEADOS)
                // ==========================================
                val radius = if (isOverEar) sizePx * 0.44f else sizePx * 0.42f
                val strokeWidth = if (isOverEar) 11f else 9f
                val arcBounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val startAngle = 145f
                val maxSweep = 250f

                val iconScale = if (isOverEar) 0.58f else 0.52f
                val iconSize = (sizePx * iconScale).toInt()
                val iconLeft = (cx - iconSize / 2f).toInt()
                val iconTop = (cy - radius * 0.68f).toInt()

                if (isItemConnected) {
                    // Pista de fondo del medio anillo (250 grados)
                    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.STROKE
                        this.strokeWidth = strokeWidth
                        color = trackColor
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawArc(arcBounds, startAngle, maxSweep, false, trackPaint)

                    // Arco de progreso con extremos redondeados
                    if (level > 0) {
                        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.style = Paint.Style.STROKE
                            this.strokeWidth = strokeWidth
                            color = accentColor
                            strokeCap = Paint.Cap.ROUND
                        }
                        val sweep = (level / 100f) * maxSweep
                        canvas.drawArc(arcBounds, startAngle, sweep, false, progressPaint)
                    }

                    // Icono amplio en el centro del medio anillo (trazo grueso)
                    if (drawable != null) {
                        drawBoldDrawable(canvas, drawable, iconLeft, iconTop, iconSize, true)
                    }

                    // Porcentaje posicionado en la abertura inferior del medio anillo
                    val percentText = "$level%"
                    val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textPrimary
                        textSize = sizePx * 0.17f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val textY = cy + radius * 0.90f
                    canvas.drawText(percentText, cx, textY, percentPaint)

                    if (battery.isCharging) {
                        val boltDrawable = ContextCompat.getDrawable(context, R.drawable.ic_charging_bolt)?.mutate()
                        if (boltDrawable != null) {
                            val boltSize = (sizePx * 0.13f).toInt()
                            val textBounds = Rect()
                            percentPaint.getTextBounds(percentText, 0, percentText.length, textBounds)
                            val boltLeft = (cx + textBounds.width() / 2f + 3f).toInt()
                            val boltTop = (textY - boltSize * 0.90f).toInt()
                            boltDrawable.setTint(accentColor)
                            boltDrawable.setBounds(boltLeft, boltTop, boltLeft + boltSize, boltTop + boltSize)
                            boltDrawable.draw(canvas)
                        }
                    }
                } else {
                    // Desconectado: Mismo icono en la misma posición, trazo fino, 100% brillo
                    if (drawable != null) {
                        drawBoldDrawable(canvas, drawable, iconLeft, iconTop, iconSize, false)
                    }
                }
            }

            else -> {
                // ==========================================
                // ESTILO 2: ANILLO COMPLETO (360° SIMÉTRICO)
                // ==========================================
                val radius = if (isOverEar) sizePx * 0.42f else sizePx * 0.40f
                val strokeWidth = if (isOverEar) 10f else 8.5f
                val iconScale = if (isOverEar) 0.82f else 0.78f
                val iconSize = (radius * iconScale).toInt()
                val iconLeft = (cx - iconSize / 2f).toInt()
                val iconTop = (cy - radius * 0.72f).toInt()

                if (isItemConnected) {
                    // Pista 360°
                    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.STROKE
                        this.strokeWidth = strokeWidth
                        color = trackColor
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawCircle(cx, cy, radius, trackPaint)

                    // Arco de progreso
                    if (level > 0) {
                        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.style = Paint.Style.STROKE
                            this.strokeWidth = strokeWidth
                            color = accentColor
                            strokeCap = Paint.Cap.ROUND
                        }
                        val sweepAngle = (level / 100f) * 360f
                        val arcBounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                        canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint)
                    }

                    // Icono grueso arriba
                    if (drawable != null) {
                        drawBoldDrawable(canvas, drawable, iconLeft, iconTop, iconSize, true)
                    }

                    // Porcentaje abajo
                    val percentText = "$level%"
                    val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textPrimary
                        textSize = radius * 0.38f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val textY = cy + radius * 0.54f
                    canvas.drawText(percentText, cx, textY, percentPaint)

                    if (battery.isCharging) {
                        val boltDrawable = ContextCompat.getDrawable(context, R.drawable.ic_charging_bolt)?.mutate()
                        if (boltDrawable != null) {
                            val boltSize = (radius * 0.28f).toInt()
                            val textBounds = Rect()
                            percentPaint.getTextBounds(percentText, 0, percentText.length, textBounds)
                            val boltLeft = (cx + textBounds.width() / 2f + 4f).toInt()
                            val boltTop = (textY - boltSize).toInt()
                            boltDrawable.setTint(accentColor)
                            boltDrawable.setBounds(boltLeft, boltTop, boltLeft + boltSize, boltTop + boltSize)
                            boltDrawable.draw(canvas)
                        }
                    }
                } else {
                    // Desconectado: Misma posición y tamaño simétrico que conectado, trazo fino, 100% brillo
                    if (drawable != null) {
                        drawBoldDrawable(canvas, drawable, iconLeft, iconTop, iconSize, false)
                    }
                }
            }
        }

        return bitmap
    }

    /**
     * Dibuja los medidores circulares de batería sobre un Canvas y devuelve un Bitmap listo para RemoteViews.
     */
    fun renderBatteryRingsBitmap(
        context: Context,
        state: HeadsetState,
        isDark: Boolean = true,
        widthPx: Int = 820,
        heightPx: Int = 260
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawables = getDrawablesForFamily(state.modelFamily)

        val textPrimary = if (isDark) Color.WHITE else 0xFF1C1C1E.toInt()
        val textSecondary = if (isDark) 0xFF8E8E93.toInt() else 0xFF6C6C70.toInt()
        val iconTint = if (isDark) Color.WHITE else 0xFF222222.toInt()
        val trackColor = if (isDark) COLOR_TRACK_DARK else 0x1A000000

        if (state.deviceType == DeviceType.OVER_EAR) {
            // WH-1000XM Diadema: Un solo audífono central prominente
            renderSingleRing(
                context = context,
                canvas = canvas,
                cx = widthPx / 2f,
                cy = heightPx * 0.44f,
                radius = heightPx * 0.34f,
                strokeWidth = 12f,
                battery = state.singleBattery,
                iconRes = drawables.headphone,
                label = "Diadema",
                isConnected = state.isConnected,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = iconTint,
                trackColor = trackColor
            )
        } else {
            // WF-1000XM TWS: 3 columnas idénticas al widget oficial de Samsung Galaxy Buds (L, R, Estuche)
            val ringRadius = heightPx * 0.32f
            val strokeWidth = 10f
            val cy = heightPx * 0.42f

            val xLeft = widthPx * 0.20f
            val xRight = widthPx * 0.50f
            val xCase = widthPx * 0.80f

            // 1. Auricular Izquierdo (L)
            renderSingleRing(
                context = context,
                canvas = canvas,
                cx = xLeft,
                cy = cy,
                radius = ringRadius,
                strokeWidth = strokeWidth,
                battery = state.leftBattery,
                iconRes = drawables.leftBud,
                label = "L",
                isConnected = state.isConnected,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = iconTint,
                trackColor = trackColor
            )

            // 2. Auricular Derecho (R)
            renderSingleRing(
                context = context,
                canvas = canvas,
                cx = xRight,
                cy = cy,
                radius = ringRadius,
                strokeWidth = strokeWidth,
                battery = state.rightBattery,
                iconRes = drawables.rightBud,
                label = "R",
                isConnected = state.isConnected,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = iconTint,
                trackColor = trackColor
            )

            // 3. Estuche de Carga (Case)
            renderSingleRing(
                context = context,
                canvas = canvas,
                cx = xCase,
                cy = cy,
                radius = ringRadius,
                strokeWidth = strokeWidth,
                battery = state.caseBattery,
                iconRes = drawables.case,
                label = "Estuche",
                isConnected = state.isConnected,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = iconTint,
                trackColor = trackColor
            )
        }

        return bitmap
    }

    private fun renderSingleRing(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        strokeWidth: Float,
        battery: BatteryInfo,
        iconRes: Int,
        label: String,
        isConnected: Boolean,
        textPrimary: Int,
        textSecondary: Int,
        iconTint: Int,
        trackColor: Int
    ) {
        val level = battery.level.coerceIn(0, 100)

        if (isConnected) {
            // Pintura de la Pista (Track) de fondo
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = trackColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawCircle(cx, cy, radius, trackPaint)

            // Arco de porcentaje con color dinámico One UI
            if (level > 0) {
                val progressColor = getBatteryColor(level)
                val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth
                    color = progressColor
                    strokeCap = Paint.Cap.ROUND
                }
                val sweepAngle = (level / 100f) * 360f
                val arcBounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint)
            }

            // Ícono Vectorial en la mitad superior del anillo
            val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
            if (drawable != null) {
                val iconSize = (radius * 0.90f).toInt()
                val iconLeft = (cx - iconSize / 2f).toInt()
                val iconTop = (cy - radius * 0.65f).toInt()
                drawable.setTint(iconTint)
                drawable.alpha = 255
                drawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                drawable.draw(canvas)
            }

            // Porcentaje de Batería con tipografía bold One UI
            val percentText = if (battery.isAvailable) "$level%" else "--%"
            val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary
                textSize = radius * 0.38f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val textY = cy + radius * 0.40f
            canvas.drawText(percentText, cx, textY, percentPaint)

            // Si está cargando, dibujar el rayo de carga junto al número
            if (battery.isCharging) {
                val boltDrawable = ContextCompat.getDrawable(context, R.drawable.ic_charging_bolt)?.mutate()
                if (boltDrawable != null) {
                    val boltSize = (radius * 0.30f).toInt()
                    val textBounds = Rect()
                    percentPaint.getTextBounds(percentText, 0, percentText.length, textBounds)
                    val boltLeft = (cx + textBounds.width() / 2f + 4f).toInt()
                    val boltTop = (textY - boltSize).toInt()
                    boltDrawable.setBounds(boltLeft, boltTop, boltLeft + boltSize, boltTop + boltSize)
                    boltDrawable.draw(canvas)
                }
            }

            // Etiqueta debajo del anillo (L, R, Estuche)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary
                textSize = radius * 0.32f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, cx, cy + radius + strokeWidth + 24f, labelPaint)

        } else {
            // DESCONECTADO:
            // Icono grande (+18% tamaño), limpio, sin anillos estorbosos, y etiqueta nítida abajo
            val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
            if (drawable != null) {
                val iconSize = (radius * 1.82f).toInt()
                val iconLeft = (cx - iconSize / 2f).toInt()
                val iconTop = (cy - iconSize / 2f - 4f).toInt()
                drawable.setTint(iconTint)
                drawable.alpha = 255
                drawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                drawable.draw(canvas)
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary
                textSize = radius * 0.38f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, cx, cy + radius * 0.95f, labelPaint)
        }
    }
}
