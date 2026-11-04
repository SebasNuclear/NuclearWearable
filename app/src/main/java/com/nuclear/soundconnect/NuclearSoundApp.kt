package com.nuclear.soundconnect

import android.app.Application
import com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository
import com.nuclear.soundconnect.wear.NuclearPhoneWearableSync

class NuclearSoundApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar repositorio, cache local y monitoreo interactivo con pantalla encendida
        val repo = SonyHeadsetRepository.getInstance(this)
        repo.startInteractiveMonitoring()

        // Sincronizar el estado conocido hacia Wear OS
        NuclearPhoneWearableSync.syncStateToWear(this, repo.state.value)
    }
}
