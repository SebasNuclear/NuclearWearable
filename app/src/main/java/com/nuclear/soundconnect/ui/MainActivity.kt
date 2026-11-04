package com.nuclear.soundconnect.ui



import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.material.icons.filled.Category

import com.nuclear.soundconnect.bluetooth.SonyModelFamily

import android.Manifest

import android.annotation.SuppressLint

import android.bluetooth.BluetoothAdapter

import android.bluetooth.BluetoothDevice

import android.bluetooth.BluetoothManager

import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context
import android.content.res.Configuration

import android.content.Intent

import android.content.pm.PackageManager

import android.os.Build

import android.os.Bundle

import android.widget.Toast

import androidx.activity.ComponentActivity

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.compose.setContent

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.animateColorAsState

import androidx.compose.animation.core.tween

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.BorderStroke

import androidx.compose.material.icons.filled.Language

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.BatteryFull

import androidx.compose.material.icons.filled.Check

import androidx.compose.material.icons.filled.ChevronRight

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.ContentCopy

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material.icons.filled.Headphones

import androidx.compose.material.icons.filled.Info

import androidx.compose.material.icons.filled.Palette

import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Security

import androidx.compose.material.icons.filled.Tune

import androidx.compose.material.icons.filled.Widgets

import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.HorizontalDivider

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.ModalBottomSheet

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.Slider

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.Slider

import androidx.compose.material3.SliderDefaults

import androidx.compose.material3.Surface

import androidx.compose.material3.Switch

import androidx.compose.material3.SwitchDefaults

import androidx.compose.material3.Text

import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateListOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.ColorFilter

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import com.nuclear.soundconnect.R

import com.nuclear.soundconnect.bluetooth.DeviceType

import com.nuclear.soundconnect.bluetooth.HexLogEntry

import com.nuclear.soundconnect.bluetooth.NoiseControlMode

import com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository

import com.nuclear.soundconnect.widget.BatteryRingCanvas

import com.nuclear.soundconnect.widget.WidgetSettingsActivity

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale



class MainActivity : ComponentActivity() {



    private lateinit var repository: SonyHeadsetRepository



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        repository = SonyHeadsetRepository.getInstance(applicationContext)



        setContent {

            MainApp(repository)

        }

    }

}



val ACCENT_PALETTE = listOf(

    Color(0xFF00E676), // Verde Atómico Nuclear

    Color(0xFF10B981), // Esmeralda

    Color(0xFF00B0FF), // Azul Eléctrico

    Color(0xFF007AFF), // Azul Zafiro One UI

    Color(0xFF00D2FF), // Cian Hielo Ártico

    Color(0xFFFFB703), // Oro Solar

    Color(0xFFFFB300), // Ámbar Retro

    Color(0xFFFF5722), // Magma Solar (Naranja)

    Color(0xFFFF1744), // Rojo Carmesí

    Color(0xFFFF007F), // Rosa Ciberpunk

    Color(0xFFA855F7), // Morado Neón

    Color(0xFF8B5CF6)  // Violeta Tóxico

)



@OptIn(ExperimentalMaterial3Api::class)

@SuppressLint("MissingPermission")

@Composable

