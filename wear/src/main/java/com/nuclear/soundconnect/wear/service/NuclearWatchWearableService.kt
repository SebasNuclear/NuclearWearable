package com.nuclear.soundconnect.wear.service

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.nuclear.soundconnect.wear.NuclearWearProtocol
import com.nuclear.soundconnect.wear.data.WearConnectionMode
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.tile.NuclearTileService

/**
 * Servicio en segundo plano para Wear OS que recibe actualizaciones de estado
 * desde el teléfono móvil e invalida el Tile para mantenerlo siempre al día.
 */
class NuclearWatchWearableService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == NuclearWearProtocol.PATH_STATE) {
                    val dataMapItem = DataMapItem.fromDataItem(item)
                    val phoneState = NuclearWearProtocol.parseDataMap(dataMapItem.dataMap)
                    Log.d(TAG, "Estado recibido en segundo plano en reloj: ${phoneState.deviceName} (${phoneState.noiseMode})")

                    val repository = WearHeadsetRepository.getInstance(applicationContext)
                    if (repository.connectionMode.value != WearConnectionMode.STANDALONE) {
                        repository.updateState(phoneState)
                    }

                    // Solicitar actualización inmediata del Tile
                    try {
                        TileService.getUpdater(applicationContext)
                            .requestUpdate(NuclearTileService::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo actualizar el Tile: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NuclearWatchService"
    }
}
