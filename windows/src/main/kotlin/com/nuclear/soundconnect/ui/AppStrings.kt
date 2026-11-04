package com.nuclear.soundconnect.ui

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ES("es", "Español", "Español"),
    EN("en", "English", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ES
        }
    }
}

data class LocalizedStrings(
    val appName: String,
    val leftBud: String,
    val rightBud: String,
    val caseLabel: String,
    val headphoneLabel: String,
    val connected: String,
    val disconnected: String,
    val syncing: String,
    val noiseCancelling: String,
    val noiseCancellingLong: String,
    val ambientSound: String,
    val ambientSoundLong: String,
    val noiseOff: String,
    val ambientLevel: String,
    val voiceFocus: String,
    val voiceFocusDesc: String,
    val soundSettingsTitle: String,

    // Submenús títulos (Mayúsculas)
    val deviceHistoryTitle: String,
    val themeColorsTitle: String,
    val compatibleModelsTitle: String,
    val languageTitle: String,
    val aboutTitle: String,

    // Subtítulos
    val deviceHistorySubtitle: String,
    val themeColorsSubtitle: String,
    val compatibleModelsSubtitle: String,
    val languageSubtitle: String,
    val aboutSubtitle: String,

    // Hoja Catálogo de Modelos
    val modelCatalogHeader: String,
    val modelCatalogSubheader: String,
    val activeModelBadge: String,
    val selectModelBtn: String,
    val resetAutoDetectBtn: String,
    val autoDetectedBadge: String,
    val forcedBadge: String,
    val designLockedByYou: String,
    val detectedByHardware: String,
    val searchPlaceholder: String,
    val allTab: String,
    val twsTab: String,
    val overEarTab: String,

    // Hoja Idioma
    val languageSheetTitle: String,
    val languageSheetSubtitle: String,

    // Hoja Historial de dispositivos
    val deviceHistorySheetTitle: String,
    val deviceHistorySheetSubtitle: String,
    val deviceHistoryEmpty: String,
    val pairedDevicesSection: String,
    val connectButton: String,
    val disconnectButton: String,
    val refreshButton: String,
    val genericDevice: String,

    // Hoja Tema
    val lightMode: String,
    val darkMode: String,
    val systemMode: String,
    val oledNightTitle: String,
    val oledDarkTitle: String,
    val oledNightSub: String,
    val oledDarkSub: String,
    val accentColorsSection: String,
    val themeSubtitleSystem: String,
    val themeSubtitleDark: String,
    val themeSubtitleLight: String,
    val themeSubtitleOledNight: String,
    val themeSubtitleOledPure: String,

    // Hoja Acerca de
    val aboutSheetTitle: String,
    val aboutVersion: String,
    val aboutDescription: String,
    val understoodBtn: String,

    // Windows Tray & Window
    val trayOpen: String,
    val trayExit: String,
    val trayBatteryTooltip: String
)

object AppStrings {
    private val spanish = LocalizedStrings(
        appName = "NuclearWearable",
        leftBud = "L",
        rightBud = "R",
        caseLabel = "Estuche",
        headphoneLabel = "Diadema",
        connected = "Conectado",
        disconnected = "Desconectado",
        syncing = "Sincronizando...",
        noiseCancelling = "Cancelación",
        noiseCancellingLong = "Cancelación de ruido",
        ambientSound = "Ambiente",
        ambientSoundLong = "Sonido ambiente",
        noiseOff = "Desactivado",
        ambientLevel = "Nivel de sonido ambiental",
        voiceFocus = "Enfoque en la voz",
        voiceFocusDesc = "Realza frecuencias de voz humana en el entorno",
        soundSettingsTitle = "Ajustes de sonido ambiental",

        deviceHistoryTitle = "HISTORIAL DE DISPOSITIVOS",
        themeColorsTitle = "TEMA Y COLORES",
        compatibleModelsTitle = "MODELOS COMPATIBLES",
        languageTitle = "IDIOMA / LANGUAGE",
        aboutTitle = "ACERCA DE",

        deviceHistorySubtitle = "Registro de baterías y dispositivos vinculados",
        themeColorsSubtitle = "Personalización visual y modo OLED",
        compatibleModelsSubtitle = "52+ modelos Sony compatibles",
        languageSubtitle = "Español",
        aboutSubtitle = "NuclearWearable Windows v1.0.0",

        modelCatalogHeader = "Modelos Sony compatibles",
        modelCatalogSubheader = "52+ modelos cubiertos con vectores nativos independientes. Puedes seleccionar tu modelo para fijarlo manualmente si lo renombraste.",
        activeModelBadge = "Activo",
        selectModelBtn = "Elegir",
        resetAutoDetectBtn = "Restablecer a detección automática",
        autoDetectedBadge = "Auto",
        forcedBadge = "Fijado",
        designLockedByYou = "Diseño fijado por ti",
        detectedByHardware = "Detectado por hardware/nombre",
        searchPlaceholder = "Buscar modelo (ej. XM5, LinkBuds, C500)...",
        allTab = "Todos",
        twsTab = "True Wireless",
        overEarTab = "Diadema / Cuello",

        languageSheetTitle = "Idioma de la aplicación",
        languageSheetSubtitle = "Selecciona el idioma para la interfaz de NuclearWearable",

        deviceHistorySheetTitle = "Historial de dispositivos",
        deviceHistorySheetSubtitle = "Último registro de carga de cada dispositivo vinculado",
        deviceHistoryEmpty = "No hay registros previos ni dispositivos vinculados.",
        pairedDevicesSection = "Dispositivos vinculados",
        connectButton = "Conectar",
        disconnectButton = "Desconectar",
        refreshButton = "Escanear",
        genericDevice = "Dispositivo",

        lightMode = "Claro",
        darkMode = "Oscuro",
        systemMode = "Sistema",
        oledNightTitle = "Negro puro OLED en modo nocturno",
        oledDarkTitle = "Negro puro OLED",
        oledNightSub = "Usa negro puro cuando el sistema esté en modo noche",
        oledDarkSub = "Fondo completamente negro (#000000)",
        accentColorsSection = "Colores de acento",
        themeSubtitleSystem = "Sistema",
        themeSubtitleDark = "Oscuro",
        themeSubtitleLight = "Claro",
        themeSubtitleOledNight = " (OLED nocturno)",
        themeSubtitleOledPure = " (OLED)",

        aboutSheetTitle = "Acerca de",
        aboutVersion = "Versión Windows 1.0.0",
        aboutDescription = "Controlador táctico y complementario de audio para Windows diseñado bajo el sistema visual NuclearUI. Compatible con 52+ modelos de auriculares Sony, telemetría de batería en bandeja de sistema y control instantáneo de cancelación de ruido.",
        understoodBtn = "Entendido",

        trayOpen = "Abrir NuclearWearable",
        trayExit = "Salir",
        trayBatteryTooltip = "NuclearWearable Sony Controller"
    )

