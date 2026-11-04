package com.nuclear.soundconnect.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nuclear.soundconnect.bluetooth.HeadsetState

/**
 * Gestor de sincronización de datos hacia dispositivos Wear OS.
 * Emite el estado actual de los audífonos hacia el canal /nuclear/state.
 */
object NuclearPhoneWearableSync {
    private const val TAG = "NuclearPhoneWearSync"

    fun syncStateToWear(context: Context, state: HeadsetState) {
        try {
            val putDataMapReq = PutDataMapRequest.create(NuclearWearProtocol.PATH_STATE).apply {
                NuclearWearProtocol.populateDataMap(state, dataMap)
            }
            val request = putDataMapReq.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext)
                .putDataItem(request)
                .addOnSuccessListener {
                    Log.d(TAG, "Telemetría sincronizada con éxito a Wear OS (${state.deviceName}, Conectado: ${state.isConnected})")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "No se pudo sincronizar estado con Wear OS: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparando PutDataMapRequest para Wear OS", e)
        }
    }
}