fun MainApp(repository: SonyHeadsetRepository) {

    val context = LocalContext.current

    val state by repository.state.collectAsState()



    val prefs = remember { context.getSharedPreferences("nuclear_app_theme_prefs", Context.MODE_PRIVATE) }

    var themeMode by remember { mutableIntStateOf(prefs.getInt("theme_mode", 2)) } // 0: Claro, 1: Oscuro, 2: Sistema

    var isOled by remember { mutableStateOf(prefs.getBoolean("is_oled", true)) }

    var accentIndex by remember { mutableIntStateOf(prefs.getInt("accent_idx", 0)) }

    val accentColor = ACCENT_PALETTE.getOrNull(accentIndex) ?: Color(0xFF00E676)



    val isSystemNight = isSystemInDarkTheme()

    val isDark = when (themeMode) {

        0 -> false

        1 -> true

        else -> isSystemNight

    }



    val bgColor = if (isDark) {

        if (isOled) Color.Black else Color(0xFF121214)

    } else {

        Color(0xFFF2F2F7)

    }

    val cardColor = if (isDark) {

        if (isOled) Color(0xFF111113) else Color(0xFF1C1C1E)

    } else {

        Color.White

    }

    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)

    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF707070)

    val dividerColor = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000)



    // Permisos

    var permissionsGranted by remember { mutableStateOf(hasBluetoothPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.RequestMultiplePermissions()

    ) { results ->

        permissionsGranted = results.values.all { it }

        if (permissionsGranted) {

            repository.syncTelemetryAsync()

        }

    }



    LaunchedEffect(Unit) {

        if (!permissionsGranted) {

            val permissions = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)

                permissions.add(Manifest.permission.BLUETOOTH_SCAN)

            } else {

                permissions.add(Manifest.permission.BLUETOOTH)

                permissions.add(Manifest.permission.BLUETOOTH_ADMIN)

            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                permissions.add(Manifest.permission.POST_NOTIFICATIONS)

            }

            permissionLauncher.launch(permissions.toTypedArray())

        } else {

            repository.syncTelemetryAsync()

        }

    }



    // Hex logs

    val hexLogs = remember { mutableStateListOf<HexLogEntry>() }

    LaunchedEffect(Unit) {

        repository.rfcommClient.hexLogs.collect { log ->

            hexLogs.add(log)

            if (hexLogs.size > 200) hexLogs.removeAt(0)

        }

    }



    // Bottom sheets state

    val language by repository.language.collectAsState()

    val strings = remember(language) { AppStrings.get(language) }



    var showThemeSheet by remember { mutableStateOf(false) }

    var showHistorySheet by remember { mutableStateOf(false) }

    var showSoundSheet by remember { mutableStateOf(false) }

    var showLanguageSheet by remember { mutableStateOf(false) }

    var showAboutSheet by remember { mutableStateOf(false) }

    var showCompatibleModelsSheet by remember { mutableStateOf(false) }



    Surface(

        modifier = Modifier.fillMaxSize(),

        color = bgColor

    ) {

        LazyColumn(

            modifier = Modifier

                .fillMaxSize()

                .statusBarsPadding()

                .navigationBarsPadding()

                .padding(horizontal = 16.dp),

            verticalArrangement = Arrangement.spacedBy(16.dp)

        ) {

            // Top action bar

            item {

                Spacer(modifier = Modifier.height(12.dp))

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.End,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    IconButton(

                        onClick = { repository.syncTelemetryAsync() },

                        modifier = Modifier.size(36.dp)

                    ) {

                        if (state.isBusy) {

                            CircularProgressIndicator(

                                modifier = Modifier.size(20.dp),

                                color = accentColor,

                                strokeWidth = 2.dp

                            )

                        } else {

                            Icon(

                                imageVector = Icons.Default.Refresh,

                                contentDescription = "Sincronizar",

                                tint = textSecondary,

                                modifier = Modifier.size(22.dp)

                            )

                        }

                    }

                }

            }



            // Permisos

            if (!permissionsGranted) {

                item {

                    Card(

                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF9500)),

                        shape = RoundedCornerShape(20.dp),

                        modifier = Modifier.fillMaxWidth()

                    ) {

                        Column(modifier = Modifier.padding(16.dp)) {

                            Text(

                                text = strings.btPermTitle,

                                fontWeight = FontWeight.Bold,

                                color = Color(0xFFFF9500)

                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(

                                text = strings.btPermDesc,

                                color = textPrimary,

                                fontSize = 13.sp

                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(

                                onClick = {

                                    val reqs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

                                    } else {

                                        arrayOf(Manifest.permission.BLUETOOTH)

                                    }

                                    permissionLauncher.launch(reqs)

                                },

                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))

                            ) {

                                Text(strings.btPermGrantBtn, color = Color.Black)

                            }

                        }

                    }

                }

            }



            // 1. HERO SUPERIOR CENTRADO (Al aire sobre el fondo de pantalla)

            item {

                HeroShowcase(

                    state = state,

                    textPrimary = textPrimary,

                    textSecondary = textSecondary,

                    accentColor = accentColor,

                    isDark = isDark,

                    strings = strings

                )

            }



            // 2. ACCESOS RÁPIDOS DE CONTROL DE RUIDO (Píldoras circulares con icono)

            // Se muestra ÚNICAMENTE si el modelo cuenta con soporte de hardware ANC

            if (state.modelFamily.hasAnc) {

                item {

                    QuickNoiseControlCard(

                        state = state,

                        cardColor = cardColor,

                        accentColor = accentColor,

                        textPrimary = textPrimary,

                        textSecondary = textSecondary,

                        strings = strings,

                        onModeSelected = { mode ->

                            repository.setNoiseModeAsync(mode)

                        },

                        onLevelChanged = { level ->

                            repository.setAmbientLevel(level)

                        },

                        onVoiceFocusChanged = { focus ->

                            repository.setVoiceFocus(focus)

                        }

                    )

                }

            }



            // 3. MENÚ DE AJUSTES AGRUPADO (Tarjeta contenedora con filas y divisores)

            item {

                Card(

                    shape = RoundedCornerShape(26.dp),

                    colors = CardDefaults.cardColors(containerColor = cardColor),

                    modifier = Modifier.fillMaxWidth()

                ) {

                    Column(modifier = Modifier.fillMaxWidth()) {

                        // Fila 2: Historial de dispositivos

                        MenuRowItem(

                            icon = Icons.Default.Headphones,

                            iconBgColor = Color(0xFF34C759),

                            title = strings.deviceHistoryTitle,

                            subtitle = strings.deviceHistorySubtitle,

                            textPrimary = textPrimary,

                            textSecondary = textSecondary,

                            onClick = { showHistorySheet = true }

                        )



                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))



                        // Fila 2: Tema y colores

                        val themeTitle = when (themeMode) {

                            0 -> strings.themeSubtitleLight

                            1 -> strings.themeSubtitleDark + if (isOled) strings.themeSubtitleOledPure else ""

                            else -> strings.themeSubtitleSystem + if (isOled) strings.themeSubtitleOledNight else ""

                        }

                        MenuRowItem(

                            icon = Icons.Default.Palette,

                            iconBgColor = Color(0xFF007AFF),

                            title = strings.themeColorsTitle,

                            subtitle = themeTitle,

                            textPrimary = textPrimary,

                            textSecondary = textSecondary,

                            onClick = { showThemeSheet = true }

                        )



                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))



                        // Fila 3: Modelos compatibles (Catálogo visual de familias)

                        MenuRowItem(

                            icon = Icons.Default.Widgets,

                            iconBgColor = Color(0xFF00D2FF),

                            title = strings.compatibleModelsTitle,

                            subtitle = strings.compatibleModelsSubtitle,

                            textPrimary = textPrimary,

                            textSecondary = textSecondary,

                            onClick = { showCompatibleModelsSheet = true }

                        )



                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))



                        // Fila 4: Idioma / Language

                        MenuRowItem(

                            icon = Icons.Default.Language,

                            iconBgColor = Color(0xFFA855F7),

                            title = strings.languageTitle,

                            subtitle = strings.languageSubtitle,

                            textPrimary = textPrimary,

                            textSecondary = textSecondary,

                            onClick = { showLanguageSheet = true }

                        )



                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))



                        // Fila 5: Acerca de

                        MenuRowItem(

                            icon = Icons.Default.Info,

                            iconBgColor = Color(0xFF64748B),

                            title = strings.aboutTitle,

                            subtitle = strings.aboutSubtitle,

                            textPrimary = textPrimary,

                            textSecondary = textSecondary,

                            onClick = { showAboutSheet = true }

                        )

                    }

                }

            }







            item { Spacer(modifier = Modifier.height(32.dp)) }

        }

    }



    // Modal: Tema y Colores

    if (showThemeSheet) {

        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(

            onDismissRequest = { showThemeSheet = false },

            sheetState = sheetState,

            containerColor = cardColor

        ) {

            ThemeSettingsSheet(

                themeMode = themeMode,

                isOled = isOled,

                accentIndex = accentIndex,

                textPrimary = textPrimary,

                textSecondary = textSecondary,

                cardColor = cardColor,

                strings = strings,

                onThemeModeChanged = { mode ->

                    themeMode = mode

                    prefs.edit().putInt("theme_mode", mode).apply()

                },

                onOledChanged = { oled ->

                    isOled = oled

                    prefs.edit().putBoolean("is_oled", oled).apply()

                },

                onAccentChanged = { idx ->

                    accentIndex = idx

                    prefs.edit().putInt("accent_idx", idx).apply()

                    com.nuclear.soundconnect.widget.NuclearWidgetManager.updateAllWidgets(context)

                    val newAccent = com.nuclear.soundconnect.widget.WidgetPrefsHelper.getAccentColor(context)
                    repository.syncAccentColor(newAccent)

                },

                onClose = { showThemeSheet = false }

            )

        }

    }



    // Modal: Historial de Dispositivos

    if (showHistorySheet) {

        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(

            onDismissRequest = { showHistorySheet = false },

            sheetState = sheetState,

            containerColor = cardColor

        ) {

            DeviceHistorySheet(

                repository = repository,

                textPrimary = textPrimary,

                textSecondary = textSecondary,

                accentColor = accentColor,

                strings = strings,

                onClose = { showHistorySheet = false }

            )

        }

    }



    // Modal: Modelos Compatibles

    if (showCompatibleModelsSheet) {

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(

            onDismissRequest = { showCompatibleModelsSheet = false },

            sheetState = sheetState,

            containerColor = cardColor

        ) {

            CompatibleModelsSheet(

                state = state,

                cardColor = cardColor,

                accentColor = accentColor,

                textPrimary = textPrimary,

                textSecondary = textSecondary,

                dividerColor = dividerColor,

                language = language,

                strings = strings,

                onSelectModel = { fam ->

                    repository.setUserForcedModel(state.deviceAddress, fam)

                    showCompatibleModelsSheet = false

                },

                onResetAutoDetect = {

                    repository.setUserForcedModel(state.deviceAddress, null)

                    showCompatibleModelsSheet = false

                },

                onClose = { showCompatibleModelsSheet = false }

            )

        }

    }



    // Modal: Selector de Idioma (Submenú listado)

    if (showLanguageSheet) {

        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(

            onDismissRequest = { showLanguageSheet = false },

            sheetState = sheetState,

            containerColor = cardColor

        ) {

            LanguageSheet(

                currentLanguage = language,

                onSelectLanguage = { selected ->

                    repository.setLanguage(selected)

                    showLanguageSheet = false

                },

                onClose = { showLanguageSheet = false },

                strings = strings,

                textPrimary = textPrimary,

                textSecondary = textSecondary,

                accentColor = accentColor

            )

        }

    }



    // Modal: Acerca de la Aplicación

    if (showAboutSheet) {

        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(

            onDismissRequest = { showAboutSheet = false },

            sheetState = sheetState,

            containerColor = cardColor

        ) {

            AboutSheet(

                strings = strings,

                textPrimary = textPrimary,

                textSecondary = textSecondary,

                accentColor = accentColor,

                onClose = { showAboutSheet = false }

            )

        }

    }

}



