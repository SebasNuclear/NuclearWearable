package com.nuclear.soundconnect.bluetooth

/**
 * Modelos de datos para el ecosistema Sony Headphones & NuclearUI Controller v1.0.0.
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

/**
 * Presets oficiales del ecualizador en el protocolo Sony MDR.
 */
enum class EqPreset(val id: Int, val labelEs: String, val labelEn: String) {
    OFF(0x00, "Desactivado", "Off"),
    ROCK(0x01, "Rock", "Rock"),
    POP(0x02, "Pop", "Pop"),
    JAZZ(0x03, "Jazz", "Jazz"),
    DANCE(0x04, "Dance", "Dance"),
    EDM(0x05, "EDM", "EDM"),
    ACOUSTIC(0x07, "Acústico", "Acoustic"),
    BRIGHT(0x10, "Brillante", "Bright"),
    EXCITED(0x11, "Excitado", "Excited"),
    MELLOW(0x12, "Suave", "Mellow"),
    RELAXED(0x13, "Relajado", "Relaxed"),
    VOCAL(0x14, "Vocal", "Vocal"),
    TREBLE(0x15, "Agudos+", "Treble Boost"),
    BASS(0x16, "Graves+", "Bass Boost"),
    SPEECH(0x17, "Voz / Podcast", "Speech"),
    HEAVY(0x30, "Heavy", "Heavy"),
    CLEAR(0x31, "Claro", "Clear"),
    HARD(0x32, "Hard", "Hard"),
    SOFT(0x33, "Soft", "Soft"),
    CUSTOM(0xA0, "Manual", "Manual"),
    USER_SETTING1(0xA1, "Personalizado 1", "Custom 1"),
    USER_SETTING2(0xA2, "Personalizado 2", "Custom 2"),
    USER_SETTING3(0xA3, "Personalizado 3", "Custom 3"),
    UNSPECIFIED(0xFF, "Personalizado (V1)", "Custom (V1)");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs

