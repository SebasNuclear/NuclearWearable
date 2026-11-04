package com.nuclear.soundconnect.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository

/**
 * Servicio en segundo plano para el teléfono móvil que intercepta comandos
 * enviados desde el reloj Wear OS mediante Google Play Services MessageClient.
 */
class NuclearPhoneWearableService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val path = messageEvent.path
        val data = messageEvent.data

        Log.d(TAG, "Mensaje recibido desde Wear OS: $path (${data.size} bytes)")

        val repository = SonyHeadsetRepository.getInstance(applicationContext)

        when (path) {
            NuclearWearProtocol.PATH_CMD_SET_NOISE_MODE -> {
                if (data.isNotEmpty()) {
                    val targetMode = NuclearWearProtocol.byteToNoiseControlMode(data[0])
                    Log.i(TAG, "Comando Wear OS: Cambiar modo de ruido a $targetMode")
                    repository.setNoiseModeAsync(targetMode) { success ->
                        Log.d(TAG, "Resultado cambio modo desde Wear OS: $success")
                        if (success) {
                            NuclearPhoneWearableSync.syncStateToWear(applicationContext, repository.state.value)
                        }
                    }
                }
            }

            NuclearWearProtocol.PATH_CMD_SET_AMBIENT_CONFIG -> {
                if (data.size >= 2) {
                    val (level, voiceFocus) = NuclearWearProtocol.parseAmbientConfigPayload(data)
                    Log.i(TAG, "Comando Wear OS: Configurar Sonido Ambiente (Nivel: $level, Voz: $voiceFocus)")
                    repository.setAmbientConfig(level, voiceFocus)
                }
            }

            NuclearWearProtocol.PATH_CMD_REQUEST_SYNC -> {
                Log.i(TAG, "Comando Wear OS: Solicitud de sincronización de batería")
                // Enviar inmediatamente la telemetría conocida en caché
                NuclearPhoneWearableSync.syncStateToWear(applicationContext, repository.state.value)

                // Ejecutar sondeo RFCOMM fresco contra los audífonos
                repository.syncTelemetryAsync(silent = false) { success ->
                    Log.d(TAG, "Resultado sync bajo demanda desde Wear OS: $success")
                    NuclearPhoneWearableSync.syncStateToWear(applicationContext, repository.state.value)
                }
            }

            else -> {
                Log.w(TAG, "Ruta no reconocida desde Wear OS: $path")
            }
        }
    }

    companion object {
        private const val TAG = "NuclearPhoneWearService"
    }
}
