package com.nuclear.soundconnect.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclear.soundconnect.bluetooth.*

/**
 * Pantalla Principal 1:1 de NuclearWearable para Windows.
 * Sigue fielmente la arquitectura Single-Surface y tokens de diseño de NuclearUI:
 * 1. Vitrina Hero al aire (Free-Floating Hero) con iconos grandes del dispositivo.
 * 2. Tarjeta squircle (26.dp) con accesos rápidos circulares (52.dp) de control de ruido.
 * 3. Tarjeta de ajustes agrupados con filas homogéneas, píldoras circulares saturadas (38.dp) y divisores finos (0.5.dp).
 * 4. Modales flotantes animados con esquinas redondeadas de 26.dp.
 */
@Composable
fun WindowScope.MainView(
    manager: SonyWindowsHeadsetManager,
    modifier: Modifier = Modifier,
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val state by manager.state.collectAsState()
    val themeMode by manager.themeMode.collectAsState()
    val isOled by manager.isOled.collectAsState()
    val accentIndex by manager.accentIndex.collectAsState()
    val language by manager.language.collectAsState()

    val accentColor = ACCENT_PALETTE.getOrNull(accentIndex) ?: Color(0xFF00E676)
    val strings = remember(language) { AppStrings.get(language) }

    // Determinación de colores del sistema de temas NuclearUI
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

    // Estados de las hojas modales
    var showThemeSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showCompatibleModelsSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, dividerColor, RoundedCornerShape(26.dp)),
        color = bgColor,
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==========================================
                // TOP ACTION BAR (Barra de ventana minimalista con controles y arrastre)
                // ==========================================
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Área de arrastre nativa de la ventana
                        WindowDraggableArea(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }

                        // Botones de acción integrados
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { manager.syncTelemetryAsync() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                if (state.isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = accentColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sincronizar",
                                        tint = textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            IconButton(
                                onClick = onMinimize,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Minimizar",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

            // ==========================================
            // 1. HERO SHOWCASE AL AIRE (FREE-FLOATING)
            // ==========================================
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

            // ==========================================
            // 2. ACCESOS RÁPIDOS DE CONTROL DE RUIDO (CARD 26.dp)
            // ==========================================
            if (state.modelFamily.hasAnc) {
                item {
                    QuickNoiseControlCard(
                        state = state,
                        cardColor = cardColor,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        strings = strings,
                        onModeSelected = { mode -> manager.setNoiseModeAsync(mode) },
                        onLevelChanged = { level -> manager.setAmbientLevel(level) },
                        onVoiceFocusChanged = { focus -> manager.setVoiceFocus(focus) }
                    )
                }
            }

            // ==========================================
            // 3. MENÚ DE AJUSTES AGRUPADO (CARD 26.dp)
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Fila 1: Historial de dispositivos
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

                        // Fila 3: Modelos compatibles
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // ==========================================
        // HOJAS MODALES (OVERLAYS NATIVOS NUCLEARUI)
        // ==========================================
        NuclearModalSheet(
            visible = showThemeSheet,
            onDismissRequest = { showThemeSheet = false },
            cardColor = cardColor
        ) {
            ThemeSettingsSheet(
                themeMode = themeMode,
                isOled = isOled,
                accentIndex = accentIndex,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardColor = cardColor,
                strings = strings,
                onThemeModeChanged = { manager.setThemeMode(it) },
                onOledChanged = { manager.setOled(it) },
                onAccentChanged = { manager.setAccentIndex(it) },
                onClose = { showThemeSheet = false }
            )
        }

        NuclearModalSheet(
            visible = showHistorySheet,
            onDismissRequest = { showHistorySheet = false },
            cardColor = cardColor
        ) {
            DeviceHistorySheet(
                manager = manager,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor,
                strings = strings,
                onClose = { showHistorySheet = false }
            )
        }

        NuclearModalSheet(
            visible = showCompatibleModelsSheet,
            onDismissRequest = { showCompatibleModelsSheet = false },
            cardColor = cardColor
        ) {
            CompatibleModelsSheet(
                state = state,
                cardColor = cardColor,
                accentColor = accentColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor,
                strings = strings,
                onSelectModel = { fam ->
                    manager.setUserForcedModel(state.deviceAddress, fam)
                    showCompatibleModelsSheet = false
                },
                onResetAutoDetect = {
                    manager.setUserForcedModel(state.deviceAddress, null)
                    showCompatibleModelsSheet = false
                },
                onClose = { showCompatibleModelsSheet = false }
            )
        }

        NuclearModalSheet(
            visible = showLanguageSheet,
            onDismissRequest = { showLanguageSheet = false },
            cardColor = cardColor
        ) {
            LanguageSheet(
                currentLanguage = language,
                onSelectLanguage = { manager.setLanguage(it) },
                onClose = { showLanguageSheet = false },
                strings = strings,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )
        }

        NuclearModalSheet(
            visible = showAboutSheet,
            onDismissRequest = { showAboutSheet = false },
            cardColor = cardColor
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
}

/**
 * Hero Superior Centrado: Ilustración a escala, nombre y estado de carga compacto.
 */
@Composable
fun HeroShowcase(
    state: HeadsetState,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    isDark: Boolean,
    strings: LocalizedStrings
) {
    val icons = remember(state.modelFamily) { SonyModelIcons.getIcons(state.modelFamily) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val connectedTint = if (isDark) Color.White else Color(0xFF1C1C1E)
        val disconnectedTint = if (isDark) Color(0xFF505054).copy(alpha = 0.45f) else Color(0xFF8E8E93).copy(alpha = 0.40f)

        if (state.deviceType == DeviceType.OVER_EAR) {
            val isHeadphoneConnected = state.isConnected && state.singleBattery.isAvailable
            Image(
                painter = painterResource(icons.headphone),
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
                    painter = painterResource(icons.leftBud),
                    contentDescription = "L",
                    modifier = Modifier.size(72.dp),
                    colorFilter = ColorFilter.tint(if (isLeftConnected) connectedTint else disconnectedTint)
                )

                Spacer(modifier = Modifier.width(18.dp))

                Image(
                    painter = painterResource(icons.case),
                    contentDescription = "Estuche",
                    modifier = Modifier.size(92.dp),
                    colorFilter = ColorFilter.tint(if (isCaseConnected) connectedTint else disconnectedTint)
                )

                Spacer(modifier = Modifier.width(18.dp))

                Image(
                    painter = painterResource(icons.rightBud),
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
 * Accesos rápidos con 3 botones circulares de control de ruido estilo wearable (52.dp).
 */
@Composable
fun QuickNoiseControlCard(
    state: HeadsetState,
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
                // Cancelación de Ruido (ANC)
                QuickActionButton(
                    iconRes = "drawable/ic_anc_on.xml",
                    label = strings.noiseCancelling,
                    isActive = state.noiseMode == NoiseControlMode.ANC,
                    accentColor = accentColor,
                    textSecondary = textSecondary,
                    onClick = { onModeSelected(NoiseControlMode.ANC) }
                )

                // Sonido Ambiente
                QuickActionButton(
                    iconRes = "drawable/ic_ambient_sound.xml",
                    label = strings.ambientSound,
                    isActive = state.noiseMode == NoiseControlMode.AMBIENT,
                    accentColor = accentColor,
                    textSecondary = textSecondary,
                    onClick = { onModeSelected(NoiseControlMode.AMBIENT) }
                )

                // Desactivado
                QuickActionButton(
                    iconRes = "drawable/ic_anc_off.xml",
                    label = strings.noiseOff,
                    isActive = state.noiseMode == NoiseControlMode.OFF,
                    accentColor = accentColor,
                    textSecondary = textSecondary,
                    onClick = { onModeSelected(NoiseControlMode.OFF) }
                )
            }

            // Desplegable animado suave si el modo activo es Ambiente
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

/**
 * Botón circular de acción rápida (52.dp) con animación suave de color.
 */
@Composable
fun QuickActionButton(
    iconRes: String,
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
 * Fila del menú agrupado con píldora circular de icono saturado (38.dp) y chevron.
 */
@Composable
fun MenuRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
