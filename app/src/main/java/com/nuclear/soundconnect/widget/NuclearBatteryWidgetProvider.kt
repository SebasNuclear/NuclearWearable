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
import com.nuclear.soundconnect.bluetooth.SonyForegroundService
import com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository
import com.nuclear.soundconnect.ui.MainActivity

/**
 * AppWidgetProvider para el Widget de BATERÍA SOLA estilo Samsung One UI.
 */
class NuclearBatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_BATTERY_REFRESH = "com.nuclear.soundconnect.ACTION_BATTERY_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NuclearBatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, NuclearBatteryWidgetProvider::class.java).apply {
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
            ACTION_BATTERY_REFRESH -> {
                performHapticFeedback(context)
                SonyForegroundService.startSync(context)
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
                // Al desbloquear pantalla, refrescar el widget adaptativamente
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
        val views = RemoteViews(context.packageName, R.layout.widget_nuclear_battery)
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

        // 3. Renderizado de las 3 Columnas de Batería con distribución horizontal 100% responsiva One UI
        val drawables = BatteryRingCanvas.getDrawablesForFamily(state.modelFamily)
        val textSecondary = if (isDark) 0xFFD1D1D6.toInt() else 0xFF707070.toInt()
        val labelColor = if (isDark) Color.WHITE else 0xFF6C6C70.toInt()
        val widgetStyle = WidgetPrefsHelper.getWidgetStyle(context, appWidgetId)
        val accentColor = WidgetPrefsHelper.getAccentColor(context)

        if (state.deviceType == com.nuclear.soundconnect.bluetooth.DeviceType.OVER_EAR) {
            // WH Diadema: Solo columna central visible con tamaño optimizado y proporciones ampliadas
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

        // 4. Clic al widget para abrir la app (tanto en fondo como en el área de audífonos)
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPending = PendingIntent.getActivity(
            context,
            202,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, appPending)
        views.setOnClickPendingIntent(R.id.battery_columns_container, appPending)
        views.setOnClickPendingIntent(R.id.header_device_info, appPending)

        return views
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
