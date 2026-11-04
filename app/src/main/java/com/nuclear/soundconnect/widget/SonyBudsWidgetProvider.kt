package com.nuclear.soundconnect.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothDevice
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
import com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository
import com.nuclear.soundconnect.ui.MainActivity

/**
 * AppWidgetProvider para el Widget estilo Samsung Galaxy Buds / One UI (4x2 Dúo).
 * Gestiona eventos táctiles, retroalimentación háptica, cambios de conexión Bluetooth
 * y actualización en tiempo real de los anillos de batería y control de ruido.
 */
class SonyBudsWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.nuclear.soundconnect.ACTION_REFRESH"
        const val ACTION_SET_ANC = "com.nuclear.soundconnect.ACTION_SET_ANC"
        const val ACTION_SET_OFF = "com.nuclear.soundconnect.ACTION_SET_OFF"
        const val ACTION_SET_AMBIENT = "com.nuclear.soundconnect.ACTION_SET_AMBIENT"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SonyBudsWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, SonyBudsWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = SonyHeadsetRepository.getInstance(context)
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
        val repo = SonyHeadsetRepository.getInstance(context)
        val views = buildRemoteViews(context, repo.state.value, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val repo = SonyHeadsetRepository.getInstance(context)

        when (intent.action) {
            ACTION_REFRESH -> {
                performHapticFeedback(context)
                SonyForegroundService.startSync(context)
            }
            ACTION_SET_ANC -> {
                performHapticFeedback(context)
                SonyForegroundService.startSetMode(context, NoiseControlMode.ANC)
            }
            ACTION_SET_OFF -> {
                performHapticFeedback(context)
                SonyForegroundService.startSetMode(context, NoiseControlMode.OFF)
            }
            ACTION_SET_AMBIENT -> {
                performHapticFeedback(context)
                SonyForegroundService.startSetMode(context, NoiseControlMode.AMBIENT)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    if (repo.isSonyAudioDevice(it)) {
                        repo.handleAclConnectionChange(it, true)
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    if (repo.isSonyAudioDevice(it)) {
                        repo.handleAclConnectionChange(it, false)
                    }
                }
            }
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device != null && level in 0..100 && repo.isSonyAudioDevice(device)) {
                    repo.handleSystemBatteryUpdate(device, level, intent)
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                // Al desbloquear el usuario (regreso a Home), sincronizar sin drenar batería con pantalla apagada
                val state = repo.state.value
                if (state.isConnected && System.currentTimeMillis() - state.lastSyncTime > 5_000L) {
                    SonyForegroundService.startSync(context)
                } else {
                    updateAllWidgets(context)
                }
            }
        }
    }

    private fun buildRemoteViews(context: Context, state: HeadsetState, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_sony_buds_4x2)
        val isDark = WidgetPrefsHelper.isDark(context, appWidgetId)

        // 1. Capa de Fondo Squircle Adaptativa al tamaño del widget (Evita deformaciones en esquinas)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val density = context.resources.displayMetrics.density
        val widthPx = ((if (minWidthDp > 0) minWidthDp else 250) * density).toInt().coerceIn(300, 1600)
        val heightPx = ((if (minHeightDp > 0) minHeightDp else 110) * density).toInt().coerceIn(160, 1200)

        val bgBitmap = WidgetPrefsHelper.renderBackgroundBitmap(context, appWidgetId, widthPx = widthPx, heightPx = heightPx)
        views.setImageViewBitmap(R.id.iv_widget_bg, bgBitmap)

        // 2. Cabecera (Nombre centrado con color según tema)
        views.setTextViewText(R.id.tv_device_name, state.deviceName)
        views.setTextColor(R.id.tv_device_name, if (isDark) Color.WHITE else 0xFF1C1C1E.toInt())

        // 3. Renderizado de las 3 Columnas de Batería con distribución horizontal 100% responsiva One UI
        val drawables = BatteryRingCanvas.getDrawablesForFamily(state.modelFamily)
        val textSecondary = if (isDark) 0xFFD1D1D6.toInt() else 0xFF707070.toInt()
        val labelColor = if (isDark) Color.WHITE else 0xFF6C6C70.toInt()
        val widgetStyle = WidgetPrefsHelper.getWidgetStyle(context, appWidgetId)
        val accentColor = WidgetPrefsHelper.getAccentColor(context)

        if (state.deviceType == com.nuclear.soundconnect.bluetooth.DeviceType.OVER_EAR) {
            views.setViewVisibility(R.id.col_left, android.view.View.GONE)
            views.setViewVisibility(R.id.col_right, android.view.View.GONE)
            views.setViewVisibility(R.id.col_center, android.view.View.VISIBLE)

            val headphoneBitmap = BatteryRingCanvas.renderColumnBitmap(
                context = context,
                battery = state.singleBattery,
                iconRes = drawables.headphone,
                isConnected = state.isConnected && state.singleBattery.isAvailable,
                isDark = isDark,
                sizePx = 320,
                isOverEar = true,
                style = widgetStyle,
                accentColor = accentColor
            )
            views.setImageViewBitmap(R.id.iv_col_center, headphoneBitmap)
            views.setTextViewText(R.id.tv_label_center, "Diadema")
            views.setTextColor(R.id.tv_label_center, labelColor)
        } else {
            // WF TWS: 3 columnas idénticas a Samsung Galaxy Buds (L, R, Estuche)
            // Soporta modo mono / individual: solo muestra anillo y porcentaje en el audífono activo
            views.setViewVisibility(R.id.col_left, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.col_center, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.col_right, android.view.View.VISIBLE)

            val leftBitmap = BatteryRingCanvas.renderColumnBitmap(
                context = context,
                battery = state.leftBattery,
                iconRes = drawables.leftBud,
                isConnected = state.isConnected && state.leftBattery.isAvailable,
                isDark = isDark,
                sizePx = 240,
                style = widgetStyle,
                accentColor = accentColor
            )
            views.setImageViewBitmap(R.id.iv_col_left, leftBitmap)
            views.setTextViewText(R.id.tv_label_left, "L")
            views.setTextColor(R.id.tv_label_left, labelColor)

            val rightBitmap = BatteryRingCanvas.renderColumnBitmap(
                context = context,
                battery = state.rightBattery,
                iconRes = drawables.rightBud,
                isConnected = state.isConnected && state.rightBattery.isAvailable,
                isDark = isDark,
                sizePx = 240,
                style = widgetStyle,
                accentColor = accentColor
            )
            views.setImageViewBitmap(R.id.iv_col_center, rightBitmap)
            views.setTextViewText(R.id.tv_label_center, "R")
            views.setTextColor(R.id.tv_label_center, labelColor)

            val caseBitmap = BatteryRingCanvas.renderColumnBitmap(
                context = context,
                battery = state.caseBattery,
                iconRes = drawables.case,
                isConnected = state.isConnected && state.caseBattery.isAvailable,
                isDark = isDark,
                sizePx = 240,
                style = widgetStyle,
                accentColor = accentColor
            )
            views.setImageViewBitmap(R.id.iv_col_right, caseBitmap)
            views.setTextViewText(R.id.tv_label_right, "Estuche")
            views.setTextColor(R.id.tv_label_right, labelColor)
        }

        // 4. Barra segmentada de Noise Control (Oculta si los audífonos están desconectados o no tienen soporte ANC)
        if (state.isConnected && state.modelFamily.hasAnc) {
            views.setViewVisibility(R.id.noise_control_bar, android.view.View.VISIBLE)

            val isAnc = state.noiseMode == NoiseControlMode.ANC
            val isOff = state.noiseMode == NoiseControlMode.OFF
            val isAmbient = state.noiseMode == NoiseControlMode.AMBIENT

            val inactiveBgRes = if (isDark) R.drawable.pill_segment_inactive else R.drawable.pill_segment_inactive_light
            val inactiveTextColor = if (isDark) Color.WHITE else 0xFF1C1C1E.toInt()
            val inactiveIconColor = if (isDark) Color.WHITE else 0xFF222222.toInt()
            val inactiveTint = if (isDark) 0x18FFFFFF else 0x15000000

            // Configurar ANC
            views.setInt(
                R.id.btn_anc,
                "setBackgroundResource",
                if (isAnc) R.drawable.pill_segment_active else inactiveBgRes
            )
            val ancTint = if (isAnc) accentColor else inactiveTint
            views.setColorStateList(R.id.btn_anc, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(ancTint))
            views.setTextColor(R.id.tv_anc_text, if (isAnc) Color.WHITE else inactiveTextColor)
            views.setInt(R.id.iv_anc_icon, "setColorFilter", if (isAnc) Color.WHITE else inactiveIconColor)

            // Configurar OFF
            views.setInt(
                R.id.btn_off,
                "setBackgroundResource",
                if (isOff) R.drawable.pill_segment_active else inactiveBgRes
            )
            val offTint = if (isOff) accentColor else inactiveTint
            views.setColorStateList(R.id.btn_off, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(offTint))
            views.setTextColor(R.id.tv_off_text, if (isOff) Color.WHITE else inactiveTextColor)
            views.setInt(R.id.iv_off_icon, "setColorFilter", if (isOff) Color.WHITE else inactiveIconColor)

            // Configurar AMBIENTE
            views.setInt(
                R.id.btn_ambient,
                "setBackgroundResource",
                if (isAmbient) R.drawable.pill_segment_active else inactiveBgRes
            )
            val ambTint = if (isAmbient) accentColor else inactiveTint
            views.setColorStateList(R.id.btn_ambient, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(ambTint))
            views.setTextColor(R.id.tv_ambient_text, if (isAmbient) Color.WHITE else inactiveTextColor)
            views.setInt(R.id.iv_ambient_icon, "setColorFilter", if (isAmbient) Color.WHITE else inactiveIconColor)
        } else {
            views.setViewVisibility(R.id.noise_control_bar, android.view.View.GONE)
        }

        // 5. PendingIntents para interactividad táctil
        views.setOnClickPendingIntent(R.id.btn_anc, createBroadcastPendingIntent(context, ACTION_SET_ANC, 102))
        views.setOnClickPendingIntent(R.id.btn_off, createBroadcastPendingIntent(context, ACTION_SET_OFF, 103))
        views.setOnClickPendingIntent(R.id.btn_ambient, createBroadcastPendingIntent(context, ACTION_SET_AMBIENT, 104))

        // Al tocar el widget (fondo, indicador o cabecera), abrir la app
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            100,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
        views.setOnClickPendingIntent(R.id.battery_columns_container, appPendingIntent)
        views.setOnClickPendingIntent(R.id.header_device_info, appPendingIntent)

        return views
    }

    private fun createBroadcastPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, SonyBudsWidgetProvider::class.java).apply {
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