    private val english = LocalizedStrings(
        appName = "NuclearWearable",
        leftBud = "L",
        rightBud = "R",
        caseLabel = "Case",
        headphoneLabel = "Headphones",
        connected = "Connected",
        disconnected = "Disconnected",
        syncing = "Syncing...",
        noiseCancelling = "Cancelling",
        noiseCancellingLong = "Noise cancelling",
        ambientSound = "Ambient",
        ambientSoundLong = "Ambient sound",
        noiseOff = "Off",
        ambientLevel = "Ambient sound level",
        voiceFocus = "Voice focus",
        voiceFocusDesc = "Enhances human speech frequencies in the environment",
        soundSettingsTitle = "Ambient sound settings",

        deviceHistoryTitle = "DEVICE HISTORY",
        themeColorsTitle = "THEME & COLORS",
        compatibleModelsTitle = "COMPATIBLE MODELS",
        languageTitle = "LANGUAGE / IDIOMA",
        aboutTitle = "ABOUT",

        deviceHistorySubtitle = "Battery logs & paired devices",
        themeColorsSubtitle = "Visual customization & OLED mode",
        compatibleModelsSubtitle = "52+ compatible Sony models",
        languageSubtitle = "English",
        aboutSubtitle = "NuclearWearable Windows v1.0.0",

        modelCatalogHeader = "Compatible Sony Models",
        modelCatalogSubheader = "52+ models covered with dedicated native vectors. You can select your model to lock it manually if you renamed it.",
        activeModelBadge = "Active",
        selectModelBtn = "Select",
        resetAutoDetectBtn = "Reset to auto-detection",
        autoDetectedBadge = "Auto",
        forcedBadge = "Locked",
        designLockedByYou = "Design locked by you",
        detectedByHardware = "Detected by hardware/name",
        searchPlaceholder = "Search model (e.g. XM5, LinkBuds, C500)...",
        allTab = "All",
        twsTab = "True Wireless",
        overEarTab = "Headband / Neckband",

        languageSheetTitle = "App Language",
        languageSheetSubtitle = "Select the interface language for NuclearWearable",

        deviceHistorySheetTitle = "Device History",
        deviceHistorySheetSubtitle = "Latest battery level recorded for each paired device",
        deviceHistoryEmpty = "No previous battery logs or paired devices found.",
        pairedDevicesSection = "Paired devices",
        connectButton = "Connect",
        disconnectButton = "Disconnect",
        refreshButton = "Scan",
        genericDevice = "Device",

        lightMode = "Light",
        darkMode = "Dark",
        systemMode = "System",
        oledNightTitle = "OLED pure black in night mode",
        oledDarkTitle = "OLED pure black",
        oledNightSub = "Use pure black when the system is in dark mode",
        oledDarkSub = "Completely black background (#000000)",
        accentColorsSection = "Accent colors",
        themeSubtitleSystem = "System",
        themeSubtitleDark = "Dark",
        themeSubtitleLight = "Light",
        themeSubtitleOledNight = " (OLED night mode)",
        themeSubtitleOledPure = " (OLED)",

        aboutSheetTitle = "About",
        aboutVersion = "Version Windows 1.0.0",
        aboutDescription = "Tactical audio companion for Windows designed under the NuclearUI visual system. Compatible with 52+ Sony headphone models, system tray battery telemetry, and instant noise cancellation control.",
        understoodBtn = "Got it",

        trayOpen = "Open NuclearWearable",
        trayExit = "Exit",
        trayBatteryTooltip = "NuclearWearable Sony Controller"
    )

    fun get(language: AppLanguage): LocalizedStrings {
        return when (language) {
            AppLanguage.ES -> spanish
            AppLanguage.EN -> english
        }
    }
}
