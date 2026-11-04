package com.nuclear.soundconnect.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nuclear.soundconnect.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano ligero bajo demanda para ejecutar transacciones RFCOMM
 * sin sufrir cancelaciones por ahorro de energía de Android.
 */
class SonyForegroundService : Service() {

    companion object {
        const val ACTION_SYNC = "com.nuclear.soundconnect.service.SYNC"
        const val ACTION_SET_MODE = "com.nuclear.soundconnect.service.SET_MODE"
        const val EXTRA_MODE = "extra_mode"

        private const val CHANNEL_ID = "sony_service_channel"
        private const val NOTIF_ID = 2001

        fun startSync(context: Context) {
            val intent = Intent(context, SonyForegroundService::class.java).apply {
                action = ACTION_SYNC
            }
            startServiceCompat(context, intent)
        }

        fun startSetMode(context: Context, mode: NoiseControlMode) {
            val intent = Intent(context, SonyForegroundService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra(EXTRA_MODE, mode.name)
            }
            startServiceCompat(context, intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val notification = buildNotification("Sincronizando con audífonos…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("SonyForegroundService", "startForeground failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = SonyHeadsetRepository.getInstance(applicationContext)

        when (intent?.action) {
            ACTION_SYNC -> {
                serviceScope.launch {
                    repo.syncTelemetryAsync {
                        stopSelf()
                    }
                }
            }
            ACTION_SET_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: NoiseControlMode.ANC.name
                val mode = runCatching { NoiseControlMode.valueOf(modeStr) }.getOrDefault(NoiseControlMode.ANC)
                serviceScope.launch {
                    repo.setNoiseModeAsync(mode) {
                        stopSelf()
                    }
                }
            }
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexión de Audífonos Sony",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación breve mientras se sincroniza el estado de los audífonos"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("NotificationTrampoline")
    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_anc_on)
            .setContentTitle("NuclearWearable")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }
}
