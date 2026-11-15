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
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.theme.WearUiPrefs
import com.nuclear.soundconnect.wear.tile.NuclearTileUiHelper.addNoiseControlResources

/**
 * Tile de Acceso Rápido: 3 botones circulares vectoriales (ANC / Ambiente / Off)
 * con conmutación directa sin abrir la app, y acceso rápido al ajuste de Sonido Ambiente.
 */
class NuclearQuickControlsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val repository = WearHeadsetRepository.getInstance(applicationContext)
        NuclearTileUiHelper.handleModeClickIfPresent(
            context = applicationContext,
            lastClickableId = requestParams.currentState.lastClickableId,
            repository = repository,
            siblingTileClass = NuclearTileService::class.java
        )

        val state = repository.state.value
        val deviceParams = requestParams.deviceConfiguration
        val accentColor = WearUiPrefs.resolveAccentColor(applicationContext, state.accentColor)

        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildQuickRowLayout(state, accentColor, deviceParams))
                            .build()
                    )
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(singleTileTimeline)
            .setFreshnessIntervalMillis(60_000L)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addNoiseControlResources()
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun buildQuickRowLayout(
        state: HeadsetState,
        accentColor: Int,
        deviceParams: DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val openAppClickable = NuclearTileUiHelper.createOpenAppClickable(applicationContext)
        val headerModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(openAppClickable)
            .build()

        val layoutBuilder = PrimaryLayout.Builder(deviceParams)

        val titleColor = if (state.isConnected) {
            NuclearTileUiHelper.COLOR_TEXT_PRIMARY
        } else {
            NuclearTileUiHelper.COLOR_TEXT_MUTED
        }

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
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())

        if (state.effectiveHasAnc) {
            centerColumn.addContent(
                NuclearTileUiHelper.buildNoiseCirclesRow(
                    state = state,
                    accentColor = accentColor,
                    buttonSizeDp = 48f,
                    iconSizeDp = 22f,
                    spacingDp = 10f
                )
            )

            // Para ajustar nivel y enfoque de sonido ambiente, abrir la app desde la píldora inferior
            if (state.noiseMode == NoiseControlMode.AMBIENT) {
                layoutBuilder.setPrimaryChipContent(
                    NuclearTileUiHelper.buildAmbientTunePill(
                        context = applicationContext,
                        ambientLevel = state.ambientLevel,
                        isConnected = state.isConnected,
                        accentColor = accentColor
                    )
                )
            }
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

            centerColumn.addContent(openChip)
        }

        layoutBuilder.setContent(centerColumn.build())
        return layoutBuilder.build()
    }

    companion object {
        private const val RESOURCES_VERSION = "2_vector_circles"
    }
}
