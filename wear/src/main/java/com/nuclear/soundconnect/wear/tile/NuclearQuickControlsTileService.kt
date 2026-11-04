package com.nuclear.soundconnect.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
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
import com.nuclear.soundconnect.wear.presentation.WearMainActivity

/**
 * Tile de Acceso Rápido Compacto (Fila de Botones One UI / Estilo Samsung Quick Controls).
 * Permite conmutar directamente entre ANC, Ambiente y Off en una sola fila minimalista.
 */
class NuclearQuickControlsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val repository = WearHeadsetRepository.getInstance(applicationContext)
        val state = repository.state.value
        val deviceParams = requestParams.deviceConfiguration

        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildQuickRowLayout(state, deviceParams))
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
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun buildQuickRowLayout(
        state: HeadsetState,
        deviceParams: DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val mainClickable = createActivityClickable(applicationContext, null)
        val mainModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(mainClickable)
            .build()

        val layoutBuilder = PrimaryLayout.Builder(deviceParams)
        val accentColor = state.accentColor

        val titleText = if (state.isConnected) state.deviceName else "${state.deviceName} (Offline)"

        // 1. Cabecera compacta
        val titleElement = Text.Builder(applicationContext, titleText)
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(if (state.isConnected) accentColor else COLOR_TEXT_MUTED))
            .setMaxLines(1)
            .build()

        // 2. Fila Única de 3 Botones Compactos en Fila (ANC / AMB / OFF)
        val buttonsRow = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        if (state.modelFamily.hasAnc) {
            val isAnc = state.noiseMode == NoiseControlMode.ANC
            val isAmb = state.noiseMode == NoiseControlMode.AMBIENT
            val isOff = state.noiseMode == NoiseControlMode.OFF

            // Botón ANC
            val ancChip = CompactChip.Builder(
                applicationContext,
                "ANC",
                createActivityClickable(applicationContext, WearMainActivity.ACTION_SET_ANC),
                deviceParams
            )
                .setChipColors(
                    ChipColors(
                        argb(if (isAnc) accentColor else COLOR_SURFACE_CARD),
                        argb(if (isAnc) COLOR_BLACK else COLOR_TEXT_PRIMARY)
                    )
                )
                .build()

            // Botón Ambiente
            val ambChip = CompactChip.Builder(
                applicationContext,
                "Amb",
                createActivityClickable(applicationContext, WearMainActivity.ACTION_SET_AMBIENT),
                deviceParams
            )
                .setChipColors(
                    ChipColors(
                        argb(if (isAmb) accentColor else COLOR_SURFACE_CARD),
                        argb(if (isAmb) COLOR_BLACK else COLOR_TEXT_PRIMARY)
                    )
                )
                .build()

            // Botón Off
            val offChip = CompactChip.Builder(
                applicationContext,
                "Off",
                createActivityClickable(applicationContext, WearMainActivity.ACTION_SET_OFF),
                deviceParams
            )
                .setChipColors(
                    ChipColors(
                        argb(if (isOff) COLOR_OFF_ACTIVE else COLOR_SURFACE_CARD),
                        argb(if (isOff) COLOR_BLACK else COLOR_TEXT_PRIMARY)
                    )
                )
                .build()

            buttonsRow.addContent(ancChip)
            buttonsRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
            buttonsRow.addContent(ambChip)
            buttonsRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
            buttonsRow.addContent(offChip)
        } else {
            val openChip = CompactChip.Builder(
                applicationContext,
                "Abrir",
                mainClickable,
                deviceParams
            )
                .setChipColors(ChipColors(argb(COLOR_SURFACE_CARD), argb(accentColor)))
                .build()

            buttonsRow.addContent(openChip)
        }

        val centerColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(mainModifiers)
            .addContent(titleElement)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(buttonsRow.build())
            .build()

        layoutBuilder.setContent(centerColumn)
        return layoutBuilder.build()
    }

    private fun createActivityClickable(context: Context, action: String?): ModifiersBuilders.Clickable {
        val launchActionBuilder = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName(WearMainActivity::class.java.name)
                    .build()
            )

        return ModifiersBuilders.Clickable.Builder()
            .setId(action ?: "quick_controls")
            .setOnClick(launchActionBuilder.build())
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"

        private const val COLOR_BLACK = 0xFF000000.toInt()
        private const val COLOR_SURFACE_CARD = 0xFF181A22.toInt()
        private const val COLOR_OFF_ACTIVE = 0xFFD0D0D0.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_MUTED = 0xFFA0A0B0.toInt()
    }
}