/**

 * Hero Superior Centrado: Ilustración del modelo, nombre centrado y estado de carga compacto.

 */

@Composable

fun HeroShowcase(

    state: com.nuclear.soundconnect.bluetooth.HeadsetState,

    textPrimary: Color,

    textSecondary: Color,

    accentColor: Color,

    isDark: Boolean,

    strings: LocalizedStrings

) {

    val drawables = BatteryRingCanvas.getDrawablesForFamily(state.modelFamily)



    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(vertical = 12.dp),

        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        val connectedTint = if (isDark) Color.White else Color(0xFF1C1C1E)

        val disconnectedTint = if (isDark) Color(0xFF505054).copy(alpha = 0.45f) else Color(0xFF8E8E93).copy(alpha = 0.40f)



        // Imagen grande centrada (+20% tamaño para mayor protagonismo hero)

        if (state.deviceType == DeviceType.OVER_EAR) {

            val isHeadphoneConnected = state.isConnected && state.singleBattery.isAvailable

            Image(

                painter = painterResource(drawables.headphone),

                contentDescription = state.deviceName,

                modifier = Modifier

                    .size(156.dp)

                    .padding(8.dp),

                colorFilter = ColorFilter.tint(if (isHeadphoneConnected) connectedTint else disconnectedTint)

            )

        } else {

            val isLeftConnected = state.isConnected && state.leftBattery.isAvailable

            val isCaseConnected = state.isConnected && state.caseBattery.isAvailable

            val isRightConnected = state.isConnected && state.rightBattery.isAvailable



            Row(

                horizontalArrangement = Arrangement.Center,

                verticalAlignment = Alignment.CenterVertically,

                modifier = Modifier.padding(vertical = 8.dp)

            ) {

                Image(

                    painter = painterResource(drawables.leftBud),

                    contentDescription = "L",

                    modifier = Modifier.size(72.dp),

                    colorFilter = ColorFilter.tint(if (isLeftConnected) connectedTint else disconnectedTint)

                )

                Spacer(modifier = Modifier.width(18.dp))

                Image(

                    painter = painterResource(drawables.case),

                    contentDescription = "Estuche",

                    modifier = Modifier.size(92.dp),

                    colorFilter = ColorFilter.tint(if (isCaseConnected) connectedTint else disconnectedTint)

                )

                Spacer(modifier = Modifier.width(18.dp))

                Image(

                    painter = painterResource(drawables.rightBud),

                    contentDescription = "R",

                    modifier = Modifier.size(72.dp),

                    colorFilter = ColorFilter.tint(if (isRightConnected) connectedTint else disconnectedTint)

                )

            }

        }



        Spacer(modifier = Modifier.height(10.dp))



        // Nombre del dispositivo en grande y centrado

        Text(

            text = state.deviceName,

            fontSize = 26.sp,

            fontWeight = FontWeight.Bold,

            color = textPrimary

        )



        Spacer(modifier = Modifier.height(4.dp))



        // Fila compacta de batería con icono

        Row(

            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.Center

        ) {

            Icon(

                imageVector = Icons.Default.BatteryFull,

                contentDescription = null,

                tint = if (state.isConnected) accentColor else textSecondary,

                modifier = Modifier.size(17.dp)

            )

            Spacer(modifier = Modifier.width(6.dp))



            if (state.deviceType == DeviceType.OVER_EAR) {

                val batText = if (state.singleBattery.isAvailable) "${state.singleBattery.level}%" else "--%"

                val statusText = if (state.isConnected) strings.connected else strings.disconnected

                Text(

                    text = "$batText  |  $statusText",

                    fontSize = 14.sp,

                    fontWeight = FontWeight.Medium,

                    color = textSecondary

                )

            } else {

                if (state.isConnected) {

                    val parts = mutableListOf<String>()

                    if (state.leftBattery.isAvailable) parts.add("${strings.leftBud} ${state.leftBattery.level}%")

                    if (state.rightBattery.isAvailable) parts.add("${strings.rightBud} ${state.rightBattery.level}%")

                    if (state.caseBattery.isAvailable) parts.add("${strings.caseLabel} ${state.caseBattery.level}%")

                    val display = if (parts.isNotEmpty()) parts.joinToString("   •   ") else strings.connected

                    Text(

                        text = display,

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Medium,

                        color = textSecondary

                    )

                } else {

                    Text(

                        text = strings.disconnected,

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Medium,

                        color = textSecondary

                    )

                }

            }

        }

    }

}