    companion object {
        fun fromId(id: Int): EqPreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Estado completo del ecualizador (clásico 5 bandas + Clear Bass o 10 bandas tipo WH-1000XM6).
 */
data class EqState(
    val isSupported: Boolean = false,
    val isLoaded: Boolean = false,
    val presetId: Int = 0x00,
    val clearBass: Int = 0,
    val bands: List<Int> = emptyList(),
    val isTenBand: Boolean = false
) {
    val activePreset: EqPreset?
        get() = EqPreset.fromId(presetId)
}

/**
 * Prioridad de enlace Bluetooth A2DP en el protocolo Sony AUDIO.
 */
enum class ConnectionPriority(val byteValue: Int) {
    SOUND_QUALITY(0x00),
    STABLE_CONNECTION(0x01),
    LOW_LATENCY(0x02);

    companion object {
        fun fromByte(value: Int): ConnectionPriority? = entries.firstOrNull { it.byteValue == value }
    }
}

/**
 * Opciones de apagado automático soportadas por PowerInquiredType 0x04/0x05.
 */
enum class AutoPowerOffOption(val byteValue: Int, val minutes: Int) {
    MIN_5(0x00, 5),
    MIN_15(0x04, 15),
    MIN_30(0x01, 30),
    MIN_60(0x02, 60),
    MIN_180(0x03, 180),
    WHEN_REMOVED(0x10, 0),
    DISABLE(0x11, -1);

    companion object {
        fun fromByte(value: Int): AutoPowerOffOption? = entries.firstOrNull { it.byteValue == value }
    }
}

enum class SpeakToChatSensitivity(val byteValue: Int) {
    AUTO(0x00),
    HIGH(0x01),
    LOW(0x02);

    companion object {
        fun fromByte(value: Int): SpeakToChatSensitivity = entries.firstOrNull { it.byteValue == value } ?: AUTO
    }
}

enum class SpeakToChatReturnTime(val byteValue: Int) {
    FAST(0x00),
    MID(0x01),
    SLOW(0x02),
    NONE(0x03);

    companion object {
        fun fromByte(value: Int): SpeakToChatReturnTime = entries.firstOrNull { it.byteValue == value } ?: MID
    }
}

/**
 * Dispositivo host registrado en el historial multipunto del audífono (Table 2 PERI 0x0E).
 */
data class MultipointDevice(
    val address: String,
    val friendlyName: String,
    val isConnected: Boolean,
    val isActiveSource: Boolean = false
)

/**
 * Presets reasignables para los paneles táctiles o botones físicos (ASSIGNABLE_SETTINGS).
 */
enum class TouchPresetOption(val byteValue: Int, val labelEs: String, val labelEn: String) {
    AMBIENT_SOUND_CONTROL(0x00, "Control de ruido (ANC / AMB)", "Ambient Sound Control"),
    VOLUME_CONTROL(0x10, "Control de volumen", "Volume Control"),
    PLAYBACK_CONTROL(0x20, "Control de reproducción", "Playback Control"),
    TRACK_CONTROL(0x21, "Control de pistas", "Track Control"),
    VOICE_RECOGNITION(0x30, "Asistente de voz", "Voice Assistant"),
    AMBIENT_SOUND_CONTROL_QUICK_ACCESS(0x35, "Control de ruido + Acceso rápido", "Ambient Sound + Quick Access"),
    QUICK_ACCESS(0x36, "Acceso rápido", "Quick Access"),
    NO_FUNCTION(0xFF, "Desactivado (Sin función)", "Disabled (No Function)");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs

    companion object {
        fun fromByte(value: Int): TouchPresetOption? = entries.firstOrNull { it.byteValue == value }
    }
}

enum class VoiceGuidanceLanguageOption(val byteValue: Int, val label: String) {
    UNDEFINED(0x00, "--"),
    ENGLISH(0x01, "English"),
    FRENCH(0x02, "Français"),
    GERMAN(0x03, "Deutsch"),
    SPANISH(0x04, "Español"),
    ITALIAN(0x05, "Italiano"),
    PORTUGUESE(0x06, "Português"),
    DUTCH(0x07, "Nederlands"),
    SWEDISH(0x08, "Svenska"),
    FINNISH(0x09, "Suomi"),
    RUSSIAN(0x0A, "Русский"),
    JAPANESE(0x0B, "日本語"),
    BRAZILIAN_PORTUGUESE(0x0D, "Português (BR)"),
    KOREAN(0x0F, "한국어"),
    TURKISH(0x10, "Türkçe"),
    CHINESE(0xF0, "中文");

    companion object {
        fun fromByte(value: Int): VoiceGuidanceLanguageOption =
            entries.firstOrNull { it.byteValue == value } ?: UNDEFINED
    }
}

/**
 * Capacidades dinámicas resueltas desde Table 1/Table 2 supportFunctions con fallback por familia.
 */
data class DeviceCapabilities(
    val supportsEq: Boolean = false,
    val tenBandEq: Boolean = false,
    val supportsDsee: Boolean = false,
    val supportsConnectionPriority: Boolean = false,
    val connectionPriorityInquiredType: Int = 0x00,
    val supportsSpeakToChat: Boolean = false,
    val speakToChatInquiredType: Int = 0x02,
    val speakToChatExtMode: Int = 1,
    val supportsPauseWhenRemoved: Boolean = false,
    val supportsAutoPowerOff: Boolean = false,
    val supportsAutoPowerOffWear: Boolean = false,
    val supportsWindNoiseReduction: Boolean = false,
    val supportsMultipoint: Boolean = false,
    val multipointViaPeri: Boolean = false,
    val multipointPeriInquiredType: Int = 0x00,
    val multipointViaGs: Boolean = false,
    val supportsVoiceGuidance: Boolean = false,
    val voiceGuidanceInquiredType: Int = 0x03,
    val supportsVoiceGuidanceLanguage: Boolean = false,
    val supportsVoiceGuidanceVolume: Boolean = false,
    val voiceGuidanceVolumeInquiredType: Int = 0x20,
    val supportsTouchAssignment: Boolean = false,
    val touchAssignmentInquiredType: Int = 0x03,
    val supportsTouchPanelToggle: Boolean = false,
    val supportsRemotePowerOff: Boolean = false
) {
    companion object {
        private val DSEE_FALLBACK_MODELS = setOf(
            SonyModelFamily.WF_1000XM6, SonyModelFamily.WF_1000XM5, SonyModelFamily.WF_1000XM4, SonyModelFamily.WF_1000XM3,
            SonyModelFamily.WH_1000XM6, SonyModelFamily.WH_1000XM5, SonyModelFamily.WH_1000XM4,
            SonyModelFamily.WH_ULT900N, SonyModelFamily.WH_CH720N, SonyModelFamily.WH_CH520,
            SonyModelFamily.WH_XB910N, SonyModelFamily.WF_C700N, SonyModelFamily.WF_C500,
            SonyModelFamily.LINKBUDS_OPEN, SonyModelFamily.LINKBUDS_S, SonyModelFamily.WI_1000X, SonyModelFamily.WH_H910N
        )

        private val SPEAK_TO_CHAT_FALLBACK_MODELS = setOf(
            SonyModelFamily.WH_1000XM6, SonyModelFamily.WH_1000XM5, SonyModelFamily.WH_1000XM4,
            SonyModelFamily.WF_1000XM6, SonyModelFamily.WF_1000XM5, SonyModelFamily.WF_1000XM4,
            SonyModelFamily.LINKBUDS_OPEN, SonyModelFamily.LINKBUDS_S
        )

        private val WEAR_SENSOR_FALLBACK_MODELS = setOf(
            SonyModelFamily.WH_1000XM6, SonyModelFamily.WH_1000XM5, SonyModelFamily.WH_1000XM4,
            SonyModelFamily.WF_1000XM6, SonyModelFamily.WF_1000XM5, SonyModelFamily.WF_1000XM4, SonyModelFamily.WF_1000XM3,
            SonyModelFamily.LINKBUDS_OPEN, SonyModelFamily.LINKBUDS_S, SonyModelFamily.WH_ULT900N
        )

        private val MULTIPOINT_FALLBACK_MODELS = setOf(
            SonyModelFamily.WH_1000XM6, SonyModelFamily.WH_1000XM5, SonyModelFamily.WH_1000XM4,
            SonyModelFamily.WF_1000XM6, SonyModelFamily.WF_1000XM5, SonyModelFamily.WF_1000XM4,
            SonyModelFamily.WH_CH720N, SonyModelFamily.WH_CH520, SonyModelFamily.WH_ULT900N,
            SonyModelFamily.WF_C700N, SonyModelFamily.LINKBUDS_S, SonyModelFamily.LINKBUDS_OPEN
        )

        fun resolve(state: HeadsetState): DeviceCapabilities {
            val isV2 = state.protocolVersion >= 2
            val t1 = state.supportFunctions
            val t2 = state.table2SupportFunctions
            val hasT1 = t1.isNotEmpty()
            val hasT2 = t2.isNotEmpty()
            val family = state.modelFamily

            val tenBandEq = (0x57 in t1) || family == SonyModelFamily.WH_1000XM6
            val supportsEq = if (hasT1) {
                t1.any { it in setOf(0x50, 0x52, 0x53, 0x55, 0x56, 0x57) }
            } else {
                true
            }

            val supportsDsee = if (!isV2) {
                false
            } else if (hasT1) {
                (0xE2 in t1) || (0x13 in t1)
            } else {
                family in DSEE_FALLBACK_MODELS
            }

            val supportsConnectionPriority = isV2 && (if (hasT1) {
                (0xE1 in t1) || (0xE3 in t1) || (0xE7 in t1)
            } else {
                true
            })
            val connPriorityType = when {
                0xE7 in t1 -> 0x05
                0xE3 in t1 -> 0x02
                else -> 0x00
            }

            val supportsSpeakToChat = if (!isV2) {
                family == SonyModelFamily.WH_1000XM4
            } else if (hasT1) {
                (0xF2 in t1) || (0xFC in t1)
            } else {
                family in SPEAK_TO_CHAT_FALLBACK_MODELS
            }
            val speakInquired = when {
                !isV2 -> 0x05
                0xFC in t1 -> 0x0C
                else -> 0x02
            }
            val speakExtMode = if (0xFC in t1) 2 else 1

            val supportsPauseWhenRemoved = isV2 && (if (hasT1) 0xF1 in t1 else family in WEAR_SENSOR_FALLBACK_MODELS)

            val supportsAutoPowerOff = if (!isV2) {
                family == SonyModelFamily.WH_1000XM4 || family == SonyModelFamily.WH_1000XM3
            } else if (hasT1) {
                (0x24 in t1) || (0x25 in t1)
            } else {
                true
            }
            val supportsAutoPowerOffWear = isV2 && (if (hasT1) 0x25 in t1 else family in WEAR_SENSOR_FALLBACK_MODELS)

            val supportsWindNoiseReduction = isV2 && state.effectiveHasAnc && (
                (hasT1 && (0x68 in t1)) || (!hasT1 && family == SonyModelFamily.WF_1000XM4)
            )

            val multipointViaPeri = isV2 && ((0x30 in t2) || (0x32 in t2))
            val multipointPeriInquiredType = if (0x32 in t2) 0x02 else 0x00
            val multipointViaGs = isV2 && !multipointViaPeri && (0xD2 in t1) && (
                (0xD2 in state.gsBooleanConfirmed) || (!hasT2 && state.multipointEnabled != null)
            )
            val supportsMultipoint = multipointViaPeri || multipointViaGs || (
                isV2 && !hasT1 && !hasT2 && (family in MULTIPOINT_FALLBACK_MODELS)
            )

            val vgInquired = when {
                0x42 in t2 -> 0x01
                0x41 in t2 -> 0x01
                0x40 in t2 -> 0x00
                0x44 in t2 -> 0x02
                0x45 in t2 -> 0x03
                else -> -1
            }
            val supportsVoiceGuidance = isV2 && (vgInquired >= 0 || (!hasT2 && state.voiceGuidanceEnabled != null))
            val resolvedVgInquired = if (vgInquired >= 0) vgInquired else 0x03
            val supportsVgLang = isV2 && ((0x41 in t2) || (0x42 in t2) || (0x44 in t2))
            val supportsVgVol = isV2 && ((0x42 in t2) || (0x43 in t2))
            val vgVolInquired = if (0x43 in t2) 0x21 else 0x20

            val supportsTouchAssignment = isV2 && (if (hasT1) (0xF3 in t1 || 0xFE in t1) else true)
            val touchInquired = if (0xFE in t1) 0x0E else 0x03
            val supportsTouchPanelToggle = isV2 && (0xD3 in t1) && (0xD3 in state.gsBooleanConfirmed)

            val supportsRemotePowerOff = isV2 && (!hasT1 || (0x23 in t1))

            return DeviceCapabilities(
                supportsEq = supportsEq,
                tenBandEq = tenBandEq,
                supportsDsee = supportsDsee,
                supportsConnectionPriority = supportsConnectionPriority,
                connectionPriorityInquiredType = connPriorityType,
                supportsSpeakToChat = supportsSpeakToChat,
                speakToChatInquiredType = speakInquired,
                speakToChatExtMode = speakExtMode,
                supportsPauseWhenRemoved = supportsPauseWhenRemoved,
                supportsAutoPowerOff = supportsAutoPowerOff,
                supportsAutoPowerOffWear = supportsAutoPowerOffWear,
                supportsWindNoiseReduction = supportsWindNoiseReduction,
                supportsMultipoint = supportsMultipoint,
                multipointViaPeri = multipointViaPeri,
                multipointPeriInquiredType = multipointPeriInquiredType,
                multipointViaGs = multipointViaGs,
                supportsVoiceGuidance = supportsVoiceGuidance,
                voiceGuidanceInquiredType = resolvedVgInquired,
                supportsVoiceGuidanceLanguage = supportsVgLang,
                supportsVoiceGuidanceVolume = supportsVgVol,
                voiceGuidanceVolumeInquiredType = vgVolInquired,
                supportsTouchAssignment = supportsTouchAssignment,
                touchAssignmentInquiredType = touchInquired,
                supportsTouchPanelToggle = supportsTouchPanelToggle,
                supportsRemotePowerOff = supportsRemotePowerOff
            )
        }
    }
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
    val accentColor: Int = 0xFF00E676.toInt(),               // Color de acento One UI sincronizado con el móvil
    val lastSyncTime: Long = System.currentTimeMillis(),
    val forcedModelFamily: SonyModelFamily? = null,          // Override manual de Capa 3 si el usuario lo fijó
    val cachedModelFamily: SonyModelFamily? = null,          // Familia cacheada por MAC (debajo de Capa 1 Firmware)
    val hardwareModelName: String = "",                      // Modelo real reportado por firmware (Capa 1: 0x04 0x01)
    val firmwareVersion: String = "",                        // Versión de firmware reportada (0x04 0x02)
    val protocolVersion: Int = 2,                            // 1 = Sony MDR V1 legacy, 2 = Sony MDR V2 moderno
    val codecName: String = "",                              // Códec Bluetooth reportado (LDAC, AAC, SBC, aptX, LC3)
    val supportFunctions: Set<Int> = emptySet(),             // Funciones soportadas (Table 1 vía 0x06 0x00)
    val supportsAncOverride: Boolean? = null,                // Capacidad ANC dinámica si fue descubierta por protocolo
    val table2SupportFunctions: Set<Int> = emptySet(),       // Funciones soportadas Table 2 (0x0E vía 0x06 0x00)
    val gsBooleanConfirmed: Set<Int> = emptySet(),           // Subtipos GENERAL_SETTING confirmados como BOOLEAN (0xD1)
    val eqState: EqState = EqState(),                        // Ecualizador (5 bandas + Clear Bass o 10 bandas XM6)
    val dseeEnabled: Boolean? = null,                        // DSEE / DSEE Extreme / DSEE Ultimate
    val connectionPriority: ConnectionPriority? = null,      // Prioridad Calidad de Sonido vs Conexión Estable
    val autoPowerOff: AutoPowerOffOption? = null,            // Apagado automático
    val speakToChatEnabled: Boolean? = null,                 // Hablar para chatear (Speak-to-Chat)
    val speakToChatSensitivity: SpeakToChatSensitivity = SpeakToChatSensitivity.AUTO,
    val speakToChatReturnTime: SpeakToChatReturnTime = SpeakToChatReturnTime.MID,
    val speakToChatFocusOnVoice: Boolean = false,            // Mode 1 (0x02): enfoque de voz durante Speak-to-Chat
    val pauseWhenRemoved: Boolean? = null,                   // Sensor de uso / Pausa al retirar
    val windNoiseReduction: Boolean = false,                 // Reducción automática de viento (0x15 WF-1000XM4)
    val voiceGuidanceEnabled: Boolean? = null,               // Guía de voz
    val voiceGuidanceLanguage: VoiceGuidanceLanguageOption = VoiceGuidanceLanguageOption.UNDEFINED,
    val voiceGuidanceVolume: Int = 0,                        // -2..+2
    val multipointEnabled: Boolean? = null,                  // Conexión multipunto (2 dispositivos)
    val pairedDevices: List<MultipointDevice> = emptyList(), // Hosts vinculados en el chip (PERI 0x0E)
    val touchLeftPreset: TouchPresetOption? = null,          // Control panel/botón Izquierdo
    val touchRightPreset: TouchPresetOption? = null,         // Control panel/botón Derecho
    val touchThirdPreset: TouchPresetOption? = null,         // Botón auxiliar si existe
    val touchPanelEnabled: Boolean? = null                   // Bloqueo de panel táctil (GS 0xD3)
) {
    val capabilities: DeviceCapabilities
        get() = DeviceCapabilities.resolve(this)

    val modelFamily: SonyModelFamily
        get() {
            if (forcedModelFamily != null) return forcedModelFamily
            if (hardwareModelName.isNotBlank()) {
                val hwDetected = SonyModelFamily.detect(hardwareModelName, deviceType)
                if (hwDetected != SonyModelFamily.GENERIC_TWS && hwDetected != SonyModelFamily.GENERIC_OVER_EAR) {
                    return hwDetected
                }
            }
            if (cachedModelFamily != null &&
                cachedModelFamily != SonyModelFamily.GENERIC_TWS &&
                cachedModelFamily != SonyModelFamily.GENERIC_OVER_EAR
            ) {
                return cachedModelFamily
            }
            val byName = SonyModelFamily.detect(deviceName, deviceType)
            if (byName != SonyModelFamily.GENERIC_TWS && byName != SonyModelFamily.GENERIC_OVER_EAR) {
                return byName
            }
            return cachedModelFamily ?: byName
        }

    val effectiveHasAnc: Boolean
        get() = if (forcedModelFamily != null) {
            forcedModelFamily.hasAnc
        } else {
            supportsAncOverride ?: modelFamily.hasAnc
        }
}

data class HexLogEntry(
    val direction: Direction,
    val hex: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Direction { TX, RX, INFO, ERROR }
}
