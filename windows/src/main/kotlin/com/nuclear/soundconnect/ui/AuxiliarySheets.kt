package com.nuclear.soundconnect.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclear.soundconnect.bluetooth.*
import java.text.SimpleDateFormat
import java.util.*

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

/**
 * Contenedor Modal estilo One UI / NuclearUI para Desktop con animación y scrim translúcido.
 */
@Composable
fun NuclearModalSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    cardColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismissRequest)
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .clickable(enabled = false) {}, // Evita cerrar al hacer click dentro
                color = cardColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Tirador superior (drag handle)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color(0x338E8E93))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    content()
                }
            }
        }
    }
}

/**
 * Hoja de Ajustes de Tema y Colores (1:1 con Android).
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
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.themeColorsTitle,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = oledTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    Text(text = oledSub, fontSize = 11.5.sp, color = textSecondary)
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
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Selector de Colores de Acento (12 Colores Oficiales NuclearUI)
        Text(
            text = strings.accentColorsSection,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Cuadrícula 2 filas de 6 esferas minimalistas
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
    }
}

/**
 * Hoja de Catálogo de Modelos Compatibles (16 Familias Industriales).
 */
@Composable
fun CompatibleModelsSheet(
    state: HeadsetState,
    cardColor: Color,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color,
    strings: LocalizedStrings,
    onSelectModel: (SonyModelFamily) -> Unit,
    onResetAutoDetect: () -> Unit,
    onClose: () -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todos, 1: TWS, 2: Diadema
    var searchQuery by remember { mutableStateOf("") }

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
    val activeIcons = remember(state.modelFamily) { SonyModelIcons.getIcons(state.modelFamily) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabecera
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Banner del modelo activo
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
                                    painter = painterResource(activeIcons.leftBud),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                                Image(
                                    painter = painterResource(activeIcons.case),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                                Image(
                                    painter = painterResource(activeIcons.rightBud),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                            }
                        } else {
                            Image(
                                painter = painterResource(activeIcons.headphone),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                colorFilter = ColorFilter.tint(textPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.modelFamily.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
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
                                    color = if (isForced) accentColor else textSecondary
                                )
                            }
                        }
                        Text(
                            text = if (isForced) strings.designLockedByYou else strings.detectedByHardware,
                            fontSize = 11.sp,
                            color = textSecondary
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = strings.resetAutoDetectBtn,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pestañas de filtrado (Todos | True Wireless | Diadema)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(strings.allTab, strings.twsTab, strings.overEarTab).forEachIndexed { index, label ->
                val isSelected = selectedFilter == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) accentColor else textSecondary.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .clickable { selectedFilter = index }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lista de modelos
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { fam ->
                val isCurrent = state.modelFamily == fam
                val icons = SonyModelIcons.getIcons(fam)

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
                                        painter = painterResource(icons.leftBud),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        colorFilter = ColorFilter.tint(textPrimary)
                                    )
                                    Image(
                                        painter = painterResource(icons.case),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        colorFilter = ColorFilter.tint(textPrimary)
                                    )
                                    Image(
                                        painter = painterResource(icons.rightBud),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        colorFilter = ColorFilter.tint(textPrimary)
                                    )
                                }
                            } else {
                                Image(
                                    painter = painterResource(icons.headphone),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    colorFilter = ColorFilter.tint(textPrimary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fam.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = fam.seriesCategory,
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }

                        if (isCurrent) {
                            Text(
                                text = strings.activeModelBadge,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
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
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hoja de Historial de Dispositivos y Dispositivos Emparejados Sony en Windows.
 */
@Composable
fun DeviceHistorySheet(
    manager: SonyWindowsHeadsetManager,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings,
    onClose: () -> Unit
) {
    val history by manager.history.collectAsState()
    val pairedDevices by manager.pairedDevices.collectAsState()
    val currentState by manager.state.collectAsState()

    val sonyPaired = remember(pairedDevices) {
        pairedDevices.filter { it.isSony }
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.deviceHistorySheetTitle,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = strings.deviceHistorySheetSubtitle,
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sección: Dispositivos Vinculados en Windows
        Text(
            text = strings.pairedDevicesSection,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (sonyPaired.isEmpty()) {
            Text(
                text = strings.deviceHistoryEmpty,
                fontSize = 12.sp,
                color = textSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            sonyPaired.forEach { dev ->
                val isSelected = dev.macAddress.equals(currentState.deviceAddress, ignoreCase = true)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) accentColor.copy(alpha = 0.12f) else textSecondary.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { manager.selectDevice(dev) }
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = dev.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "${dev.macAddress}  •  ${if (dev.isConnected) strings.connected else strings.disconnected}",
                                fontSize = 11.sp,
                                color = if (dev.isConnected) Color(0xFF34C759) else textSecondary
                            )
                        }

                        if (isSelected) {
                            Button(
                                onClick = { manager.syncTelemetryAsync() },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(strings.refreshButton, fontSize = 11.sp, color = Color.Black)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { manager.selectDevice(dev) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(strings.connectButton, fontSize = 11.sp, color = textPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Sección: Historial de Baterías Vistas
        if (history.isNotEmpty()) {
            Text(
                text = strings.deviceHistoryTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            history.forEach { record ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = textSecondary.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = record.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = dateFormat.format(Date(record.lastSeenTimestamp)),
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (record.deviceType == DeviceType.OVER_EAR) {
                            Text(
                                text = "${strings.headphoneLabel}: ${if (record.singleLevel > 0) "${record.singleLevel}%" else "--%"}",
                                fontSize = 12.sp,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "${strings.leftBud}: ${if (record.leftLevel > 0) "${record.leftLevel}%" else "--"}", fontSize = 12.sp, color = accentColor)
                                Text(text = "${strings.rightBud}: ${if (record.rightLevel > 0) "${record.rightLevel}%" else "--"}", fontSize = 12.sp, color = accentColor)
                                Text(text = "${strings.caseLabel}: ${if (record.caseLevel > 0) "${record.caseLevel}%" else "--"}", fontSize = 12.sp, color = accentColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hoja de Idioma / Language.
 */
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
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.languageSheetTitle,
                    fontSize = 19.sp,
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

        Spacer(modifier = Modifier.height(16.dp))

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
    }
}

/**
 * Hoja de Información / Acerca de.
 */
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
            .padding(vertical = 14.dp),
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

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = strings.aboutDescription,
            fontSize = 12.5.sp,
            color = textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Text(strings.understoodBtn, color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
    }
}