/**

 * Accesos rápidos con 3 botones de control de ruido estilo reloj/wearable.

 */

@Composable

fun QuickNoiseControlCard(

    state: com.nuclear.soundconnect.bluetooth.HeadsetState,

    cardColor: Color,

    accentColor: Color,

    textPrimary: Color,

    textSecondary: Color,

    strings: LocalizedStrings,

    onModeSelected: (NoiseControlMode) -> Unit,

    onLevelChanged: (Int) -> Unit,

    onVoiceFocusChanged: (Boolean) -> Unit

) {

    Card(

        shape = RoundedCornerShape(26.dp),

        colors = CardDefaults.cardColors(containerColor = cardColor),

        modifier = Modifier.fillMaxWidth()

    ) {

        Column(

            modifier = Modifier

                .fillMaxWidth()

                .padding(16.dp)

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceEvenly

            ) {

                // ANC

                QuickActionButton(

                    iconRes = R.drawable.ic_anc_on,

                    label = strings.noiseCancelling,

                    isActive = state.noiseMode == NoiseControlMode.ANC,

                    accentColor = accentColor,

                    textSecondary = textSecondary,

                    onClick = { onModeSelected(NoiseControlMode.ANC) }

                )



                // AMBIENT

                QuickActionButton(

                    iconRes = R.drawable.ic_ambient_sound,

                    label = strings.ambientSound,

                    isActive = state.noiseMode == NoiseControlMode.AMBIENT,

                    accentColor = accentColor,

                    textSecondary = textSecondary,

                    onClick = { onModeSelected(NoiseControlMode.AMBIENT) }

                )



                // OFF

                QuickActionButton(

                    iconRes = R.drawable.ic_anc_off,

                    label = strings.noiseOff,

                    isActive = state.noiseMode == NoiseControlMode.OFF,

                    accentColor = accentColor,

                    textSecondary = textSecondary,

                    onClick = { onModeSelected(NoiseControlMode.OFF) }

                )

            }



            // Desplegable suave si el modo activo es Ambiente

            AnimatedVisibility(visible = state.noiseMode == NoiseControlMode.AMBIENT) {

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(top = 16.dp)

                ) {

                    var sliderPos by remember(state.ambientLevel) { mutableFloatStateOf(state.ambientLevel.toFloat()) }



                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text(text = strings.ambientLevel, fontSize = 13.sp, color = textPrimary)

                        Text(

                            text = "${sliderPos.toInt()} / 20",

                            fontSize = 13.sp,

                            fontWeight = FontWeight.Bold,

                            color = accentColor

                        )

                    }



                    Slider(

                        value = sliderPos,

                        onValueChange = { sliderPos = it },

                        onValueChangeFinished = { onLevelChanged(sliderPos.toInt()) },

                        valueRange = 1f..20f,

                        steps = 18,

                        colors = SliderDefaults.colors(

                            thumbColor = accentColor,

                            activeTrackColor = accentColor,

                            inactiveTrackColor = textSecondary.copy(alpha = 0.25f)

                        )

                    )



                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text(text = strings.voiceFocus, fontSize = 13.sp, color = textPrimary)

                        Switch(

                            checked = state.voiceFocus,

                            onCheckedChange = onVoiceFocusChanged,

                            colors = SwitchDefaults.colors(

                                checkedThumbColor = Color.White,

                                checkedTrackColor = accentColor

                            )

                        )

                    }

                }

            }

        }

    }

}



@Composable

