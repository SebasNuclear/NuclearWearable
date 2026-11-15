package com.nuclear.soundconnect.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclear.soundconnect.audio.EarSide
import com.nuclear.soundconnect.audio.LiveCodecHudInfo
import com.nuclear.soundconnect.backup.ProfileBackupManager
import com.nuclear.soundconnect.bluetooth.AutoPowerOffOption
import com.nuclear.soundconnect.bluetooth.ConnectionPriority
import com.nuclear.soundconnect.bluetooth.EqPreset
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.HexLogEntry
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyWindowsHeadsetManager
import com.nuclear.soundconnect.bluetooth.SpeakToChatReturnTime
import com.nuclear.soundconnect.bluetooth.SpeakToChatSensitivity
import com.nuclear.soundconnect.bluetooth.TouchPresetOption
import com.nuclear.soundconnect.bluetooth.VoiceGuidanceLanguageOption
import com.nuclear.soundconnect.util.WindowsFileDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val CLASSIC_EQ_LABELS = listOf("400", "1k", "2.5k", "6.3k", "16k")
private val XM6_EQ_LABELS = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@Composable
private fun SheetHeaderRow(
    title: String,
    subtitle: String? = null,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isBusy: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    refreshDesc: String = "",
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = textSecondary, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRefresh != null) {
                IconButton(onClick = onRefresh, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = refreshDesc,
                        tint = accentColor
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = textSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = textSecondary, fontSize = 11.5.sp, lineHeight = 15.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun SectionCard(
    textPrimary: Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = textPrimary.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun LiveCodecDetailsCard(
    hud: LiveCodecHudInfo,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings
) {
    SectionCard(textPrimary = textPrimary) {
        Text(
            text = strings.codecLiveSectionTitle,
            color = textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CodecStatPill(
                label = strings.codecActiveLabel,
                value = when {
                    hud.isLeAudioActive -> strings.leAudioTag
                    hud.codecName.isNotBlank() -> hud.codecName
                    else -> "--"
                },
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                modifier = Modifier.weight(1f)
            )
            CodecStatPill(
                label = strings.codecSampleRateLabel,
                value = if (hud.sampleRateHz > 0) {
                    if (hud.sampleRateHz % 1000 == 0) "${hud.sampleRateHz / 1000} kHz"
                    else String.format(Locale.US, "%.1f kHz", hud.sampleRateHz / 1000.0)
                } else "--",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                modifier = Modifier.weight(1f)
            )
            CodecStatPill(
                label = strings.codecBitDepthLabel,
                value = if (hud.bitsPerSample > 0) "${hud.bitsPerSample}-bit" else "--",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                modifier = Modifier.weight(1f)
            )
            CodecStatPill(
                label = strings.codecChannelsLabel,
                value = "${hud.channelCount}ch",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                modifier = Modifier.weight(0.75f)
            )
        }

        if (hud.isHfpCallActive) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x33FF9500),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.hfpCallWarning,
                            color = Color(0xFFFFB703),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { WindowsFileDialogs.openBluetoothSettings() }) {
                        Text(strings.openBtSettingsBtn, color = accentColor, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodecStatPill(
    label: String,
    value: String,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, color = textSecondary, fontSize = 11.sp)
        Text(text = value, color = textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioEqSheet(
    state: HeadsetState,
    hud: LiveCodecHudInfo,
    isEnglish: Boolean,
    manager: SonyWindowsHeadsetManager,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings,
    onClose: () -> Unit
) {
    val caps = state.capabilities
    val eq = state.eqState
    val scroll = rememberScrollState()

    val isTenBand = eq.isTenBand || caps.tenBandEq
    val expectedBands = if (isTenBand) 10 else 5
    var draftClearBass by remember { mutableIntStateOf(eq.clearBass) }
    val draftBands = remember(isTenBand) {
        mutableStateListOf<Int>().apply {
            val fill = if (isTenBand) 6 else 0
            repeat(expectedBands) { add(fill) }
        }
    }

    LaunchedEffect(eq.presetId, eq.clearBass, eq.bands, isTenBand) {
        draftClearBass = eq.clearBass.coerceIn(-10, 10)
        draftBands.clear()
        val fallbackVal = if (isTenBand) 6 else 0
        for (i in 0 until expectedBands) {
            draftBands.add(eq.bands.getOrNull(i) ?: fallbackVal)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetHeaderRow(
            title = strings.eqSheetTitle,
            subtitle = state.hardwareModelName.ifBlank { state.deviceName },
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            isBusy = state.isBusy,
            onRefresh = {
                manager.refreshCodecHud()
                manager.syncExtendedControlsAsync()
            },
            refreshDesc = strings.refreshFromChipBtn,
            onClose = onClose
        )

        LiveCodecDetailsCard(
            hud = hud,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentColor = accentColor,
            strings = strings
        )

        if (caps.supportsEq) {
            val quickPresets = remember(state.protocolVersion) {
                listOf(
                    EqPreset.OFF,
                    EqPreset.BRIGHT,
                    EqPreset.EXCITED,
                    EqPreset.MELLOW,
                    EqPreset.RELAXED,
                    EqPreset.VOCAL,
                    EqPreset.TREBLE,
                    EqPreset.BASS,
                    EqPreset.SPEECH,
                    if (state.protocolVersion < 2) EqPreset.UNSPECIFIED else EqPreset.CUSTOM
                )
            }

            SectionCard(textPrimary = textPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.eqSectionTitle,
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = eq.activePreset?.label(isEnglish) ?: "0x%02X".format(eq.presetId),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (!eq.isLoaded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = strings.eqNotLoadedHint, color = textSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (preset in quickPresets) {
                        val selected = eq.presetId == preset.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (preset == EqPreset.CUSTOM || preset == EqPreset.UNSPECIFIED) {
                                    manager.setEqualizerCustomAsync(draftClearBass, draftBands.toList())
                                } else {
                                    manager.setEqualizerPresetAsync(preset.id)
                                }
                            },
                            label = { Text(preset.label(isEnglish), fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                selectedLabelColor = accentColor,
                                labelColor = textPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (!isTenBand) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.eqClearBassLabel, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (draftClearBass > 0) "+$draftClearBass" else "$draftClearBass",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = draftClearBass.toFloat(),
                        onValueChange = { draftClearBass = it.roundToInt().coerceIn(-10, 10) },
                        valueRange = -10f..10f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor
                        )
                    )
                }

                Text(
                    text = strings.eqCustomBandsLabel,
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))

                val bandLabels = if (isTenBand) XM6_EQ_LABELS else CLASSIC_EQ_LABELS
                for (i in 0 until draftBands.size) {
                    val value = draftBands[i]
                    val displayDelta = if (isTenBand) value - 6 else value
                    val bandLabel = bandLabels.getOrElse(i) { "B${i + 1}" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bandLabel,
                            color = textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.width(38.dp)
                        )
                        Slider(
                            value = value.toFloat(),
                            onValueChange = {
                                draftBands[i] = if (isTenBand) {
                                    it.roundToInt().coerceIn(0, 12)
                                } else {
                                    it.roundToInt().coerceIn(-10, 10)
                                }
                            },
                            valueRange = if (isTenBand) 0f..12f else -10f..10f,
                            steps = if (isTenBand) 11 else 19,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor
                            )
                        )
                        Text(
                            text = if (displayDelta > 0) "+$displayDelta" else "$displayDelta",
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { manager.setEqualizerCustomAsync(draftClearBass, draftBands.toList()) },
                    enabled = state.isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.eqApplyCustomBtn, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (caps.supportsDsee) {
            SectionCard(textPrimary = textPrimary) {
                SettingSwitchRow(
                    title = strings.dseeTitle,
                    subtitle = strings.dseeDesc,
                    checked = state.dseeEnabled == true,
                    enabled = state.isConnected,
                    accentColor = accentColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onCheckedChange = { manager.setDseeEnabledAsync(it) }
                )
            }
        }

        if (caps.supportsConnectionPriority) {
            SectionCard(textPrimary = textPrimary) {
                Text(
                    text = strings.connPriorityTitle,
                    color = textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = buildList {
                        add(ConnectionPriority.SOUND_QUALITY to strings.prioritySoundQuality)
                        add(ConnectionPriority.STABLE_CONNECTION to strings.priorityStableConn)
                        if (caps.connectionPriorityInquiredType == 0x05) {
                            add(ConnectionPriority.LOW_LATENCY to strings.priorityLowLatency)
                        }
                    }
                    for ((prio, label) in options) {
                        val selected = state.connectionPriority == prio
                        FilterChip(
                            selected = selected,
                            enabled = state.isConnected,
                            onClick = { manager.setConnectionPriorityAsync(prio) },
                            label = { Text(label, fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                selectedLabelColor = accentColor,
                                labelColor = textPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComfortControlsSheet(
    state: HeadsetState,
    isEnglish: Boolean,
    manager: SonyWindowsHeadsetManager,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings,
    onClose: () -> Unit
) {
    val caps = state.capabilities
    val findState by manager.findBudsState.collectAsState()
    var showPowerOffDialog by remember { mutableStateOf(false) }

    // Garantiza detener la baliza sonora al cerrar la hoja para proteger el oído
    DisposableEffect(Unit) {
        onDispose {
            manager.stopFindMyBuds()
        }
    }

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetHeaderRow(
            title = strings.comfortSheetTitle,
            subtitle = state.hardwareModelName.ifBlank { state.deviceName },
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            isBusy = state.isBusy,
            onRefresh = { manager.syncExtendedControlsAsync() },
            refreshDesc = strings.refreshFromChipBtn,
            onClose = onClose
        )

        if (caps.supportsSpeakToChat) {
            SectionCard(textPrimary = textPrimary) {
                SettingSwitchRow(
                    title = strings.speakToChatTitle,
                    subtitle = strings.speakToChatDesc,
                    checked = state.speakToChatEnabled == true,
                    enabled = state.isConnected,
                    accentColor = accentColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onCheckedChange = { manager.setSpeakToChatEnabledAsync(it) }
                )

                if (state.protocolVersion >= 2 && state.speakToChatEnabled == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(strings.stcSensitivityLabel, color = textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SpeakToChatSensitivity.AUTO to strings.stcSensAuto,
                            SpeakToChatSensitivity.HIGH to strings.stcSensHigh,
                            SpeakToChatSensitivity.LOW to strings.stcSensLow
                        ).forEach { (sens, label) ->
                            FilterChip(
                                selected = state.speakToChatSensitivity == sens,
                                enabled = state.isConnected,
                                onClick = {
                                    manager.setSpeakToChatConfigAsync(
                                        sens,
                                        state.speakToChatReturnTime,
                                        state.speakToChatFocusOnVoice
                                    )
                                },
                                label = { Text(label, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                    selectedLabelColor = accentColor,
                                    labelColor = textPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(strings.stcReturnTimeLabel, color = textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SpeakToChatReturnTime.FAST to strings.stcRetFast,
                            SpeakToChatReturnTime.MID to strings.stcRetMid,
                            SpeakToChatReturnTime.SLOW to strings.stcRetSlow,
                            SpeakToChatReturnTime.NONE to strings.stcRetNone
                        ).forEach { (rt, label) ->
                            FilterChip(
                                selected = state.speakToChatReturnTime == rt,
                                enabled = state.isConnected,
                                onClick = {
                                    manager.setSpeakToChatConfigAsync(
                                        state.speakToChatSensitivity,
                                        rt,
                                        state.speakToChatFocusOnVoice
                                    )
                                },
                                label = { Text(label, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                    selectedLabelColor = accentColor,
                                    labelColor = textPrimary
                                )
                            )
                        }
                    }

                    if (caps.speakToChatExtMode == 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingSwitchRow(
                            title = strings.stcFocusOnVoiceTitle,
                            checked = state.speakToChatFocusOnVoice,
                            enabled = state.isConnected,
                            accentColor = accentColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCheckedChange = {
                                manager.setSpeakToChatConfigAsync(
                                    state.speakToChatSensitivity,
                                    state.speakToChatReturnTime,
                                    it
                                )
                            }
                        )
                    }
                }
            }
        }

        if (caps.supportsPauseWhenRemoved || caps.supportsWindNoiseReduction) {
            SectionCard(textPrimary = textPrimary) {
                if (caps.supportsPauseWhenRemoved) {
                    SettingSwitchRow(
                        title = strings.pauseWhenRemovedTitle,
                        subtitle = strings.pauseWhenRemovedDesc,
                        checked = state.pauseWhenRemoved == true,
                        enabled = state.isConnected,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onCheckedChange = { manager.setPauseWhenRemovedAsync(it) }
                    )
                }
                if (caps.supportsWindNoiseReduction) {
                    if (caps.supportsPauseWhenRemoved) {
                        HorizontalDivider(color = textPrimary.copy(alpha = 0.08f))
                    }
                    SettingSwitchRow(
                        title = strings.windNoiseReductionTitle,
                        subtitle = strings.windNoiseReductionDesc,
                        checked = state.windNoiseReduction,
                        enabled = state.isConnected,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onCheckedChange = { manager.setWindNoiseReductionAsync(it) }
                    )
                }
            }
        }

        if (caps.supportsAutoPowerOff) {
            SectionCard(textPrimary = textPrimary) {
                Text(strings.autoPowerOffTitle, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Spacer(modifier = Modifier.height(8.dp))
                val choices = buildList {
                    add(AutoPowerOffOption.MIN_5 to strings.autoOff5Min)
                    add(AutoPowerOffOption.MIN_15 to strings.autoOff15Min)
                    add(AutoPowerOffOption.MIN_30 to strings.autoOff30Min)
                    add(AutoPowerOffOption.MIN_60 to strings.autoOff60Min)
                    add(AutoPowerOffOption.MIN_180 to strings.autoOff180Min)
                    if (caps.supportsAutoPowerOffWear) {
                        add(AutoPowerOffOption.WHEN_REMOVED to strings.autoOffRemoved)
                    }
                    add(AutoPowerOffOption.DISABLE to strings.autoOffNever)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for ((opt, label) in choices) {
                        FilterChip(
                            selected = state.autoPowerOff == opt,
                            enabled = state.isConnected,
                            onClick = { manager.setAutoPowerOffAsync(opt) },
                            label = { Text(label, fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                selectedLabelColor = accentColor,
                                labelColor = textPrimary
                            )
                        )
                    }
                }
            }
        }

        if (caps.supportsVoiceGuidance) {
            var langExpanded by remember { mutableStateOf(false) }
            SectionCard(textPrimary = textPrimary) {
                SettingSwitchRow(
                    title = strings.voiceGuidanceTitle,
                    checked = state.voiceGuidanceEnabled == true,
                    enabled = state.isConnected,
                    accentColor = accentColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onCheckedChange = { enabled ->
                        manager.setVoiceGuidanceAsync(enabled, state.voiceGuidanceLanguage)
                    }
                )
                if (caps.supportsVoiceGuidanceLanguage) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.voiceGuidanceLangLabel, color = textSecondary, fontSize = 12.5.sp)
                        Box {
                            OutlinedButton(
                                onClick = { langExpanded = true },
                                enabled = state.isConnected
                            ) {
                                Text(state.voiceGuidanceLanguage.label, color = textPrimary, fontSize = 11.5.sp)
                            }
                            DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                                VoiceGuidanceLanguageOption.entries
                                    .filter { it != VoiceGuidanceLanguageOption.UNDEFINED }
                                    .forEach { langOpt ->
                                        DropdownMenuItem(
                                            text = { Text(langOpt.label) },
                                            onClick = {
                                                langExpanded = false
                                                manager.setVoiceGuidanceAsync(
                                                    state.voiceGuidanceEnabled ?: true,
                                                    langOpt
                                                )
                                            }
                                        )
                                    }
                            }
                        }
                    }
                }
                if (caps.supportsVoiceGuidanceVolume) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.voiceGuidanceVolLabel, color = textSecondary, fontSize = 12.5.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (v in -2..2) {
                                FilterChip(
                                    selected = state.voiceGuidanceVolume == v,
                                    enabled = state.isConnected,
                                    onClick = { manager.setVoiceGuidanceVolumeAsync(v) },
                                    label = { Text(if (v > 0) "+$v" else "$v", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                        selectedLabelColor = accentColor,
                                        labelColor = textPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (caps.supportsTouchAssignment || caps.supportsTouchPanelToggle) {
            var leftExpanded by remember { mutableStateOf(false) }
            var rightExpanded by remember { mutableStateOf(false) }
            SectionCard(textPrimary = textPrimary) {
                Text(strings.touchControlsSectionTitle, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                if (caps.supportsTouchPanelToggle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingSwitchRow(
                        title = strings.touchPanelEnabledTitle,
                        subtitle = strings.touchPanelEnabledDesc,
                        checked = state.touchPanelEnabled == true,
                        enabled = state.isConnected,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onCheckedChange = { manager.setTouchPanelEnabledAsync(it) }
                    )
                }
                if (caps.supportsTouchAssignment) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val curLeft = state.touchLeftPreset ?: TouchPresetOption.AMBIENT_SOUND_CONTROL
                    val curRight = state.touchRightPreset ?: TouchPresetOption.PLAYBACK_CONTROL

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.touchLeftLabel, color = textSecondary, fontSize = 12.5.sp)
                        Box {
                            OutlinedButton(onClick = { leftExpanded = true }, enabled = state.isConnected) {
                                Text(curLeft.label(isEnglish), color = textPrimary, fontSize = 11.5.sp)
                            }
                            DropdownMenu(expanded = leftExpanded, onDismissRequest = { leftExpanded = false }) {
                                TouchPresetOption.entries.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.label(isEnglish)) },
                                        onClick = {
                                            leftExpanded = false
                                            manager.setTouchAssignmentAsync(preset, curRight)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.touchRightLabel, color = textSecondary, fontSize = 12.5.sp)
                        Box {
                            OutlinedButton(onClick = { rightExpanded = true }, enabled = state.isConnected) {
                                Text(curRight.label(isEnglish), color = textPrimary, fontSize = 11.5.sp)
                            }
                            DropdownMenu(expanded = rightExpanded, onDismissRequest = { rightExpanded = false }) {
                                TouchPresetOption.entries.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.label(isEnglish)) },
                                        onClick = {
                                            rightExpanded = false
                                            manager.setTouchAssignmentAsync(curLeft, preset)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Find My Buds (Tono estéreo local L / R)
        SectionCard(textPrimary = textPrimary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.findBudsSectionTitle, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0x28FF9500),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.findBudsWarning,
                    color = Color(0xFFFFB703),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (findState.isPlaying) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.findBudsPlayingLabel} (${findState.activeSide?.name ?: ""}): ${findState.elapsedSeconds}s / ${findState.totalSeconds}s",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { manager.stopFindMyBuds() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.findBudsStopBtn, color = Color.White, fontSize = 12.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { manager.startFindMyBuds(EarSide.LEFT) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                    ) {
                        Text(strings.findBudsPlayLeft, color = textPrimary, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = { manager.startFindMyBuds(EarSide.RIGHT) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                    ) {
                        Text(strings.findBudsPlayRight, color = textPrimary, fontSize = 11.5.sp)
                    }
                }
            }
        }

        if (caps.supportsRemotePowerOff && state.protocolVersion >= 2) {
            SectionCard(textPrimary = textPrimary) {
                if (showPowerOffDialog) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.remotePowerOffConfirmTitle,
                            color = Color(0xFFFF453A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = strings.remotePowerOffConfirmBody,
                            color = textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPowerOffDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.cancelBtn, color = textPrimary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    showPowerOffDialog = false
                                    manager.remotePowerOffAsync { ok ->
                                        if (ok) onClose()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.confirmActionBtn, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(strings.remotePowerOffTitle, color = Color(0xFFFF453A), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                            Text(strings.remotePowerOffDesc, color = textSecondary, fontSize = 11.5.sp)
                        }
                        Button(
                            onClick = { showPowerOffDialog = true },
                            enabled = state.isConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.remotePowerOffBtn, color = Color.White, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultipointSheet(
    state: HeadsetState,
    manager: SonyWindowsHeadsetManager,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings,
    onClose: () -> Unit
) {
    val caps = state.capabilities
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetHeaderRow(
            title = strings.multipointSheetTitle,
            subtitle = state.hardwareModelName.ifBlank { state.deviceName },
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            isBusy = state.isBusy,
            onRefresh = { manager.syncExtendedControlsAsync() },
            refreshDesc = strings.refreshFromChipBtn,
            onClose = onClose
        )

        SectionCard(textPrimary = textPrimary) {
            SettingSwitchRow(
                title = strings.multipointToggleTitle,
                subtitle = strings.multipointToggleDesc,
                checked = state.multipointEnabled == true,
                enabled = state.isConnected && caps.supportsMultipoint,
                accentColor = accentColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onCheckedChange = { manager.setMultipointEnabledAsync(it) }
            )
        }

        SectionCard(textPrimary = textPrimary) {
            Text(strings.multipointHostsTitle, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (state.pairedDevices.isEmpty()) {
                Text(strings.multipointNoHosts, color = textSecondary, fontSize = 12.sp)
            } else {
                state.pairedDevices.forEachIndexed { idx, host ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = textPrimary.copy(alpha = 0.08f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = host.friendlyName,
                                color = textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = host.address, color = textSecondary, fontSize = 11.sp)
                            if (host.isActiveSource || host.isConnected) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (host.isActiveSource) strings.multipointActiveSourceBadge else strings.multipointConnectedBadge,
                                    color = if (host.isActiveSource) accentColor else textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (caps.multipointViaPeri) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (host.isConnected && !host.isActiveSource) {
                                    OutlinedButton(
                                        onClick = { manager.switchMultipointActiveSourceAsync(host.address) },
                                        enabled = state.isConnected
                                    ) {
                                        Text(strings.multipointSwitchSourceBtn, fontSize = 11.sp, color = accentColor)
                                    }
                                }
                                Button(
                                    onClick = {
                                        manager.setMultipointDeviceConnectionAsync(host.address, !host.isConnected)
                                    },
                                    enabled = state.isConnected,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (host.isConnected) textPrimary.copy(alpha = 0.12f) else accentColor
                                    )
                                ) {
                                    Text(
                                        text = if (host.isConnected) strings.multipointDisconnectBtn else strings.multipointConnectBtn,
                                        fontSize = 11.sp,
                                        color = if (host.isConnected) textPrimary else Color.Black
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationBackupSheet(
    manager: SonyWindowsHeadsetManager,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings,
    onClose: () -> Unit
) {
    val cfg by manager.automationConfig.collectAsState()
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetHeaderRow(
            title = strings.automationSheetTitle,
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onClose = onClose
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = accentColor.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.automationPrivacyBadge, color = accentColor, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        SectionCard(textPrimary = textPrimary) {
            SettingSwitchRow(
                title = strings.automationMasterTitle,
                subtitle = strings.automationMasterDesc,
                checked = cfg.masterEnabled,
                accentColor = accentColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onCheckedChange = { manager.updateAutomationConfig(cfg.copy(masterEnabled = it)) }
            )

            if (cfg.masterEnabled) {
                HorizontalDivider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                SettingSwitchRow(
                    title = strings.automationCallRuleTitle,
                    subtitle = strings.automationCallRuleDesc,
                    checked = cfg.callRuleEnabled,
                    accentColor = accentColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onCheckedChange = { manager.updateAutomationConfig(cfg.copy(callRuleEnabled = it)) }
                )

                HorizontalDivider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                SettingSwitchRow(
                    title = strings.automationMediaRuleTitle,
                    subtitle = strings.automationMediaRuleDesc,
                    checked = cfg.mediaRuleEnabled,
                    accentColor = accentColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onCheckedChange = { manager.updateAutomationConfig(cfg.copy(mediaRuleEnabled = it)) }
                )
                if (cfg.mediaRuleEnabled) {
                    ModePickerChips(
                        label = strings.automationTargetModeLabel,
                        selected = cfg.mediaTargetMode,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        strings = strings,
                        onSelect = { manager.updateAutomationConfig(cfg.copy(mediaTargetMode = it)) }
                    )
                }

                HorizontalDivider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                SettingSwitchRow(
                    title = strings.automationScheduleRuleTitle,
                    checked = cfg.scheduleRuleEnabled,
                    accentColor = accentColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onCheckedChange = { manager.updateAutomationConfig(cfg.copy(scheduleRuleEnabled = it)) }
                )
                if (cfg.scheduleRuleEnabled) {
                    HourStepperRow(
                        label = strings.automationScheduleStartLabel,
                        hour = cfg.scheduleStartHour,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onChange = { h ->
                            manager.updateAutomationConfig(cfg.copy(scheduleStartHour = h))
                        }
                    )
                    HourStepperRow(
                        label = strings.automationScheduleEndLabel,
                        hour = cfg.scheduleEndHour,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onChange = { h ->
                            manager.updateAutomationConfig(cfg.copy(scheduleEndHour = h))
                        }
                    )
                    ModePickerChips(
                        label = strings.automationTargetModeLabel,
                        selected = cfg.scheduleTargetMode,
                        accentColor = accentColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        strings = strings,
                        onSelect = { manager.updateAutomationConfig(cfg.copy(scheduleTargetMode = it)) }
                    )
                }
            }
        }

        // Respaldo y restauración local (.json) vía diálogo nativo de Windows
        SectionCard(textPrimary = textPrimary) {
            Text(strings.backupSectionTitle, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                val json = manager.exportBackupJson()
                                WindowsFileDialogs.saveTextFile("nuclearwearable_profile.json", "json", json)
                            }
                            val saved = res.getOrNull()
                            if (res.isSuccess && saved != null) {
                                statusIsError = false
                                statusMessage = "${strings.backupExportSuccess} (${saved.name})"
                            } else if (res.isFailure) {
                                statusIsError = true
                                statusMessage = strings.backupImportError
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.backupExportBtn, color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val rawRes = withContext(Dispatchers.IO) {
                                WindowsFileDialogs.openTextFile(strings.backupImportBtn, listOf("json"))
                            }
                            val raw = rawRes.getOrNull() ?: return@launch
                            val restored = manager.restoreBackupJson(raw)
                            statusIsError = restored.isFailure
                            statusMessage = if (restored.isSuccess) strings.backupImportSuccess else strings.backupImportError
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.backupImportBtn, color = textPrimary, fontSize = 11.5.sp)
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusMessage ?: "",
                    color = if (statusIsError) Color(0xFFFF453A) else accentColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LabModeSheet(
    state: HeadsetState,
    hud: LiveCodecHudInfo,
    hexLogs: List<HexLogEntry>,
    manager: SonyWindowsHeadsetManager,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    strings: LocalizedStrings,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hexUnknownOnly by remember { mutableStateOf(false) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        SheetHeaderRow(
            title = strings.labModeTitle,
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onClose = onClose
        )
        Spacer(modifier = Modifier.height(4.dp))

        val hwDisplay = state.hardwareModelName.ifBlank { state.modelFamily.displayName }
        val fwDisplay = state.firmwareVersion.ifBlank { "--" }
        val codecDisplay = hud.formatShortBadge().ifBlank { state.codecName.ifBlank { "--" } }
        Text(
            text = "Proto: V${state.protocolVersion}  •  HW: $hwDisplay  •  FW: $fwDisplay  •  $codecDisplay  •  T1:${state.supportFunctions.size} / T2:${state.table2SupportFunctions.size}",
            color = textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = hexUnknownOnly,
                onClick = { hexUnknownOnly = !hexUnknownOnly },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(strings.hexFilterUnknownOnly, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                    selectedLabelColor = accentColor,
                    labelColor = textPrimary
                )
            )
            feedbackMsg?.let { msg ->
                Text(msg, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val displayedLogs = if (hexUnknownOnly) {
            hexLogs.filter { ProfileBackupManager.isUnknownOrUnparsedFrame(it) }
        } else {
            hexLogs
        }

        HexMonitorCard(
            logs = displayedLogs,
            onClear = { manager.clearHexLogs() },
            onCopy = {
                val dump = ProfileBackupManager.formatHexMonitorExport(hexLogs, hexUnknownOnly)
                if (WindowsFileDialogs.copyToClipboard(dump)) {
                    feedbackMsg = "Copied!"
                }
            },
            onExportFile = {
                scope.launch {
                    val dump = ProfileBackupManager.formatHexMonitorExport(hexLogs, hexUnknownOnly)
                    val res = withContext(Dispatchers.IO) {
                        WindowsFileDialogs.saveTextFile("nuclearwearable_rfcomm_logs.txt", "txt", dump)
                    }
                    if (res.getOrNull() != null) {
                        feedbackMsg = strings.backupExportSuccess
                    }
                }
            }
        )
    }
}

@Composable
private fun HexMonitorCard(
    logs: List<HexLogEntry>,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onExportFile: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RFCOMM Hex Trace (${logs.size})",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Row {
                    IconButton(onClick = onExportFile, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Guardar .txt",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(16.dp)
                        )
                    }
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

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D0D0E))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Esperando paquetes RFCOMM...",
                        color = Color(0xFF55555A),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                HexLogEntry.Direction.ERROR -> "! ERR:"
                            }
                            Column {
                                Text(
                                    text = "$prefix ${entry.summary}",
                                    color = color,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (entry.hex.isNotBlank()) {
                                    Text(
                                        text = entry.hex,
                                        color = Color(0xFFA1A1AA),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModePickerChips(
    label: String,
    selected: NoiseControlMode,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    strings: LocalizedStrings,
    onSelect: (NoiseControlMode) -> Unit
) {
    Text(label, color = textSecondary, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            NoiseControlMode.ANC to strings.noiseCancelling,
            NoiseControlMode.AMBIENT to strings.ambientSound,
            NoiseControlMode.OFF to strings.noiseOff
        ).forEach { (mode, text) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(text, fontSize = 11.5.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                    selectedLabelColor = accentColor,
                    labelColor = textPrimary
                )
            )
        }
    }
}

@Composable
private fun HourStepperRow(
    label: String,
    hour: Int,
    textPrimary: Color,
    textSecondary: Color,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textSecondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−") }
            Text(
                text = String.format(Locale.US, "%02d:00", hour),
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.width(48.dp)
            )
            TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
        }
    }
}
