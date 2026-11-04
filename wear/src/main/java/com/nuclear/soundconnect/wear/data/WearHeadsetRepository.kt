package com.nuclear.soundconnect.wear.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.nuclear.soundconnect.bluetooth.BatteryInfo
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyRfcommClient
import com.nuclear.soundconnect.wear.NuclearWearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WearConnectionMode {
    STANDALONE,   // Conectado directamente por Bluetooth en el reloj
    COMPANION,    // Enlazado al teléfono mediante Wearable Data Layer
    DISCONNECTED  // Desconectado o a la espera de enlace
}

/**
 * Repositorio central de Wear OS con arquitectura Dual-Mode:
 * Capaz de operar de forma remota como puente del teléfono o de manera 100% independiente
 * si los audífonos Sony están emparejados y conectados directamente al reloj.
 */
class WearHeadsetRepository private constructor(private val context: Context) : DataClient.OnDataChangedListener {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(loadCachedState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

    private val _connectionMode = MutableStateFlow(WearConnectionMode.DISCONNECTED)
    val connectionMode: StateFlow<WearConnectionMode> = _connectionMode.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val rfcommClient = SonyRfcommClient(context)
    private val dataClient: DataClient = Wearable.getDataClient(context)

    init {
        dataClient.addListener(this)
        scope.launch {
            checkCurrentModeAndSync()
        }
    }

    /**
     * Evalúa si hay audífonos Sony conectados directamente al reloj (Modo Standalone).
     * Si no los hay, consulta el último DataItem en /nuclear/state enviado por el teléfono (Modo Companion).
     */
    suspend fun checkCurrentModeAndSync() = withContext(Dispatchers.IO) {
        val directSonyDevice = getDirectlyConnectedSonyDevice()
        if (directSonyDevice != null) {
            _connectionMode.value = WearConnectionMode.STANDALONE
            val current = _state.value
            val standaloneState = current.copy(
                deviceName = try { directSonyDevice.name ?: current.deviceName } catch (_: SecurityException) { current.deviceName },
                deviceAddress = directSonyDevice.address,
                isConnected = true,
                deviceType = rfcommClient.detectDeviceType(directSonyDevice)
            )
            updateState(standaloneState)
            syncStandaloneTelemetry(directSonyDevice)
            return@withContext
        }

        // Si no hay audífonos Sony directos en el reloj, consultar el DataItem del móvil
        fetchInitialDataFromPhone()
    }

    /**
     * Recupera el DataItem más reciente en /nuclear/state mediante DataClient.
     */
    private suspend fun fetchInitialDataFromPhone() = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("wear://*${NuclearWearProtocol.PATH_STATE}")
            val dataItemBuffer = Tasks.await(dataClient.getDataItems(uri))
            try {
                for (item in dataItemBuffer) {
                    if (item.uri.path == NuclearWearProtocol.PATH_STATE) {
                        val dataMapItem = DataMapItem.fromDataItem(item)
                        val newState = NuclearWearProtocol.parseDataMap(dataMapItem.dataMap)
                        _connectionMode.value = if (newState.isConnected) WearConnectionMode.COMPANION else WearConnectionMode.DISCONNECTED
                        updateState(newState)
                        break
                    }
                }
            } finally {
                dataItemBuffer.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar el estado inicial del móvil: ${e.localizedMessage}")
            if (_connectionMode.value != WearConnectionMode.STANDALONE) {
                _connectionMode.value = if (_state.value.isConnected) WearConnectionMode.COMPANION else WearConnectionMode.DISCONNECTED
            }
        }
    }

