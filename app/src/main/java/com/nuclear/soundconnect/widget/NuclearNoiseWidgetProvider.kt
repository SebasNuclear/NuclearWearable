package com.nuclear.soundconnect.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import com.nuclear.soundconnect.R
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyForegroundService
import com.nuclear.soundconnect.ui.MainActivity

/**
 * AppWidgetProvider para el Widget de CONTROL DE RUIDO SOLO estilo Samsung One UI.
 */
class NuclearNoiseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NOISE_ANC = "com.nuclear.soundconnect.ACTION_NOISE_ANC"
        const val ACTION_NOISE_OFF = "com.nuclear.soundconnect.ACTION_NOISE_OFF"
        const val ACTION_NOISE_AMBIENT = "com.nuclear.soundconnect.ACTION_NOISE_AMBIENT"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NuclearNoiseWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, NuclearNoiseWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository.getInstance(context)
        val state = repo.state.value

        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context, state, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val repo = com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository.getInstance(context)
        val views = buildRemoteViews(context, repo.state.value, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NOISE_ANC -> {
                performHapticFeedback(context)
                SonyForegroundService.startSetMode(context, NoiseControlMode.ANC)
            }
            ACTION_NOISE_OFF -> {
                performHapticFeedback(context)
                SonyForegroundService.startSetMode(context, NoiseControlMode.OFF)
            }
            ACTION_NOISE_AMBIENT -> {
                performHapticFeedback(context)
                SonyForegroundService.startSetMode(context, NoiseControlMode.AMBIENT)
            }
            Intent.ACTION_USER_PRESENT -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun buildRemoteViews(context: Context, state: HeadsetState, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_nuclear_noise)
        val isDark = WidgetPrefsHelper.isDark(context, appWidgetId)

        // 1. Capa de Fondo Squircle Adaptativa al tamaño del widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
        val density = context.resources.displayMetrics.density
        val widthPx = ((if (minWidthDp > 0) minWidthDp else 250) * density).toInt().coerceIn(300, 1600)
        val heightPx = ((if (minHeightDp > 0) minHeightDp else 56) * density).toInt().coerceIn(140, 800)

        val bgBitmap = WidgetPrefsHelper.renderBackgroundBitmap(context, appWidgetId, widthPx = widthPx, heightPx = heightPx)
        views.setImageViewBitmap(R.id.iv_widget_bg, bgBitmap)

        // 2. Cabecera (Nombre centrado)
        views.setTextViewText(R.id.tv_device_name, state.deviceName)
        views.setTextColor(R.id.tv_device_name, if (isDark) Color.WHITE else 0xFF1C1C1E.toInt())

        // 3. Píldora segmentada con estética One UI
        val isAnc = state.noiseMode == NoiseControlMode.ANC
        val isOff = state.noiseMode == NoiseControlMode.OFF
        val isAmbient = state.noiseMode == NoiseControlMode.AMBIENT

        val inactiveBgRes = if (isDark) R.drawable.pill_segment_inactive else R.drawable.pill_segment_inactive_light
        val inactiveTextColor = if (isDark) Color.WHITE else 0xFF1C1C1E.toInt()
        val inactiveIconColor = if (isDark) Color.WHITE else 0xFF222222.toInt()
        val inactiveTint = if (isDark) 0x18FFFFFF else 0x15000000
        val accentColor = WidgetPrefsHelper.getAccentColor(context)

        // ANC
        views.setInt(
            R.id.btn_anc,
            "setBackgroundResource",
            if (isAnc) R.drawable.pill_segment_active else inactiveBgRes
        )
        val ancTint = if (isAnc) accentColor else inactiveTint
        views.setColorStateList(R.id.btn_anc, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(ancTint))
        views.setTextColor(R.id.tv_anc_text, if (isAnc) Color.WHITE else inactiveTextColor)
        views.setInt(R.id.iv_anc_icon, "setColorFilter", if (isAnc) Color.WHITE else inactiveIconColor)

        // OFF
        views.setInt(
            R.id.btn_off,
            "setBackgroundResource",
            if (isOff) R.drawable.pill_segment_active else inactiveBgRes
        )
        val offTint = if (isOff) accentColor else inactiveTint
        views.setColorStateList(R.id.btn_off, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(offTint))
        views.setTextColor(R.id.tv_off_text, if (isOff) Color.WHITE else inactiveTextColor)
        views.setInt(R.id.iv_off_icon, "setColorFilter", if (isOff) Color.WHITE else inactiveIconColor)

        // AMBIENTE
        views.setInt(
            R.id.btn_ambient,
            "setBackgroundResource",
            if (isAmbient) R.drawable.pill_segment_active else inactiveBgRes
        )
        val ambTint = if (isAmbient) accentColor else inactiveTint
        views.setColorStateList(R.id.btn_ambient, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(ambTint))
        views.setTextColor(R.id.tv_ambient_text, if (isAmbient) Color.WHITE else inactiveTextColor)
        views.setInt(R.id.iv_ambient_icon, "setColorFilter", if (isAmbient) Color.WHITE else inactiveIconColor)

        // Intents de botones de control
        views.setOnClickPendingIntent(R.id.btn_anc, createBroadcastPendingIntent(context, ACTION_NOISE_ANC, 301))
        views.setOnClickPendingIntent(R.id.btn_off, createBroadcastPendingIntent(context, ACTION_NOISE_OFF, 302))
        views.setOnClickPendingIntent(R.id.btn_ambient, createBroadcastPendingIntent(context, ACTION_NOISE_AMBIENT, 303))

        // Clic al encabezado o fondo para abrir la app
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPending = PendingIntent.getActivity(
            context,
            304,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.header_device_info, appPending)
        views.setOnClickPendingIntent(R.id.widget_root, appPending)

        return views
    }

    private fun createBroadcastPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NuclearNoiseWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun performHapticFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(18)
                }
            }
        } catch (_: Exception) {}
    }
}