fun QuickActionButton(

    iconRes: Int,

    label: String,

    isActive: Boolean,

    accentColor: Color,

    textSecondary: Color,

    onClick: () -> Unit

) {

    val bgAnim by animateColorAsState(

        targetValue = if (isActive) accentColor else Color(0x148E8E93),

        animationSpec = tween(220)

    )

    val iconTintAnim by animateColorAsState(

        targetValue = if (isActive) Color.White else textSecondary,

        animationSpec = tween(220)

    )



    Column(

        horizontalAlignment = Alignment.CenterHorizontally,

        modifier = Modifier

            .clip(RoundedCornerShape(16.dp))

            .clickable(onClick = onClick)

            .padding(horizontal = 8.dp, vertical = 6.dp)

    ) {

        Box(

            modifier = Modifier

                .size(52.dp)

                .clip(CircleShape)

                .background(bgAnim),

            contentAlignment = Alignment.Center

        ) {

            Image(

                painter = painterResource(iconRes),

                contentDescription = label,

                modifier = Modifier.size(24.dp),

                colorFilter = ColorFilter.tint(iconTintAnim)

            )

        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(

            text = label,

            fontSize = 11.sp,

            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,

            color = if (isActive) accentColor else textSecondary

        )

    }

}



/**

 * Fila del menú agrupado con píldora circular de icono y texto limpio.

 */

@Composable

fun MenuRowItem(

    icon: ImageVector,

    iconBgColor: Color,

    title: String,

    subtitle: String? = null,

    textPrimary: Color,

    textSecondary: Color,

    onClick: () -> Unit

) {

    Row(

        modifier = Modifier

            .fillMaxWidth()

            .clickable(onClick = onClick)

            .padding(horizontal = 16.dp, vertical = 14.dp),

        verticalAlignment = Alignment.CenterVertically,

        horizontalArrangement = Arrangement.SpaceBetween

    ) {

        Row(

            verticalAlignment = Alignment.CenterVertically,

            modifier = Modifier.weight(1f)

        ) {

            Box(

                modifier = Modifier

                    .size(38.dp)

                    .clip(CircleShape)

                    .background(iconBgColor),

                contentAlignment = Alignment.Center

            ) {

                Icon(

                    imageVector = icon,

                    contentDescription = null,

                    tint = Color.White,

                    modifier = Modifier.size(20.dp)

                )

            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {

                Text(

                    text = title,

                    fontSize = 15.sp,

                    fontWeight = FontWeight.SemiBold,

                    color = textPrimary

                )

                if (subtitle != null) {

                    Text(

                        text = subtitle,

                        fontSize = 12.sp,

                        color = textSecondary

                    )

                }

            }

        }

        Icon(

            imageVector = Icons.Default.ChevronRight,

            contentDescription = null,

            tint = textSecondary.copy(alpha = 0.5f),

            modifier = Modifier.size(20.dp)

        )

    }

}



/**

 * Bottom Sheet: Jerarquía de temas Claro / Oscuro (OLED switch) / Sistema (OLED switch) + Colores.

 */

@Composable

fun ThemeSettingsSheet(

    themeMode: Int,

    isOled: Boolean,

    accentIndex: Int,

    textPrimary: Color,

    textSecondary: Color,

    cardColor: Color,

    strings: LocalizedStrings,

    onThemeModeChanged: (Int) -> Unit,

    onOledChanged: (Boolean) -> Unit,

    onAccentChanged: (Int) -> Unit,

    onClose: () -> Unit

) {

    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 24.dp, vertical = 16.dp)

    ) {

        Text(

            text = strings.themeColorsTitle,

            fontSize = 20.sp,

            fontWeight = FontWeight.Bold,

            color = textPrimary

        )

        Spacer(modifier = Modifier.height(18.dp))



        // Selector de 3 modos: Claro | Oscuro | Sistema

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .clip(RoundedCornerShape(14.dp))

                .background(Color(0x1F8E8E93))

                .padding(4.dp),

            horizontalArrangement = Arrangement.spacedBy(4.dp)

        ) {

            listOf(0 to strings.lightMode, 1 to strings.darkMode, 2 to strings.systemMode).forEach { (idx, label) ->

                val isSelected = themeMode == idx

                Box(

                    modifier = Modifier

                        .weight(1f)

                        .height(38.dp)

                        .clip(RoundedCornerShape(10.dp))

                        .background(if (isSelected) (if (isOled) Color(0xFF2C2C2E) else Color.White) else Color.Transparent)

                        .clickable { onThemeModeChanged(idx) },

                    contentAlignment = Alignment.Center

                ) {

                    Text(

                        text = label,

                        fontSize = 13.sp,

                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,

                        color = if (isSelected) (if (isOled) Color.White else Color.Black) else textSecondary

                    )

                }

            }

        }



        Spacer(modifier = Modifier.height(18.dp))



        // Switch OLED: visible para Oscuro (1) y Sistema (2)

        if (themeMode == 1 || themeMode == 2) {

            val oledTitle = if (themeMode == 1) strings.oledDarkTitle else strings.oledNightTitle

            val oledSub = if (themeMode == 1) strings.oledDarkSub else strings.oledNightSub



            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(vertical = 8.dp),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Column(modifier = Modifier.weight(1f)) {

                    Text(text = oledTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)

                    Text(text = oledSub, fontSize = 12.sp, color = textSecondary)

                }

                Switch(

                    checked = isOled,

                    onCheckedChange = onOledChanged,

                    colors = SwitchDefaults.colors(

                        checkedThumbColor = Color.White,

                        checkedTrackColor = ACCENT_PALETTE.getOrNull(accentIndex) ?: Color(0xFF00E676)

                    )

                )

            }

            Spacer(modifier = Modifier.height(12.dp))

        }



        // Selector de Colores de Acento (12 Colores Oficiales del Nuclear Design System)

        Text(

            text = strings.accentColorsSection,

            fontSize = 14.sp,

            fontWeight = FontWeight.SemiBold,

            color = textPrimary

        )

        Spacer(modifier = Modifier.height(12.dp))



        // Presentación minimalista en 2 filas de 6 esferas de color (sin nombres de texto)

        ACCENT_PALETTE.chunked(6).forEachIndexed { rowIndex, rowColors ->

            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(vertical = 4.dp),

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                rowColors.forEachIndexed { colIndex, color ->

                    val index = rowIndex * 6 + colIndex

                    val selected = accentIndex == index

                    Box(

                        modifier = Modifier

                            .size(44.dp)

                            .clip(CircleShape)

                            .background(color)

                            .border(

                                width = if (selected) 3.dp else 0.dp,

                                color = if (selected) textPrimary else Color.Transparent,

                                shape = CircleShape

                            )

                            .clickable { onAccentChanged(index) },

                        contentAlignment = Alignment.Center

                    ) {

                        if (selected) {

                            Icon(

                                imageVector = Icons.Default.Check,

                                contentDescription = null,

                                tint = Color.White,

                                modifier = Modifier.size(20.dp)

                            )

                        }

                    }

                }

            }

        }



        Spacer(modifier = Modifier.height(28.dp))

    }

}



