package com.nuclear.soundconnect.wear.presentation.theme

import android.content.Context
import androidx.wear.tiles.TileService
import com.nuclear.soundconnect.wear.tile.NuclearQuickControlsTileService
import com.nuclear.soundconnect.wear.tile.NuclearTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Paleta oficial NuclearUI (12 colores sincronizados con la app móvil).
 */
val NUCLEAR_WEAR_ACCENT_PALETTE: List<Int> = listOf(
    0xFF00E676.toInt(), // Verde Atómico Nuclear
    0xFF10B981.toInt(), // Esmeralda
    0xFF00B0FF.toInt(), // Azul Eléctrico
    0xFF007AFF.toInt(), // Azul Zafiro One UI
    0xFF00D2FF.toInt(), // Cian Hielo Ártico
    0xFFFFB703.toInt(), // Oro Solar
    0xFFFFB300.toInt(), // Ámbar Retro
    0xFFFF5722.toInt(), // Magma Solar
    0xFFFF1744.toInt(), // Rojo Carmesí
    0xFFFF007F.toInt(), // Rosa Ciberpunk
    0xFFA855F7.toInt(), // Morado Neón
    0xFF8B5CF6.toInt()  // Violeta Tóxico
)

data class WatchAccentConfig(
    val usePhoneSync: Boolean = true,
    val customAccentArgb: Int = NUCLEAR_WEAR_ACCENT_PALETTE.first()
)

/**
 * Preferencias locales de interfaz para Wear OS.
 * Permite elegir un color de acento independiente en el reloj sin alterar
 * WearHeadsetRepository ni el protocolo de Data Layer.
 */
object WearUiPrefs {

    private const val PREFS_NAME = "nuclear_wear_ui_prefs"
    private const val KEY_USE_PHONE_SYNC = "use_phone_sync_accent"
    private const val KEY_CUSTOM_ACCENT = "custom_watch_accent_argb"

    @Volatile
    private var initialized = false

    private val _config = MutableStateFlow(WatchAccentConfig())

    fun observeConfig(context: Context): StateFlow<WatchAccentConfig> {
        ensureLoaded(context.applicationContext)
        return _config.asStateFlow()
    }

    fun getConfig(context: Context): WatchAccentConfig {
        ensureLoaded(context.applicationContext)
        return _config.value
    }

    fun resolveAccentColor(context: Context, phoneAccentArgb: Int): Int {
        return resolveAccentColor(phoneAccentArgb, getConfig(context))
    }

    fun resolveAccentColor(phoneAccentArgb: Int, config: WatchAccentConfig): Int {
        return if (config.usePhoneSync) phoneAccentArgb else config.customAccentArgb
    }

    fun setFollowPhone(context: Context) {
        val appContext = context.applicationContext
        val current = getConfig(appContext)
        saveAndNotify(appContext, current.copy(usePhoneSync = true))
    }

    fun setCustomAccent(context: Context, argb: Int) {
        val appContext = context.applicationContext
        saveAndNotify(appContext, WatchAccentConfig(usePhoneSync = false, customAccentArgb = argb))
    }

    private fun ensureLoaded(appContext: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _config.value = WatchAccentConfig(
                usePhoneSync = prefs.getBoolean(KEY_USE_PHONE_SYNC, true),
                customAccentArgb = prefs.getInt(KEY_CUSTOM_ACCENT, NUCLEAR_WEAR_ACCENT_PALETTE.first())
            )
            initialized = true
        }
    }

    private fun saveAndNotify(appContext: Context, updated: WatchAccentConfig) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_PHONE_SYNC, updated.usePhoneSync)
            .putInt(KEY_CUSTOM_ACCENT, updated.customAccentArgb)
            .apply()
        _config.value = updated
        requestAllTileUpdates(appContext)
    }

    fun requestAllTileUpdates(context: Context) {
        val appContext = context.applicationContext
        try {
            val updater = TileService.getUpdater(appContext)
            updater.requestUpdate(NuclearTileService::class.java)
            updater.requestUpdate(NuclearQuickControlsTileService::class.java)
        } catch (_: Exception) {
        }
    }
}
