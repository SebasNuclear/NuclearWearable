package com.nuclear.soundconnect.bluetooth

/**
 * Modelos de datos para el ecosistema Sony Headphones & NuclearUI Controller v1.0.0 (Windows Desktop).
 */

enum class NoiseControlMode {
    ANC,      // Cancelación Activa de Ruido (Active Noise Cancelling)
    OFF,      // Control de ruido desactivado
    AMBIENT   // Modo de Sonido Ambiente (Ambient Sound Mode)
}

enum class DeviceType {
    TWS,      // Audífonos True Wireless (ej. WF-1000XM, LinkBuds, Serie C) -> Muestra L, R y Estuche
    OVER_EAR  // Audífonos de Diadema y Collarín (ej. WH-1000XM, ULT WEAR, WI-1000X) -> Muestra Unidad Única
}

/**
 * Familia específica de modelo Sony para asignar sus recursos vectoriales exactos,
 * características de hardware (ANC vs solo batería) y categorías.
 */
enum class SonyModelFamily(
    val displayName: String,
    val defaultDeviceType: DeviceType,
    val hasAnc: Boolean,
    val seriesCategory: String,
    val modelsCovered: List<String>
) {
    WF_1000XM6("WF-1000XM6", DeviceType.TWS, true, "1000X Flagship", listOf("WF-1000XM6")),
    WF_1000XM5("WF-1000XM5", DeviceType.TWS, true, "1000X Flagship", listOf("WF-1000XM5")),
    WF_1000XM4("WF-1000XM4", DeviceType.TWS, true, "1000X Flagship", listOf("WF-1000XM4")),
    WF_1000XM3("WF-1000XM3", DeviceType.TWS, true, "1000X Flagship", listOf("WF-1000XM3")),

    WH_1000XM6("WH-1000XM6", DeviceType.OVER_EAR, true, "1000X Flagship", listOf("WH-1000XM6")),
    WH_1000XM5("WH-1000XM5", DeviceType.OVER_EAR, true, "1000X Flagship", listOf("WH-1000XM5")),
    WH_1000XM4("WH-1000XM4", DeviceType.OVER_EAR, true, "1000X Flagship", listOf("WH-1000XM4")),
    WH_1000XM3("WH-1000XM3", DeviceType.OVER_EAR, true, "1000X Flagship", listOf("WH-1000XM3", "WH-1000XM2", "MDR-1000X")),

    LINKBUDS_OPEN("LinkBuds Open", DeviceType.TWS, false, "LinkBuds Anillo Abierto", listOf("LinkBuds (WF-L900)", "LinkBuds Open (WF-L910)")),
    LINKBUDS_S("LinkBuds S", DeviceType.TWS, true, "LinkBuds Ultra Compacto", listOf("LinkBuds S (WF-LS900N)", "LinkBuds Fit (WF-LS910N)")),

    WH_ULT900N("ULT WEAR", DeviceType.OVER_EAR, true, "ULT / Heavy Bass", listOf("WH-ULT900N (ULT WEAR)")),
    WH_CH720N("WH-CH720N", DeviceType.OVER_EAR, true, "Serie CH Ligera", listOf("WH-CH720N", "WH-CH710N", "WH-CH700N")),
    WH_CH520("WH-CH520", DeviceType.OVER_EAR, false, "Serie CH On-Ear (Solo Batería)", listOf("WH-CH520", "WH-CH510", "WH-CH500")),
    WH_XB910N("WH-XB910N", DeviceType.OVER_EAR, true, "Extra Bass Over-Ear", listOf("WH-XB910N", "WH-XB900N")),

    WF_C700N("WF-C700N", DeviceType.TWS, true, "Serie C Confort ANC", listOf("WF-C700N", "WF-C710N")),
    WF_C500("WF-C500", DeviceType.TWS, false, "Serie C Esférico (Solo Batería)", listOf("WF-C500", "WF-C510")),

    WF_SP800N("WF-SP800N", DeviceType.TWS, true, "Serie Sport Arc Supporter", listOf("WF-SP800N", "WF-SP700N")),
    WI_1000X("WI-1000X", DeviceType.OVER_EAR, true, "Collarín Premium ANC", listOf("WI-1000X", "WI-1000XM2")),
    WI_C100("WI-C100", DeviceType.OVER_EAR, false, "Banda Flexible (Solo Batería)", listOf("WI-C100", "WI-C200", "WI-C310", "WI-XB400")),

    INZONE_BUDS("INZONE Buds", DeviceType.TWS, true, "Gaming PlayStation", listOf("INZONE Buds (WF-G700N)")),
    MDR_XB950("MDR-XB950", DeviceType.OVER_EAR, true, "Club Bass Almohadillas Gigantes", listOf("MDR-XB950N1", "MDR-XB950B1", "MDR-XB650BT")),
    WH_H910N("WH-H910N", DeviceType.OVER_EAR, true, "h.ear on Unibody ANC", listOf("WH-H910N (h.ear on 3)", "WH-H900N (h.ear on 2)")),

    GENERIC_TWS("Sony TWS", DeviceType.TWS, true, "Genérico", listOf("Otros TWS Sony")),
    GENERIC_OVER_EAR("Sony Wireless Headset", DeviceType.OVER_EAR, true, "Genérico", listOf("Otras Diademas Sony"));

    companion object {
        fun detect(deviceName: String?, fallbackType: DeviceType = DeviceType.TWS): SonyModelFamily {
            val name = (deviceName ?: "").uppercase()
            if (name.isBlank()) return WF_1000XM6

            return when {
                // 1000X Series
                name.contains("WF-1000XM6") || (name.contains("1000XM6") && !name.contains("WH-")) -> WF_1000XM6
                name.contains("WF-1000XM5") || (name.contains("1000XM5") && !name.contains("WH-")) -> WF_1000XM5
                name.contains("WF-1000XM4") || (name.contains("1000XM4") && !name.contains("WH-")) -> WF_1000XM4
                name.contains("WF-1000XM3") || (name.contains("1000XM3") && !name.contains("WH-")) -> WF_1000XM3
                name.contains("WH-1000XM6") -> WH_1000XM6
                name.contains("WH-1000XM5") -> WH_1000XM5
                name.contains("WH-1000XM4") -> WH_1000XM4
                name.contains("WH-1000XM3") || name.contains("WH-1000XM2") || name.contains("MDR-1000X") -> WH_1000XM3

                // LinkBuds Series
                name.contains("WF-LS900N") || name.contains("WF-LS910N") || name.contains("LINKBUDS S") || name.contains("LINKBUDS FIT") -> LINKBUDS_S
                name.contains("WF-L900") || name.contains("WF-L910") || name.contains("LINKBUDS") -> LINKBUDS_OPEN

                // ULT WEAR & Heavy Bass
                name.contains("WH-ULT900N") || name.contains("ULT900") || name.contains("ULT WEAR") -> WH_ULT900N
                name.contains("WH-XB910N") || name.contains("WH-XB900N") || name.contains("XB910") || name.contains("XB900") -> WH_XB910N
                name.contains("MDR-XB950") || name.contains("MDR-XB650") || name.contains("XB950") -> MDR_XB950

                // Serie CH
                name.contains("WH-CH720N") || name.contains("WH-CH710N") || name.contains("WH-CH700N") || name.contains("CH720") || name.contains("CH710") || name.contains("CH700") -> WH_CH720N
                name.contains("WH-CH520") || name.contains("WH-CH510") || name.contains("WH-CH500") || name.contains("CH520") || name.contains("CH510") || name.contains("CH500") -> WH_CH520

                // Serie C
                name.contains("WF-C700N") || name.contains("WF-C710N") || name.contains("C700N") || name.contains("C710N") -> WF_C700N
                name.contains("WF-C500") || name.contains("WF-C510") || name.contains("C500") || name.contains("C510") -> WF_C500

                // Deportes & Gaming
                name.contains("WF-SP800N") || name.contains("WF-SP700N") || name.contains("SP800") || name.contains("SP700") -> WF_SP800N
                name.contains("WF-G700N") || name.contains("INZONE") -> INZONE_BUDS

                // Bandas al cuello (Neckbands)
                name.contains("WI-1000X") || name.contains("WI-1000XM2") -> WI_1000X
                name.contains("WI-C100") || name.contains("WI-C200") || name.contains("WI-C310") || name.contains("WI-XB400") || name.contains("WIC100") || name.contains("WIC200") || name.contains("WIC310") -> WI_C100

                // h.ear on
                name.contains("WH-H910N") || name.contains("WH-H900N") || name.contains("H910N") || name.contains("H900N") || name.contains("H.EAR") -> WH_H910N

                // Fallbacks basados en tipo o prefijo
                fallbackType == DeviceType.OVER_EAR || name.startsWith("WH-") || name.startsWith("WI-") || name.startsWith("MDR-") || name.contains("OVER-EAR") -> GENERIC_OVER_EAR
                else -> GENERIC_TWS
            }
        }
    }
}