/**

 * Catálogo Visual e Interactivo de las 16 Familias Industriales Sony

 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibleModelsSheet(
    state: com.nuclear.soundconnect.bluetooth.HeadsetState,
    cardColor: Color,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color,
    language: AppLanguage,
    strings: LocalizedStrings,
    onSelectModel: (SonyModelFamily) -> Unit,
    onResetAutoDetect: () -> Unit,
    onClose: () -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todos, 1: TWS, 2: Diadema
    var searchQuery by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val allFamilies = remember {
        SonyModelFamily.entries.filter { it != SonyModelFamily.GENERIC_TWS && it != SonyModelFamily.GENERIC_OVER_EAR }
    }

    val filtered = remember(selectedFilter, searchQuery) {
        allFamilies.filter { fam ->
            val matchFilter = when (selectedFilter) {
                1 -> fam.defaultDeviceType == DeviceType.TWS
                2 -> fam.defaultDeviceType == DeviceType.OVER_EAR
                else -> true
            }
            val q = searchQuery.trim().uppercase()
            val matchSearch = if (q.isBlank()) true else {
                fam.displayName.uppercase().contains(q) ||
                fam.seriesCategory.uppercase().contains(q) ||
                fam.modelsCovered.any { it.uppercase().contains(q) }
            }
            matchFilter && matchSearch
        }
    }

    val isForced = state.forcedModelFamily != null
    val activeDrawables = BatteryRingCanvas.getDrawablesForFamily(state.modelFamily)

    @Composable
    fun HeaderSection() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.modelCatalogHeader,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = strings.modelCatalogSubheader,
                    fontSize = 11.5.sp,
                    color = textSecondary,
                    lineHeight = 15.sp,
                    maxLines = if (isLandscape) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = textSecondary)
            }
        }
    }

    @Composable
    fun ActiveBannerCard() {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isForced) accentColor.copy(alpha = 0.12f) else textSecondary.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(textSecondary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.modelFamily.defaultDeviceType == DeviceType.TWS) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(activeDrawables.leftBud),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                                Image(
                                    painter = painterResource(activeDrawables.case),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                                Image(
                                    painter = painterResource(activeDrawables.rightBud),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                            }
                        } else {
                            Image(
                                painter = painterResource(activeDrawables.headphone),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                colorFilter = ColorFilter.tint(textPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.modelFamily.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isForced) accentColor.copy(alpha = 0.18f) else textSecondary.copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isForced) strings.forcedBadge else strings.autoDetectedBadge,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isForced) accentColor else textSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isForced) strings.designLockedByYou else strings.detectedByHardware,
                            fontSize = 11.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isForced) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onResetAutoDetect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = cardColor.copy(alpha = 0.6f),
                            contentColor = accentColor
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = strings.resetAutoDetectBtn,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun FilterTabs() {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(strings.allTab, strings.twsTab, strings.overEarTab)
            filters.forEachIndexed { index, label ->
                val isSelected = selectedFilter == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) accentColor else textSecondary.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .clickable { selectedFilter = index }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    @Composable
    fun ModelList(modifier: Modifier = Modifier) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { fam ->
                val isCurrent = state.modelFamily == fam
                val drawables = BatteryRingCanvas.getDrawablesForFamily(fam)

                val subtitleText = when {
                    fam.modelsCovered.size > 1 -> fam.modelsCovered.joinToString(", ")
                    fam.modelsCovered.firstOrNull()?.equals(fam.displayName, ignoreCase = true) == true -> fam.seriesCategory
                    else -> fam.modelsCovered.firstOrNull() ?: fam.seriesCategory
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCurrent) { onSelectModel(fam) }
                        .border(
                            width = if (isCurrent) 1.5.dp else 0.5.dp,
                            color = if (isCurrent) accentColor else dividerColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vista previa de los iconos
                        Box(
                            modifier = Modifier
                                .size(width = 58.dp, height = 42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(textSecondary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (fam.defaultDeviceType == DeviceType.TWS) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(drawables.leftBud),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        colorFilter = ColorFilter.tint(textPrimary)
                                    )
                                    Image(
                                        painter = painterResource(drawables.case),
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                        colorFilter = ColorFilter.tint(textPrimary)
                                    )
                                    Image(
                                        painter = painterResource(drawables.rightBud),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        colorFilter = ColorFilter.tint(textPrimary)
                                    )
                                }
                            } else {
                                Image(
                                    painter = painterResource(drawables.headphone),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = fam.displayName,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (fam.hasAnc) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "ANC",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34C759),
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (language == AppLanguage.ES) "Batería" else "Battery",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitleText,
                                fontSize = 11.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = strings.activeModelBadge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Button(
                                onClick = { onSelectModel(fam) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = strings.selectModelBtn,
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isLandscape) {
        // Modo Horizontal: Layout de 2 columnas para aprovechar el ancho completo y dar máxima altura a la lista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeaderSection()
                ActiveBannerCard()
                FilterTabs()
            }
            ModelList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        // Modo Vertical: Flujo vertical con espaciado equilibrado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            HeaderSection()
            Spacer(modifier = Modifier.height(10.dp))
            ActiveBannerCard()
            Spacer(modifier = Modifier.height(10.dp))
            FilterTabs()
            Spacer(modifier = Modifier.height(10.dp))
            ModelList(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun LanguageSheet(

    currentLanguage: AppLanguage,

    onSelectLanguage: (AppLanguage) -> Unit,

    onClose: () -> Unit,

    strings: LocalizedStrings,

    textPrimary: Color,

    textSecondary: Color,

    accentColor: Color

) {

    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 24.dp, vertical = 16.dp)

            .navigationBarsPadding()

    ) {

        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(

                    text = strings.languageSheetTitle,

                    fontSize = 20.sp,

                    fontWeight = FontWeight.Bold,

                    color = textPrimary

                )

                Text(

                    text = strings.languageSheetSubtitle,

                    fontSize = 12.sp,

                    color = textSecondary

                )

            }

            IconButton(onClick = onClose) {

                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = textSecondary)

            }

        }



        Spacer(modifier = Modifier.height(18.dp))



        AppLanguage.entries.forEach { lang ->

            val isSelected = currentLanguage == lang

            Card(

                shape = RoundedCornerShape(14.dp),

                colors = CardDefaults.cardColors(

                    containerColor = if (isSelected) accentColor.copy(alpha = 0.12f) else textSecondary.copy(alpha = 0.08f)

                ),

                modifier = Modifier

                    .fillMaxWidth()

                    .clickable {

                        onSelectLanguage(lang)

                        onClose()

                    }

                    .border(

                        width = if (isSelected) 1.5.dp else 0.dp,

                        color = if (isSelected) accentColor else Color.Transparent,

                        shape = RoundedCornerShape(14.dp)

                    )

            ) {

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(horizontal = 16.dp, vertical = 14.dp),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Column {

                        Text(

                            text = lang.displayName,

                            fontSize = 15.sp,

                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,

                            color = textPrimary

                        )

                        Text(

                            text = if (lang == AppLanguage.ES) "Español (Latinoamérica / España)" else "English (US / UK / Global)",

                            fontSize = 11.5.sp,

                            color = textSecondary

                        )

                    }

                    if (isSelected) {

                        Icon(

                            imageVector = Icons.Default.Check,

                            contentDescription = null,

                            tint = accentColor,

                            modifier = Modifier.size(20.dp)

                        )

                    }

                }

            }

            Spacer(modifier = Modifier.height(10.dp))

        }



        Spacer(modifier = Modifier.height(16.dp))

    }

}



@Composable

fun AboutSheet(

    strings: LocalizedStrings,

    textPrimary: Color,

    textSecondary: Color,

    accentColor: Color,

    onClose: () -> Unit

) {

    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 24.dp, vertical = 20.dp),

        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        Box(

            modifier = Modifier

                .size(68.dp)

                .clip(CircleShape)

                .background(accentColor.copy(alpha = 0.15f))

                .border(2.dp, accentColor, CircleShape),

            contentAlignment = Alignment.Center

        ) {

            Icon(

                imageVector = Icons.Default.Headphones,

                contentDescription = null,

                tint = accentColor,

                modifier = Modifier.size(36.dp)

            )

        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(

            text = strings.appName,

            fontSize = 22.sp,

            fontWeight = FontWeight.Bold,

            color = textPrimary

        )

        Text(

            text = strings.aboutVersion,

            fontSize = 13.sp,

            fontWeight = FontWeight.Medium,

            color = textSecondary

        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(

            text = strings.aboutDescription,

            fontSize = 13.sp,

            color = textSecondary,

            textAlign = androidx.compose.ui.text.style.TextAlign.Center,

            lineHeight = 18.sp

        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(

            onClick = onClose,

            modifier = Modifier

                .fillMaxWidth()

                .height(46.dp),

            shape = RoundedCornerShape(14.dp),

            colors = ButtonDefaults.buttonColors(containerColor = accentColor)

        ) {

            Text(strings.understoodBtn, color = Color.Black, fontWeight = FontWeight.SemiBold)

        }

        Spacer(modifier = Modifier.height(12.dp))

    }

}



/**

 * Bottom Sheet: Historial completo de batería de todos los dispositivos conectados.

 */

