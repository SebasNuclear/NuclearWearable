package com.nuclear.soundconnect.wear.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.data.WearConnectionMode
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.components.BatteryArcsDisplay
import com.nuclear.soundconnect.wear.presentation.components.NoiseControlRow
import com.nuclear.soundconnect.wear.presentation.theme.NuclearAmbientBlue
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBlack
import com.nuclear.soundconnect.wear.presentation.theme.NuclearBorder
import com.nuclear.soundconnect.wear.presentation.theme.NuclearSurfaceCard
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextPrimary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearTextSecondary
import com.nuclear.soundconnect.wear.presentation.theme.NuclearWearTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WearMainActivity : ComponentActivity() {

    private lateinit var repository: WearHeadsetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WearHeadsetRepository.getInstance(applicationContext)

        handleIntentAction(intent)

        setContent {
            val state by repository.state.collectAsState()
            val mode by repository.connectionMode.collectAsState()
            val isBusy by repository.isBusy.collectAsState()
            val accentColor = Color(state.accentColor)

            NuclearWearTheme(accentColor = accentColor) {
                WearMainScreen(
                    state = state,
                    connectionMode = mode,
                    isBusy = isBusy,
                    accentColor = accentColor,
                    onNoiseModeSelect = { targetMode ->
                        repository.setNoiseMode(targetMode)
                    },
                    onAmbientConfigChange = { level, voiceFocus ->
                        repository.setAmbientConfig(level, voiceFocus)
                    },
                    onRefreshClick = {
                        repository.requestSync()
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
            ACTION_SET_ANC -> repository.setNoiseMode(NoiseControlMode.ANC)
            ACTION_SET_AMBIENT -> repository.setNoiseMode(NoiseControlMode.AMBIENT)
            ACTION_SET_OFF -> repository.setNoiseMode(NoiseControlMode.OFF)
        }
    }

    companion object {
        const val ACTION_SET_ANC = "com.nuclear.soundconnect.ACTION_SET_ANC"
        const val ACTION_SET_AMBIENT = "com.nuclear.soundconnect.ACTION_SET_AMBIENT"
        const val ACTION_SET_OFF = "com.nuclear.soundconnect.ACTION_SET_OFF"
    }
}

@Composable
fun WearMainScreen(
    state: com.nuclear.soundconnect.bluetooth.HeadsetState,
    connectionMode: WearConnectionMode,
    isBusy: Boolean,
    accentColor: Color,
    onNoiseModeSelect: (NoiseControlMode) -> Unit,
    onAmbientConfigChange: (level: Int, voiceFocus: Boolean) -> Unit,
    onRefreshClick: () -> Unit
) {
    val listState = rememberScalingLazyListState()

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
                // 1. Cabecera: Nombre de Audífono y Badge de Conexión
                item {
                    DeviceHeader(
                        deviceName = state.deviceName,
                        connectionMode = connectionMode,
                        accentColor = accentColor,
                        isBusy = isBusy,
                        onRefresh = onRefreshClick
                    )
                }

                // 2. Anillos Circulares de Batería (L / R con iconos vectoriales y Estuche)
                item {
                    BatteryArcsDisplay(
                        state = state,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // 3. Fila de Control de Ruido (ANC / Ambiente / Off) con Stepper de Nivel si aplica
                if (state.modelFamily.hasAnc) {
                    item {
                        NoiseControlRow(
                            currentMode = state.noiseMode,
                            ambientLevel = state.ambientLevel,
                            voiceFocus = state.voiceFocus,
                            accentColor = accentColor,
                            isBusy = isBusy,
                            onModeSelect = onNoiseModeSelect,
                            onAmbientConfigChange = onAmbientConfigChange,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                    }
                }

                // 4. Pie de página: Última sincronización
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
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(
    deviceName: String,
    connectionMode: WearConnectionMode,
    accentColor: Color,
    isBusy: Boolean,
    onRefresh: () -> Unit
) {
    val (modeText, modeColor) = when (connectionMode) {
        WearConnectionMode.STANDALONE -> "En Reloj (BT)" to accentColor
        WearConnectionMode.COMPANION -> "Vía Teléfono" to NuclearAmbientBlue
        WearConnectionMode.DISCONNECTED -> "Desconectado" to Color(0xFFFF5252)
    }

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
            Text(
                text = deviceName,
                color = NuclearTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Botón de sincronización con indicador de carga
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

        Spacer(modifier = Modifier.height(2.dp))

        // Badge de canal de conexión
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(modeColor.copy(alpha = 0.15f))
                .border(1.dp, modeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
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
