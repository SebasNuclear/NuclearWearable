package com.nuclear.soundconnect.bluetooth

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Representación multiplataforma de un dispositivo Bluetooth Classic emparejado.
 */
data class SonyBluetoothDevice(
    val address: String,
    val name: String? = null,
    val uuids: List<UUID> = emptyList(),
    val isConnected: Boolean = false,
    val osBatteryPercent: Int? = null
)

/**
 * Conexión de flujo serie RFCOMM activa sobre la que opera [SonyRfcommClient].
 */
interface RfcommSocketConnection {
    val inputStream: InputStream
    val outputStream: OutputStream
    val isConnected: Boolean
    fun close()
}

/**
 * Conector Bluetooth dependiente de plataforma (Linux BlueZ + JNA libc AF_BLUETOOTH / Mock en tests).
 */
interface RfcommConnector {
    fun isAdapterPowered(): Boolean
    fun findPairedDevices(): List<SonyBluetoothDevice>
    fun connect(device: SonyBluetoothDevice, uuid: UUID): RfcommSocketConnection?
}