@SuppressLint("MissingPermission")

@Composable

fun DeviceHistorySheet(

    repository: SonyHeadsetRepository,

    textPrimary: Color,

    textSecondary: Color,

    accentColor: Color,

    strings: LocalizedStrings,

    onClose: () -> Unit

) {

    val context = LocalContext.current

    val historyList = remember { repository.getDeviceHistory() }



    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    val bonded = remember {

        try {

            adapter?.bondedDevices?.filter { SonyHeadsetRepository.isSonyAudioDevice(it) } ?: emptyList()

        } catch (_: SecurityException) {

            emptyList()

        }

    }



    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 24.dp, vertical = 16.dp)

    ) {

        Text(

            text = strings.deviceHistorySheetTitle,

            fontSize = 20.sp,

            fontWeight = FontWeight.Bold,

            color = textPrimary

        )

        Text(

            text = strings.deviceHistorySheetSubtitle,

            fontSize = 12.sp,

            color = textSecondary,

            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)

        )



        if (historyList.isEmpty() && bonded.isEmpty()) {

            Text(

                text = strings.deviceHistoryEmpty,

                color = textSecondary,

                fontSize = 13.sp,

                modifier = Modifier.padding(vertical = 20.dp)

            )

        } else {

            LazyColumn(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(340.dp),

                verticalArrangement = Arrangement.spacedBy(10.dp)

            ) {

                if (historyList.isNotEmpty()) {

                    items(historyList) { record ->

                        Card(

                            shape = RoundedCornerShape(16.dp),

                            colors = CardDefaults.cardColors(containerColor = Color(0x128E8E93)),

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Column(modifier = Modifier.padding(14.dp)) {

                                Row(

                                    modifier = Modifier.fillMaxWidth(),

                                    horizontalArrangement = Arrangement.SpaceBetween,

                                    verticalAlignment = Alignment.CenterVertically

                                ) {

                                    Text(

                                        text = record.name,

                                        fontSize = 15.sp,

                                        fontWeight = FontWeight.Bold,

                                        color = textPrimary

                                    )

                                    val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(record.lastSeenTimestamp))

                                    Text(text = timeStr, fontSize = 11.sp, color = textSecondary)

                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (record.deviceType == DeviceType.OVER_EAR) {

                                    Text(

                                        text = "${strings.headphoneLabel}: ${if (record.singleLevel > 0) "${record.singleLevel}%" else "--%"}",

                                        fontSize = 13.sp,

                                        color = accentColor,

                                        fontWeight = FontWeight.Medium

                                    )

                                } else {

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                                        Text(text = "${strings.leftBud}: ${if (record.leftLevel > 0) "${record.leftLevel}%" else "--"}", fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Medium)

                                        Text(text = "${strings.rightBud}: ${if (record.rightLevel > 0) "${record.rightLevel}%" else "--"}", fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Medium)

                                        Text(text = "${strings.caseLabel}: ${if (record.caseLevel > 0) "${record.caseLevel}%" else "--"}", fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Medium)

                                    }

                                }

                            }

                        }

                    }

                }



                if (bonded.isNotEmpty()) {

                    item {

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(

                            text = strings.pairedDevicesSection,

                            fontSize = 13.sp,

                            fontWeight = FontWeight.Bold,

                            color = textSecondary

                        )

                    }

                    items(bonded) { dev ->

                        Row(

                            modifier = Modifier

                                .fillMaxWidth()

                                .clip(RoundedCornerShape(12.dp))

                                .background(Color(0x0E8E8E93))

                            .clickable {

                                repository.setPreferredDevice(dev)

                                onClose()

                            }

                            .padding(12.dp),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Column {

                                Text(text = dev.name ?: strings.genericDevice, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)

                                Text(text = dev.address, color = textSecondary, fontSize = 11.sp)

                            }

                            Text(text = strings.connectButton, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        }

                    }

                }

            }

        }



        Spacer(modifier = Modifier.height(20.dp))

    }

}



