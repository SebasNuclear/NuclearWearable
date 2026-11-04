package com.nuclear.soundconnect.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils

object WidgetPrefsHelper {
    private const val PREFS_NAME = "nuclear_widget_prefs"
    private const val KEY_PREFIX_ALPHA = "alpha_"
    private const val KEY_PREFIX_THEME = "theme_"
    private const val KEY_PREFIX_STYLE = "style_"

    const val THEME_LIGHT = 0
    const val THEME_OLED_DARK = 1
    const val THEME_SYSTEM = 2

    const val STYLE_MINIMAL = 0    // Minimalista sin anillos
    const val STYLE_HALF_RING = 1  // Medio anillo (arco abierto inferior con extremos redondeados)
    const val STYLE_FULL_RING = 2  // Anillo completo (aro cerrado 360° simétrico)

    const val STYLE_RINGS = 1      // Compatibilidad con código previo

    fun getWidgetStyle(context: Context, widgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("$KEY_PREFIX_STYLE$widgetId", STYLE_MINIMAL)
    }

    fun setWidgetStyle(context: Context, widgetId: Int, style: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("$KEY_PREFIX_STYLE$widgetId", style).apply()
    }

    fun getAccentColor(context: Context): Int {
        val appPrefs = context.getSharedPreferences("nuclear_app_theme_prefs", Context.MODE_PRIVATE)
        val idx = appPrefs.getInt("accent_idx", 0)
        return when (idx) {
            0 -> 0xFF00E676.toInt() // Verde Atómico Nuclear
            1 -> 0xFF10B981.toInt() // Esmeralda
            2 -> 0xFF00B0FF.toInt() // Azul Eléctrico
            3 -> 0xFF007AFF.toInt() // Azul Zafiro One UI
            4 -> 0xFF00D2FF.toInt() // Cian Hielo Ártico
            5 -> 0xFFFFB703.toInt() // Oro Solar
            6 -> 0xFFFFB300.toInt() // Ámbar Retro
            7 -> 0xFFFF5722.toInt() // Magma Solar (Naranja)
            8 -> 0xFFFF1744.toInt() // Rojo Carmesí
            9 -> 0xFFFF007F.toInt() // Rosa Ciberpunk
            10 -> 0xFFA855F7.toInt() // Morado Neón
            11 -> 0xFF8B5CF6.toInt() // Violeta Tóxico
            else -> 0xFF00E676.toInt()
        }
    }

    fun getAlphaPercent(context: Context, widgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("$KEY_PREFIX_ALPHA$widgetId", 90) // 90% default One UI
    }

    fun setAlphaPercent(context: Context, widgetId: Int, alpha: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("$KEY_PREFIX_ALPHA$widgetId", alpha.coerceIn(0, 100)).apply()
    }

    fun getTheme(context: Context, widgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("$KEY_PREFIX_THEME$widgetId", THEME_SYSTEM)
    }

    fun setTheme(context: Context, widgetId: Int, theme: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("$KEY_PREFIX_THEME$widgetId", theme).apply()
    }

    /**
     * Determina si el texto, iconos y elementos del widget deben ser blancos/claros (como en Modo Oscuro).
     * En Samsung One UI:
     * - Si el tema es OLED Oscuro: siempre blanco.
     * - Si el tema es Sistema y el móvil está en modo noche: blanco.
     * - Si el tema es Claro:
     *   * Si la opacidad del fondo es <= 40% (es decir, fondo transparente o semi-transparente):
     *     ¡SE COLOREAN DE BLANCO O COLORES CLAROS COMO SI EL MODO OSCURO ESTUVIERA ACTIVO!
     *   * Solo si el fondo blanco es lo bastante sólido (> 40%), se usa texto oscuro (#1C1C1E).
     */
    fun isDark(context: Context, widgetId: Int): Boolean {
        val alphaPercent = getAlphaPercent(context, widgetId)
        // Regla Samsung One UI: con 40% o menos de opacidad de fondo, se colorean de blanco / colores claros
        // como si el modo oscuro estuviera activo para garantizar legibilidad contra el wallpaper.
        if (alphaPercent <= 40) {
            return true
        }

        val theme = getTheme(context, widgetId)
        return when (theme) {
            THEME_LIGHT -> false
            THEME_OLED_DARK -> true
            THEME_SYSTEM -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
            else -> true
        }
    }

    /**
     * Dibuja la placa de fondo squircle (esquinas 26dp) con el color y la transparencia elegida por el usuario.
     * Esta capa es independiente para NO transparentar el texto ni los iconos del widget.
     */
    fun renderBackgroundBitmap(context: Context, widgetId: Int, widthPx: Int = 800, heightPx: Int = 300): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val alphaPercent = getAlphaPercent(context, widgetId)
        val alpha255 = (alphaPercent * 255 / 100).coerceIn(0, 255)

        if (alpha255 <= 0) {
            return bitmap // Fondo 100% transparente sin bordes
        }

        val theme = getTheme(context, widgetId)
        val isThemeDark = when (theme) {
            THEME_LIGHT -> false
            THEME_OLED_DARK -> true
            THEME_SYSTEM -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
            else -> true
        }

        // OLED negro puro (#000000) o Blanco puro (#FFFFFF)
        val baseColor = if (isThemeDark) Color.BLACK else Color.WHITE
        val fillColor = ColorUtils.setAlphaComponent(baseColor, alpha255)

        val cornerRadius = 60f // ~26dp a escala
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Borde sutil de 1dp estilo One UI solo si hay opacidad suficiente
        if (alpha255 > 25) {
            val borderAlpha = ((if (isThemeDark) 0x24 else 0x1A) * alpha255 / 255).coerceIn(0, 255)
            val borderBase = if (isThemeDark) Color.WHITE else Color.BLACK
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(borderBase, borderAlpha)
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val insetRect = RectF(1.5f, 1.5f, widthPx - 1.5f, heightPx - 1.5f)
            canvas.drawRoundRect(insetRect, cornerRadius, cornerRadius, borderPaint)
        }

        return bitmap
    }
}