    /**
     * Intercepta cambios en los DataItems emitidos por el teléfono en tiempo real.
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == NuclearWearProtocol.PATH_STATE) {
                    val dataMapItem = DataMapItem.fromDataItem(item)
                    val phoneState = NuclearWearProtocol.parseDataMap(dataMapItem.dataMap)
                    Log.d(TAG, "Telemetría recibida desde el teléfono: ${phoneState.deviceName} (Conectado: ${phoneState.isConnected})")

                    // Solo actualizar desde el teléfono si no estamos usando audífonos directos en el reloj
                    if (_connectionMode.value != WearConnectionMode.STANDALONE) {
                        _connectionMode.value = if (phoneState.isConnected) WearConnectionMode.COMPANION else WearConnectionMode.DISCONNECTED
                        updateState(phoneState)
                    }
                }
            }
        }
    }

    /**
     * Actualiza el estado tanto en memoria como en persistencia local.
     */
    fun updateState(newState: HeadsetState) {
        _state.value = newState
        saveCachedState(newState)
    }

    /**
     * Conmuta el modo de cancelación de ruido (ANC / AMBIENT / OFF).
     */
    fun setNoiseMode(mode: NoiseControlMode, onComplete: ((Boolean) -> Unit)? = null) {
        val currentState = _state.value
        if (!currentState.modelFamily.hasAnc) {
            onComplete?.invoke(false)
            return
        }

        // Optimistic UI Update inmediata para máxima fluidez en la pantalla táctil
        val optimisticState = currentState.copy(noiseMode = mode)
        updateState(optimisticState)

        scope.launch {
            _isBusy.value = true
            var success = false

            try {
                if (_connectionMode.value == WearConnectionMode.STANDALONE) {
                    val directDevice = getDirectlyConnectedSonyDevice()
                    if (directDevice != null) {
                        val res = rfcommClient.setNoiseMode(
                            device = directDevice,
                            mode = mode,
                            ambientLevel = currentState.ambientLevel,
                            voiceFocus = currentState.voiceFocus,
                            currentState = optimisticState
                        )
                        if (res.isSuccess) {
                            updateState(res.getOrThrow())
                            success = true
                        }
                    }
                } else {
                    // Modo Companion: Transmitir comando MessageClient hacia el móvil
                    success = sendMessageToPhone(
                        path = NuclearWearProtocol.PATH_CMD_SET_NOISE_MODE,
                        payload = byteArrayOf(NuclearWearProtocol.noiseControlModeToByte(mode))
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando setNoiseMode: ${e.localizedMessage}")
            } finally {
                _isBusy.value = false
                onComplete?.invoke(success)
            }
        }
    }

    /**
     * Configura el nivel de Sonido Ambiente (1..20) y el Enfoque en la voz.
     */
    fun setAmbientConfig(level: Int, voiceFocus: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        val clamped = level.coerceIn(1, 20)
        val currentState = _state.value
        val optimisticState = currentState.copy(
            ambientLevel = clamped,
            voiceFocus = voiceFocus
        )
        updateState(optimisticState)

        scope.launch {
            _isBusy.value = true
            var success = false

            try {
                if (_connectionMode.value == WearConnectionMode.STANDALONE) {
                    val directDevice = getDirectlyConnectedSonyDevice()
                    if (directDevice != null) {
                        val targetMode = if (optimisticState.noiseMode == NoiseControlMode.AMBIENT) {
                            NoiseControlMode.AMBIENT
                        } else {
                            optimisticState.noiseMode
                        }
                        val res = rfcommClient.setNoiseMode(
                            device = directDevice,
                            mode = targetMode,
                            ambientLevel = clamped,
                            voiceFocus = voiceFocus,
                            currentState = optimisticState
                        )
                        if (res.isSuccess) {
                            updateState(res.getOrThrow())
                            success = true
                        }
                    }
                } else {
                    // Modo Companion: Transmitir comando MessageClient hacia el móvil
                    val payload = NuclearWearProtocol.buildAmbientConfigPayload(clamped, voiceFocus)
                    success = sendMessageToPhone(
                        path = NuclearWearProtocol.PATH_CMD_SET_AMBIENT_CONFIG,
                        payload = payload
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando setAmbientConfig: ${e.localizedMessage}")
            } finally {
                _isBusy.value = false
                onComplete?.invoke(success)
            }
        }
    }

    /**
     * Solicita una sincronización fresca de batería y telemetría.
     */
    fun requestSync(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            _isBusy.value = true
            var success = false

            try {
                val directDevice = getDirectlyConnectedSonyDevice()
                if (directDevice != null) {
                    _connectionMode.value = WearConnectionMode.STANDALONE
                    val res = rfcommClient.syncTelemetry(directDevice, _state.value)
                    if (res.isSuccess) {
                        updateState(res.getOrThrow())
                        success = true
                    }
                } else {
                    // Solicitar al móvil que ejecute la sincronización
                    _connectionMode.value = WearConnectionMode.COMPANION
                    success = sendMessageToPhone(
                        path = NuclearWearProtocol.PATH_CMD_REQUEST_SYNC,
                        payload = ByteArray(0)
                    )
                    // Además refrescamos el DataItem actual
                    fetchInitialDataFromPhone()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error solicitando sync: ${e.localizedMessage}")
            } finally {
                _isBusy.value = false
                onComplete?.invoke(success)
            }
        }
    }

    private suspend fun syncStandaloneTelemetry(device: BluetoothDevice): Unit = withContext(Dispatchers.IO) {
        try {
            _isBusy.value = true
            val res = rfcommClient.syncTelemetry(device, _state.value)
            if (res.isSuccess) {
                updateState(res.getOrThrow())
            }
            Unit
        } catch (e: Exception) {
            Log.e(TAG, "Error en syncStandaloneTelemetry: ${e.localizedMessage}")
        } finally {
            _isBusy.value = false
        }
    }

    private suspend fun sendMessageToPhone(path: String, payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            if (nodes.isEmpty()) {
                Log.w(TAG, "No hay teléfono conectado para enviar el comando $path")
                return@withContext false
            }

            val messageClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, path, payload))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje $path al teléfono: ${e.localizedMessage}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDirectlyConnectedSonyDevice(): BluetoothDevice? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return null
        val adapter = manager.adapter ?: return null
        if (!adapter.isEnabled) return null

        val bonded = try { adapter.bondedDevices } catch (_: SecurityException) { null } ?: return null
        val sonyDevices = bonded.filter { SonyRfcommClient.isSonyAudioDevice(it) }

        // Verificar si alguno está conectado actualmente en el perfil HEADSET o A2DP del reloj
        for (dev in sonyDevices) {
            val isHeadsetConnected = manager.getConnectionState(dev, BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            val isA2dpConnected = manager.getConnectionState(dev, BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
            if (isHeadsetConnected || isA2dpConnected) {
                return dev
            }
        }
        return null
    }

    private fun saveCachedState(s: HeadsetState) {
        prefs.edit().apply {
            putString(KEY_DEV_NAME, s.deviceName)
            putString(KEY_DEV_ADDR, s.deviceAddress)
            putString(KEY_DEV_TYPE, s.deviceType.name)
            putBoolean(KEY_IS_CONNECTED, s.isConnected)
            putInt(KEY_LEFT_BATTERY, s.leftBattery.level)
            putBoolean(KEY_LEFT_CHARGING, s.leftBattery.isCharging)
            putInt(KEY_RIGHT_BATTERY, s.rightBattery.level)
            putBoolean(KEY_RIGHT_CHARGING, s.rightBattery.isCharging)
            putInt(KEY_CASE_BATTERY, s.caseBattery.level)
            putBoolean(KEY_CASE_CHARGING, s.caseBattery.isCharging)
            putInt(KEY_SINGLE_BATTERY, s.singleBattery.level)
            putBoolean(KEY_SINGLE_CHARGING, s.singleBattery.isCharging)
            putString(KEY_NOISE_MODE, s.noiseMode.name)
            putInt(KEY_AMBIENT_LEVEL, s.ambientLevel)
            putBoolean(KEY_VOICE_FOCUS, s.voiceFocus)
            putInt(KEY_ACCENT_COLOR, s.accentColor)
            putLong(KEY_LAST_SYNC, s.lastSyncTime)
            apply()
        }
    }

    private fun loadCachedState(): HeadsetState {
        val devName = prefs.getString(KEY_DEV_NAME, "WF-1000XM6") ?: "WF-1000XM6"
        val devAddr = prefs.getString(KEY_DEV_ADDR, "") ?: ""
        val isConn = prefs.getBoolean(KEY_IS_CONNECTED, false)
        val devTypeStr = prefs.getString(KEY_DEV_TYPE, DeviceType.TWS.name) ?: DeviceType.TWS.name
        val devType = try { DeviceType.valueOf(devTypeStr) } catch (_: Exception) { DeviceType.TWS }

        val leftBat = BatteryInfo(
            level = prefs.getInt(KEY_LEFT_BATTERY, -1),
            isCharging = prefs.getBoolean(KEY_LEFT_CHARGING, false)
        )
        val rightBat = BatteryInfo(
            level = prefs.getInt(KEY_RIGHT_BATTERY, -1),
            isCharging = prefs.getBoolean(KEY_RIGHT_CHARGING, false)
        )
        val caseBat = BatteryInfo(
            level = prefs.getInt(KEY_CASE_BATTERY, -1),
            isCharging = prefs.getBoolean(KEY_CASE_CHARGING, false)
        )
        val singleBat = BatteryInfo(
            level = prefs.getInt(KEY_SINGLE_BATTERY, -1),
            isCharging = prefs.getBoolean(KEY_SINGLE_CHARGING, false)
        )

        val noiseModeStr = prefs.getString(KEY_NOISE_MODE, NoiseControlMode.ANC.name) ?: NoiseControlMode.ANC.name
        val noiseMode = try { NoiseControlMode.valueOf(noiseModeStr) } catch (_: Exception) { NoiseControlMode.ANC }

        return HeadsetState(
            deviceName = devName,
            deviceAddress = devAddr,
            isConnected = isConn,
            isBusy = false,
            deviceType = devType,
            leftBattery = leftBat,
            rightBattery = rightBat,
            caseBattery = caseBat,
            singleBattery = singleBat,
            noiseMode = noiseMode,
            ambientLevel = prefs.getInt(KEY_AMBIENT_LEVEL, 20),
            voiceFocus = prefs.getBoolean(KEY_VOICE_FOCUS, false),
            accentColor = prefs.getInt(KEY_ACCENT_COLOR, 0xFF00E676.toInt()),
            lastSyncTime = prefs.getLong(KEY_LAST_SYNC, 0L)
        )
    }

    companion object {
        private const val TAG = "WearHeadsetRepository"
        private const val PREFS_NAME = "nuclear_wear_prefs"

        private const val KEY_DEV_NAME = "dev_name"
        private const val KEY_DEV_ADDR = "dev_addr"
        private const val KEY_DEV_TYPE = "dev_type"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_LEFT_BATTERY = "left_bat"
        private const val KEY_LEFT_CHARGING = "left_chg"
        private const val KEY_RIGHT_BATTERY = "right_bat"
        private const val KEY_RIGHT_CHARGING = "right_chg"
        private const val KEY_CASE_BATTERY = "case_bat"
        private const val KEY_CASE_CHARGING = "case_chg"
        private const val KEY_SINGLE_BATTERY = "single_bat"
        private const val KEY_SINGLE_CHARGING = "single_chg"
        private const val KEY_NOISE_MODE = "noise_mode"
        private const val KEY_AMBIENT_LEVEL = "ambient_lvl"
        private const val KEY_VOICE_FOCUS = "voice_focus"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_LAST_SYNC = "last_sync"

        @Volatile
        private var INSTANCE: WearHeadsetRepository? = null

        fun getInstance(context: Context): WearHeadsetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WearHeadsetRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
