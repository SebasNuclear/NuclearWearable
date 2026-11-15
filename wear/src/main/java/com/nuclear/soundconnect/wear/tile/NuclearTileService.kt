package com.nuclear.soundconnect.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.theme.WearUiPrefs
import com.nuclear.soundconnect.wear.presentation.util.SonyModelDrawables
import com.nuclear.soundconnect.wear.tile.NuclearTileUiHelper.addAndroidDrawable
import com.nuclear.soundconnect.wear.tile.NuclearTileUiHelper.addNoiseControlResources

/**
 * Wear OS Tile principal: muestra baterías por componente con atenuación gris al desconectar
 * y 3 botones circulares vectoriales para alternar ANC / Ambiente / Off sin abrir la app.
 */
class NuclearTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val repository = WearHeadsetRepository.getInstance(applicationContext)
        NuclearTileUiHelper.handleModeClickIfPresent(
            context = applicationContext,
            lastClickableId = requestParams.currentState.lastClickableId,
            repository = repository,
            siblingTileClass = NuclearQuickControlsTileService::class.java
        )

        val state = repository.state.value
        val deviceParams = requestParams.deviceConfiguration
        val accentColor = WearUiPrefs.resolveAccentColor(applicationContext, state.accentColor)

        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildLayout(state, accentColor, deviceParams))
                            .build()
                    )
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("v2_${state.modelFamily.name}_${state.lastSyncTime}")
            .setTileTimeline(singleTileTimeline)
            .setFreshnessIntervalMillis(60_000L)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val repository = WearHeadsetRepository.getInstance(applicationContext)
        val state = repository.state.value
        val drawables = SonyModelDrawables.getDrawablesForFamily(state.modelFamily)

        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .addAndroidDrawable(ID_ICON_LEFT_BUD, drawables.leftBud)
            .addAndroidDrawable(ID_ICON_RIGHT_BUD, drawables.rightBud)
            .addAndroidDrawable(ID_ICON_CASE, drawables.case)
            .addAndroidDrawable(ID_ICON_HEADPHONE, drawables.headphone)
            .addAndroidDrawable(ID_ICON_CHARGING_BOLT, R.drawable.ic_charging_bolt)
            .addNoiseControlResources()
            .build()

        return Futures.immediateFuture(resources)
    }

    private fun buildLayout(
        state: HeadsetState,
        accentColor: Int,
        deviceParams: DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val openAppClickable = NuclearTileUiHelper.createOpenAppClickable(applicationContext)
        val headerModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(openAppClickable)
            .build()

        val layoutBuilder = PrimaryLayout.Builder(deviceParams)

        // 1. Cabecera sin palabra "Desconectado" (gris atenuado cuando no está conectado)
        val titleColor = if (state.isConnected) {
            NuclearTileUiHelper.COLOR_TEXT_PRIMARY
        } else {
            NuclearTileUiHelper.COLOR_TEXT_MUTED
        }

        // 2. Fila de baterías con iconos vectoriales atenuados en gris al desconectar
        val batteryRow = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        if (state.deviceType == DeviceType.OVER_EAR) {
            val isSingleAvailable = state.isConnected && state.singleBattery.isAvailable
            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_HEADPHONE,
                    imageSizeDp = 24f,
                    label = "Diadema",
                    percent = state.singleBattery.displayPercent,
                    isAvailable = isSingleAvailable,
                    isCharging = state.singleBattery.isCharging,
                    valueColor = accentColor
                )
            )
        } else {
            val isLeftAvailable = state.isConnected && state.leftBattery.isAvailable
            val isRightAvailable = state.isConnected && state.rightBattery.isAvailable
            val isCaseAvailable = state.isConnected && state.caseBattery.isAvailable

            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_LEFT_BUD,
                    imageSizeDp = 20f,
                    label = "L",
                    percent = state.leftBattery.displayPercent,
                    isAvailable = isLeftAvailable,
                    isCharging = state.leftBattery.isCharging,
                    valueColor = accentColor
                )
            )
            batteryRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).build())
            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_RIGHT_BUD,
                    imageSizeDp = 20f,
                    label = "R",
                    percent = state.rightBattery.displayPercent,
                    isAvailable = isRightAvailable,
                    isCharging = state.rightBattery.isCharging,
                    valueColor = accentColor
                )
            )
            batteryRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).build())
            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_CASE,
                    imageSizeDp = 20f,
                    label = "Estuche",
                    percent = state.caseBattery.displayPercent,
                    isAvailable = isCaseAvailable,
                    isCharging = state.caseBattery.isCharging,
                    valueColor = NuclearTileUiHelper.COLOR_CASE_SILVER
                )
            )
        }

        // 3. Contenido central (título + baterías + acceso a ajuste ambiente si aplica)
        val centerColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setModifiers(headerModifiers)
                    .addContent(
                        Text.Builder(applicationContext, state.deviceName)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(titleColor))
                            .setMaxLines(1)
                            .build()
                    )
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).build())
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setModifiers(headerModifiers)
                    .addContent(batteryRow.build())
                    .build()
            )

        if (state.effectiveHasAnc && state.noiseMode == NoiseControlMode.AMBIENT) {
            centerColumn
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).build())
                .addContent(
                    NuclearTileUiHelper.buildAmbientTunePill(
                        context = applicationContext,
                        ambientLevel = state.ambientLevel,
                        isConnected = state.isConnected,
                        accentColor = accentColor
                    )
                )
        }

        layoutBuilder.setContent(centerColumn.build())

        // 4. Ranura inferior: 3 círculos con símbolos vectoriales (sin texto, cambio in-place)
        if (state.effectiveHasAnc) {
            layoutBuilder.setPrimaryChipContent(
                NuclearTileUiHelper.buildNoiseCirclesRow(
                    state = state,
                    accentColor = accentColor,
                    buttonSizeDp = 38f,
                    iconSizeDp = 19f,
                    spacingDp = 8f
                )
            )
        } else {
            val openChip = CompactChip.Builder(
                applicationContext,
                "Abrir",
                openAppClickable,
                deviceParams
            )
                .setChipColors(
                    ChipColors(
                        argb(NuclearTileUiHelper.COLOR_SURFACE_CARD),
                        argb(if (state.isConnected) accentColor else NuclearTileUiHelper.COLOR_TEXT_MUTED)
                    )
                )
                .build()

            layoutBuilder.setPrimaryChipContent(openChip)
        }

        return layoutBuilder.build()
    }

    private fun buildBatteryCapsule(
        imageResourceId: String,
        imageSizeDp: Float,
        label: String,
        percent: String,
        isAvailable: Boolean,
        isCharging: Boolean,
        valueColor: Int
    ): LayoutElementBuilders.LayoutElement {
        val iconTint = if (isAvailable) {
            NuclearTileUiHelper.COLOR_TEXT_PRIMARY
        } else {
            NuclearTileUiHelper.COLOR_DISCONNECTED_TINT
        }
        val pctColor = if (isAvailable) valueColor else NuclearTileUiHelper.COLOR_TEXT_MUTED
        val pctDisplay = if (isAvailable) percent else "--%"

        val imageElement = LayoutElementBuilders.Image.Builder()
            .setResourceId(imageResourceId)
            .setWidth(dp(imageSizeDp))
            .setHeight(dp(imageSizeDp))
            .setColorFilter(
                LayoutElementBuilders.ColorFilter.Builder()
                    .setTint(argb(iconTint))
                    .build()
            )
            .build()

        val pctRow = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(applicationContext, pctDisplay)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(pctColor))
                    .build()
            )

        if (isAvailable && isCharging) {
            pctRow
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(2f)).build())
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(ID_ICON_CHARGING_BOLT)
                        .setWidth(dp(9f))
                        .setHeight(dp(9f))
                        .setColorFilter(
                            LayoutElementBuilders.ColorFilter.Builder()
                                .setTint(argb(pctColor))
                                .build()
                        )
                        .build()
                )
        }

        val labelText = Text.Builder(applicationContext, label)
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(NuclearTileUiHelper.COLOR_TEXT_MUTED))
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(NuclearTileUiHelper.COLOR_SURFACE_CARD))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(12f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(6f))
                            .setEnd(dp(6f))
                            .setTop(dp(4f))
                            .setBottom(dp(4f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(imageElement)
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build())
                    .addContent(pctRow.build())
                    .addContent(labelText)
                    .build()
            )
            .build()
    }

    companion object {
        const val ID_ICON_LEFT_BUD = "id_icon_left_bud"
        const val ID_ICON_RIGHT_BUD = "id_icon_right_bud"
        const val ID_ICON_CASE = "id_icon_case"
        const val ID_ICON_HEADPHONE = "id_icon_headphone"
        const val ID_ICON_CHARGING_BOLT = "id_icon_charging_bolt"
    }
}