@Composable

fun HexMonitorCard(

    logs: List<HexLogEntry>,

    onClear: () -> Unit,

    onCopy: () -> Unit

) {

    val listState = rememberLazyListState()



    LaunchedEffect(logs.size) {

        if (logs.isNotEmpty()) {

            listState.animateScrollToItem(logs.size - 1)

        }

    }



    Card(

        shape = RoundedCornerShape(20.dp),

        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),

        modifier = Modifier.fillMaxWidth()

    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(

                        modifier = Modifier

                            .size(10.dp)

                            .clip(CircleShape)

                            .background(Color(0xFF34C759))

                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(

                        text = "Registro de paquetes RFCOMM",

                        color = Color.White,

                        fontWeight = FontWeight.SemiBold,

                        fontSize = 14.sp

                    )

                }



                Row {

                    IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {

                        Icon(

                            imageVector = Icons.Default.ContentCopy,

                            contentDescription = "Copiar",

                            tint = Color(0xFF8E8E93),

                            modifier = Modifier.size(16.dp)

                        )

                    }

                    IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {

                        Icon(

                            imageVector = Icons.Default.Delete,

                            contentDescription = "Limpiar",

                            tint = Color(0xFF8E8E93),

                            modifier = Modifier.size(16.dp)

                        )

                    }

                }

            }



            Spacer(modifier = Modifier.height(10.dp))



            Box(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(180.dp)

                    .clip(RoundedCornerShape(12.dp))

                    .background(Color(0xFF0D0D0E))

                    .padding(8.dp)

            ) {

                if (logs.isEmpty()) {

                    Text(

                        text = "Esperando paquetes RFCOMM... Toca el botón de sincronizar o cambia de modo para transmitir.",

                        color = Color(0xFF55555A),

                        fontSize = 12.sp,

                        fontFamily = FontFamily.Monospace,

                        modifier = Modifier.align(Alignment.Center)

                    )

                } else {

                    LazyColumn(

                        state = listState,

                        modifier = Modifier.fillMaxSize()

                    ) {

                        items(logs) { entry ->

                            val color = when (entry.direction) {

                                HexLogEntry.Direction.TX -> Color(0xFF007AFF)

                                HexLogEntry.Direction.RX -> Color(0xFF34C759)

                                HexLogEntry.Direction.INFO -> Color(0xFFFFD700)

                                HexLogEntry.Direction.ERROR -> Color(0xFFFF3B30)

                            }

                            val prefix = when (entry.direction) {

                                HexLogEntry.Direction.TX -> ">> TX:"

                                HexLogEntry.Direction.RX -> "<< RX:"

                                HexLogEntry.Direction.INFO -> "? INFO:"

                                HexLogEntry.Direction.ERROR -> "? ERR:"

                            }



                            Column(modifier = Modifier.padding(vertical = 2.dp)) {

                                Text(

                                    text = "$prefix ${entry.summary}",

                                    color = color,

                                    fontSize = 11.sp,

                                    fontWeight = FontWeight.SemiBold,

                                    fontFamily = FontFamily.Monospace

                                )

                                if (entry.hex.isNotBlank()) {

                                    Text(

                                        text = entry.hex,

                                        color = Color(0xFFCCCCCC),

                                        fontSize = 10.sp,

                                        fontFamily = FontFamily.Monospace

                                    )

                                }

                            }

                        }

                    }

                }

            }

        }

    }

}



private fun hasBluetoothPermissions(context: Context): Boolean {

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    } else {

        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED

    }

}

