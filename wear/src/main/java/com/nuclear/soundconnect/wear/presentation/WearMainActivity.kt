package com.nuclear.soundconnect.wear.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.data.WearConnectionMode
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.components.BatteryArcsDisplay
import com.nuclear.soundconnect.wear.presentation.components.NoiseControlRow
import com.nuclear.soundconnect.wear.presentation.theme.NUCLEAR_WEAR_ACCENT_PALETTE
import com.nuclear.soundconnect.wear.presentation.theme.NuclearAmbientBlue
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBlack
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBorder
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSurfaceCard
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSurfaceElevated
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextPrimary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextSecondary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearWearTheme
import com.nuclear.soundconnect.wear.presentation.theme.WatchAccentConfig
import com.nuclear.soundconnect.wear.presentation.theme.WearUiPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WearMainActivity : ComponentActivity() {

    private lateinit var repository: WearHeadsetRepository
    private val focusAmbientTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WearHeadsetRepository.getInstance(applicationContext)

        handleIntentAction(intent)

        setContent {
            val state by repository.state.collectAsState()
            val mode by repository.connectionMode.collectAsState()
            val isBusy by repository.isBusy.collectAsState()
            val accentConfig by WearUiPrefs.observeConfig(applicationContext).collectAsState()
            val accentColor = Color(WearUiPrefs.resolveAccentColor(state.accentColor, accentConfig))

            NuclearWearTheme(accentColor = accentColor) {
                WearMainScreen(
                    state = state,
                    connectionMode = mode,
                    isBusy = isBusy,
                    accentColor = accentColor,
                    accentConfig = accentConfig,
                    focusAmbientTrigger = focusAmbientTrigger.value,
                    onNoiseModeSelect = { targetMode ->
                        repository.setNoiseMode(targetMode)
                        WearUiPrefs.requestAllTileUpdates(applicationContext)
                    },
                    onAmbientConfigChange = { level, voiceFocus ->
                        repository.setAmbientConfig(level, voiceFocus)
                        WearUiPrefs.requestAllTileUpdates(applicationContext)
                    },
                    onRefreshClick = {
                        repository.requestSync()
                        WearUiPrefs.requestAllTileUpdates(applicationContext)
                    },
                    onFollowPhoneAccent = {
                        WearUiPrefs.setFollowPhone(applicationContext)
                    },
                    onSelectCustomAccent = { argb ->
                        WearUiPrefs.setCustomAccent(applicationContext, argb)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_SET_ANC -> {
                repository.setNoiseMode(NoiseControlMode.ANC)
                WearUiPrefs.requestAllTileUpdates(applicationContext)
            }
            ACTION_SET_AMBIENT -> {
                repository.setNoiseMode(NoiseControlMode.AMBIENT)
                focusAmbientTrigger.value += 1
                WearUiPrefs.requestAllTileUpdates(applicationContext)
            }
            ACTION_SET_OFF -> {
                repository.setNoiseMode(NoiseControlMode.OFF)
                WearUiPrefs.requestAllTileUpdates(applicationContext)
            }
            ACTION_OPEN_AMBIENT -> {
                focusAmbientTrigger.value += 1
            }
            else -> {
                if (intent?.getBooleanExtra(EXTRA_OPEN_AMBIENT, false) == true) {
                    focusAmbientTrigger.value += 1
                }
            }
        }
    }

    companion object {
        const val ACTION_SET_ANC = "com.nuclear.soundconnect.ACTION_SET_ANC"
        const val ACTION_SET_AMBIENT = "com.nuclear.soundconnect.ACTION_SET_AMBIENT"
        const val ACTION_SET_OFF = "com.nuclear.soundconnect.ACTION_SET_OFF"
        const val ACTION_OPEN_AMBIENT = "com.nuclear.soundconnect.ACTION_OPEN_AMBIENT"
        const val EXTRA_OPEN_AMBIENT = "open_ambient"
    }
}

@Composable
fun WearMainScreen(
    state: HeadsetState,
    connectionMode: WearConnectionMode,
    isBusy: Boolean,
    accentColor: Color,
    accentConfig: WatchAccentConfig,
    focusAmbientTrigger: Int,
    onNoiseModeSelect: (NoiseControlMode) -> Unit,
    onAmbientConfigChange: (level: Int, voiceFocus: Boolean) -> Unit,
    onRefreshClick: () -> Unit,
    onFollowPhoneAccent: () -> Unit,
    onSelectCustomAccent: (Int) -> Unit
) {
    var showAccentPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = showAccentPicker) {
        showAccentPicker = false
    }

    if (showAccentPicker) {
        WatchAccentPickerScreen(
            phoneAccentArgb = state.accentColor,
            currentConfig = accentConfig,
            activeAccent = accentColor,
            onFollowPhone = onFollowPhoneAccent,
            onSelectColor = onSelectCustomAccent,
            onDismiss = { showAccentPicker = false }
        )
        return
    }

    val listState = rememberScalingLazyListState()
    LaunchedEffect(focusAmbientTrigger) {
        if (focusAmbientTrigger > 0 && state.effectiveHasAnc) {
            listState.animateScrollToItem(2)
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuclearBlack)
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Cabecera: Nombre del audífono, punto de conexión y refresco
                item {
                    DeviceHeader(
                        deviceName = state.deviceName,
                        isConnected = state.isConnected,
                        connectionMode = connectionMode,
                        accentColor = accentColor,
                        isBusy = isBusy,
                        onRefresh = onRefreshClick
                    )
                }

                // 2. Anillos Circulares de Batería y siluetas con atenuación gris al desconectar
                item {
                    BatteryArcsDisplay(
                        state = state,
                        accentColor = accentColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // 3. Píldora discreta del códec activo (solo cuando conectado y no vacío)
                if (state.isConnected && state.codecName.isNotBlank()) {
                    item {
                        CodecBadge(codecName = state.codecName)
                    }
                }

                // 4. Fila de Control de Ruido (ANC / Ambiente / Off) con Stepper de Nivel
                if (state.effectiveHasAnc) {
                    item {
                        NoiseControlRow(
                            currentMode = state.noiseMode,
                            ambientLevel = state.ambientLevel,
                            voiceFocus = state.voiceFocus,
                            accentColor = accentColor,
                            isBusy = isBusy,
                            isConnected = state.isConnected,
                            onModeSelect = onNoiseModeSelect,
                            onAmbientConfigChange = onAmbientConfigChange,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    }
                }

                // 5. Selector independiente de color de acento del reloj
                item {
                    WatchThemeSelectorButton(
                        accentColor = accentColor,
                        usePhoneSync = accentConfig.usePhoneSync,
                        onClick = { showAccentPicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                // 6. Pie de página discreto: última sincronización
                item {
                    val syncText = if (state.lastSyncTime > 0) {
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        "Sync: ${sdf.format(Date(state.lastSyncTime))}"
                    } else {
                        "Sin sincronizar"
                    }

                    Text(
                        text = syncText,
                        color = NuclearTextSecondary,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(
    deviceName: String,
    isConnected: Boolean,
    connectionMode: WearConnectionMode,
    accentColor: Color,
    isBusy: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (isConnected) {
                        R.drawable.ic_status_dot_connected
                    } else {
                        R.drawable.ic_status_dot_disconnected
                    }
                ),
                contentDescription = null,
                tint = if (isConnected) accentColor else NuclearTextSecondary,
                modifier = Modifier.size(7.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = deviceName,
                color = if (isConnected) NuclearTextPrimary else NuclearTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(NuclearSurfaceCard)
                    .border(1.dp, NuclearBorder, CircleShape)
                    .clickable(enabled = !isBusy, onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        indicatorColor = accentColor
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Refrescar",
                        tint = NuclearTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Mostrar canal solo cuando hay conexión activa (en gris/sin etiqueta cuando no está conectado)
        if (isConnected && connectionMode != WearConnectionMode.DISCONNECTED) {
            Spacer(modifier = Modifier.height(3.dp))
            val (modeText, modeColor) = when (connectionMode) {
                WearConnectionMode.STANDALONE -> "En Reloj (BT)" to accentColor
                WearConnectionMode.COMPANION -> "Vía Teléfono" to NuclearAmbientBlue
                WearConnectionMode.DISCONNECTED -> return@Column
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(modeColor.copy(alpha = 0.14f))
                    .border(1.dp, modeColor.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = modeText,
                    color = modeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CodecBadge(codecName: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(NuclearSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = codecName,
            color = NuclearTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WatchThemeSelectorButton(
    accentColor: Color,
    usePhoneSync: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NuclearSurfaceCard)
            .border(1.dp, NuclearBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_palette),
                contentDescription = "Color del reloj",
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Color del reloj",
                    color = NuclearTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (usePhoneSync) "Según celular" else "Independiente",
                    color = NuclearTextSecondary,
                    fontSize = 9.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
    }
}

@Composable
private fun WatchAccentPickerScreen(
    phoneAccentArgb: Int,
    currentConfig: WatchAccentConfig,
    activeAccent: Color,
    onFollowPhone: () -> Unit,
    onSelectColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerListState = rememberScalingLazyListState()
    val phoneColor = Color(phoneAccentArgb)
    val paletteRows = NUCLEAR_WEAR_ACCENT_PALETTE.chunked(4)

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = pickerListState) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuclearBlack)
        ) {
            ScalingLazyColumn(
                state = pickerListState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Color del reloj",
                        color = NuclearTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Opción de seguir el acento del móvil
                item {
                    val isSelected = currentConfig.usePhoneSync
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) phoneColor.copy(alpha = 0.16f) else NuclearSurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) phoneColor else NuclearBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onFollowPhone() }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(phoneColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Seguir al celular",
                                color = if (isSelected) phoneColor else NuclearTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Activo",
                                tint = phoneColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Cuadrícula de los 12 colores de NuclearUI
                items(paletteRows.size) { rowIndex ->
                    val rowColors = paletteRows[rowIndex]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        rowColors.forEach { argb ->
                            val swatchColor = Color(argb)
                            val isSelected = !currentConfig.usePhoneSync && currentConfig.customAccentArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectColor(argb) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    val checkTint = if (swatchColor.luminance() > 0.45f) Color.Black else Color.White
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check),
                                        contentDescription = "Seleccionado",
                                        tint = checkTint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Botón para volver a la vista principal
                item {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(activeAccent)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val textColor = if (activeAccent.luminance() > 0.55f) Color.Black else Color.White
                        Text(
                            text = "Listo",
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