data class BatteryInfo(
    val level: Int = -1,           // 0..100, o -1 si es desconocido/en espera
    val isCharging: Boolean = false // true si el pin detecta carga activa
) {
    val displayPercent: String
        get() = if (level in 0..100) "$level%" else "--%"

    val isAvailable: Boolean
        get() = level in 0..100
}

data class HeadsetState(
    val deviceName: String = "WF-1000XM6",
    val deviceAddress: String = "",
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val deviceType: DeviceType = DeviceType.TWS,
    val leftBattery: BatteryInfo = BatteryInfo(-1, false),
    val rightBattery: BatteryInfo = BatteryInfo(-1, false),
    val caseBattery: BatteryInfo = BatteryInfo(-1, false),
    val singleBattery: BatteryInfo = BatteryInfo(-1, false), // Usado en Over-Ear y Neckband
    val noiseMode: NoiseControlMode = NoiseControlMode.ANC,
    val ambientLevel: Int = 20,                              // Rango 1..20
    val voiceFocus: Boolean = false,                         // Enfoque en la voz
    val lastSyncTime: Long = System.currentTimeMillis(),
    val forcedModelFamily: SonyModelFamily? = null           // Override manual de Capa 3 si el usuario lo fijó
) {
    val modelFamily: SonyModelFamily
        get() = forcedModelFamily ?: SonyModelFamily.detect(deviceName, deviceType)
}

data class HexLogEntry(
    val direction: Direction,
    val hex: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Direction { TX, RX, INFO, ERROR }
}
