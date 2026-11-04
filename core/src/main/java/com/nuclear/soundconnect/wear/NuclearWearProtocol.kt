package com.nuclear.soundconnect.wear

import com.google.android.gms.wearable.DataMap
import com.nuclear.soundconnect.bluetooth.BatteryInfo
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyModelFamily

/**
 * Protocolo de comunicación bidireccional y serialización de datos
 * entre el teléfono móvil y el reloj inteligente Wear OS vía Google Play Services Wearable.
 */
object NuclearWearProtocol {
    const val PATH_STATE = "/nuclear/state"
    const val PATH_CMD_SET_NOISE_MODE = "/nuclear/cmd/set_noise_mode"
    const val PATH_CMD_SET_AMBIENT_CONFIG = "/nuclear/cmd/set_ambient_config"
    const val PATH_CMD_REQUEST_SYNC = "/nuclear/cmd/request_sync"

    // Claves del DataMap sincronizado
    const val KEY_CONNECTED = "connected"
    const val KEY_DEVICE_NAME = "device_name"
    const val KEY_DEVICE_ADDRESS = "device_address"
    const val KEY_DEVICE_TYPE = "device_type"
    const val KEY_MODEL_FAMILY = "model_family"
    const val KEY_HAS_ANC = "has_anc"
    const val KEY_BAT_LEFT = "bat_left"
    const val KEY_BAT_LEFT_CHG = "bat_left_chg"
    const val KEY_BAT_RIGHT = "bat_right"
    const val KEY_BAT_RIGHT_CHG = "bat_right_chg"
    const val KEY_BAT_CASE = "bat_case"
    const val KEY_BAT_CASE_CHG = "bat_case_chg"
    const val KEY_BAT_SINGLE = "bat_single"
    const val KEY_BAT_SINGLE_CHG = "bat_single_chg"
    const val KEY_NOISE_MODE = "noise_mode"
    const val KEY_AMBIENT_LEVEL = "ambient_level"
    const val KEY_VOICE_FOCUS = "voice_focus"
    const val KEY_ACCENT_COLOR = "accent_color"
    const val KEY_LAST_SYNC = "last_sync"
    const val KEY_TIMESTAMP = "timestamp"

    // Comandos de byte para SET_NOISE_MODE
    const val BYTE_ANC: Byte = 0
    const val BYTE_OFF: Byte = 1
    const val BYTE_AMBIENT: Byte = 2

    fun noiseControlModeToByte(mode: NoiseControlMode): Byte {
        return when (mode) {
            NoiseControlMode.ANC -> BYTE_ANC
            NoiseControlMode.OFF -> BYTE_OFF
            NoiseControlMode.AMBIENT -> BYTE_AMBIENT
        }
    }

    fun byteToNoiseControlMode(byte: Byte): NoiseControlMode {
        return when (byte) {
            BYTE_ANC -> NoiseControlMode.ANC
            BYTE_OFF -> NoiseControlMode.OFF
            BYTE_AMBIENT -> NoiseControlMode.AMBIENT
            else -> NoiseControlMode.ANC
        }
    }

    fun buildAmbientConfigPayload(level: Int, voiceFocus: Boolean): ByteArray {
        val clampedLevel = level.coerceIn(1, 20).toByte()
        val vfByte: Byte = if (voiceFocus) 1 else 0
        return byteArrayOf(clampedLevel, vfByte)
    }

    fun parseAmbientConfigPayload(data: ByteArray): Pair<Int, Boolean> {
        if (data.size < 2) return Pair(20, false)
        val level = data[0].toInt().coerceIn(1, 20)
        val voiceFocus = data[1].toInt() != 0
        return Pair(level, voiceFocus)
    }

    fun populateDataMap(state: HeadsetState, dataMap: DataMap) {
        dataMap.putBoolean(KEY_CONNECTED, state.isConnected)
        dataMap.putString(KEY_DEVICE_NAME, state.deviceName)
        dataMap.putString(KEY_DEVICE_ADDRESS, state.deviceAddress)
        dataMap.putString(KEY_DEVICE_TYPE, state.deviceType.name)
        dataMap.putString(KEY_MODEL_FAMILY, state.modelFamily.name)
        dataMap.putBoolean(KEY_HAS_ANC, state.modelFamily.hasAnc)
        dataMap.putInt(KEY_BAT_LEFT, state.leftBattery.level)
        dataMap.putBoolean(KEY_BAT_LEFT_CHG, state.leftBattery.isCharging)
        dataMap.putInt(KEY_BAT_RIGHT, state.rightBattery.level)
        dataMap.putBoolean(KEY_BAT_RIGHT_CHG, state.rightBattery.isCharging)
        dataMap.putInt(KEY_BAT_CASE, state.caseBattery.level)
        dataMap.putBoolean(KEY_BAT_CASE_CHG, state.caseBattery.isCharging)
        dataMap.putInt(KEY_BAT_SINGLE, state.singleBattery.level)
        dataMap.putBoolean(KEY_BAT_SINGLE_CHG, state.singleBattery.isCharging)
        dataMap.putString(KEY_NOISE_MODE, state.noiseMode.name)
        dataMap.putInt(KEY_AMBIENT_LEVEL, state.ambientLevel)
        dataMap.putBoolean(KEY_VOICE_FOCUS, state.voiceFocus)
        dataMap.putInt(KEY_ACCENT_COLOR, state.accentColor)
        dataMap.putLong(KEY_LAST_SYNC, state.lastSyncTime)
        dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
    }

    fun parseDataMap(dataMap: DataMap): HeadsetState {
        val devName = dataMap.getString(KEY_DEVICE_NAME, "WF-1000XM6")
        val devAddr = dataMap.getString(KEY_DEVICE_ADDRESS, "")
        val isConn = dataMap.getBoolean(KEY_CONNECTED, false)
        val devTypeStr = dataMap.getString(KEY_DEVICE_TYPE, DeviceType.TWS.name)
        val devType = try { DeviceType.valueOf(devTypeStr) } catch (_: Exception) { DeviceType.TWS }

        val modelFamStr = dataMap.getString(KEY_MODEL_FAMILY, "")
        val forcedFamily = try {
            if (modelFamStr.isNotBlank()) SonyModelFamily.valueOf(modelFamStr) else null
        } catch (_: Exception) {
            null
        }

        val batLeft = BatteryInfo(
            level = dataMap.getInt(KEY_BAT_LEFT, -1),
            isCharging = dataMap.getBoolean(KEY_BAT_LEFT_CHG, false)
        )
        val batRight = BatteryInfo(
            level = dataMap.getInt(KEY_BAT_RIGHT, -1),
            isCharging = dataMap.getBoolean(KEY_BAT_RIGHT_CHG, false)
        )
        val batCase = BatteryInfo(
            level = dataMap.getInt(KEY_BAT_CASE, -1),
            isCharging = dataMap.getBoolean(KEY_BAT_CASE_CHG, false)
        )
        val batSingle = BatteryInfo(
            level = dataMap.getInt(KEY_BAT_SINGLE, -1),
            isCharging = dataMap.getBoolean(KEY_BAT_SINGLE_CHG, false)
        )

        val noiseModeStr = dataMap.getString(KEY_NOISE_MODE, NoiseControlMode.ANC.name)
        val noiseMode = try { NoiseControlMode.valueOf(noiseModeStr) } catch (_: Exception) { NoiseControlMode.ANC }

        val ambientLevel = dataMap.getInt(KEY_AMBIENT_LEVEL, 20)
        val voiceFocus = dataMap.getBoolean(KEY_VOICE_FOCUS, false)
        val accentColor = dataMap.getInt(KEY_ACCENT_COLOR, 0xFF00E676.toInt())
        val lastSync = dataMap.getLong(KEY_LAST_SYNC, 0L)

        return HeadsetState(
            deviceName = devName,
            deviceAddress = devAddr,
            isConnected = isConn,
            isBusy = false,
            deviceType = devType,
            leftBattery = batLeft,
            rightBattery = batRight,
            caseBattery = batCase,
            singleBattery = batSingle,
            noiseMode = noiseMode,
            ambientLevel = ambientLevel,
            voiceFocus = voiceFocus,
            accentColor = accentColor,
            lastSyncTime = lastSync,
            forcedModelFamily = forcedFamily
        )
    }
}
