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
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.WearMainActivity
import com.nuclear.soundconnect.wear.presentation.util.SonyModelDrawables

/**
 * Wear OS Tile: Tarjeta deslizable interactiva para el carrusel de la carátula.
 * Muestra iconos vectoriales del modelo, estado de batería y controles táctiles One UI.
 */
class NuclearTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val repository = WearHeadsetRepository.getInstance(applicationContext)
        val state = repository.state.value
        val deviceParams = requestParams.deviceConfiguration

        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildLayout(state, deviceParams))
                            .build()
                    )
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("${state.modelFamily.name}_${state.lastSyncTime}")
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
            .addIdToImageMapping(
                ID_ICON_LEFT_BUD,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawables.leftBud)
                            .build()
                    )
                    .build()
            )
            .addIdToImageMapping(
                ID_ICON_RIGHT_BUD,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawables.rightBud)
                            .build()
                    )
                    .build()
            )
            .addIdToImageMapping(
                ID_ICON_CASE,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawables.case)
                            .build()
                    )
                    .build()
            )
            .addIdToImageMapping(
                ID_ICON_HEADPHONE,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawables.headphone)
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(resources)
    }

    private fun buildLayout(
        state: HeadsetState,
        deviceParams: DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val mainClickable = createActivityClickable(applicationContext, null)
        val mainModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(mainClickable)
            .build()

        val layoutBuilder = PrimaryLayout.Builder(deviceParams)
        val accentColor = state.accentColor

        // 1. Título y Estado
        val titleText = if (state.isConnected) {
            state.deviceName
        } else {
            "${state.deviceName} (Desconectado)"
        }

        // 2. Fila de Baterías con Iconos Vectoriales
        val batteryRow = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        if (state.deviceType == DeviceType.OVER_EAR) {
            val singlePct = state.singleBattery.displayPercent
            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_HEADPHONE,
                    imageSizeDp = 28f,
                    label = "Diadema",
                    percent = singlePct,
                    isCharging = state.singleBattery.isCharging,
                    valueColor = accentColor
                )
            )
        } else {
            val leftPct = state.leftBattery.displayPercent
            val rightPct = state.rightBattery.displayPercent
            val casePct = state.caseBattery.displayPercent

            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_LEFT_BUD,
                    imageSizeDp = 22f,
                    label = "L",
                    percent = leftPct,
                    isCharging = state.leftBattery.isCharging,
                    valueColor = accentColor
                )
            )
            batteryRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_RIGHT_BUD,
                    imageSizeDp = 22f,
                    label = "R",
                    percent = rightPct,
                    isCharging = state.rightBattery.isCharging,
                    valueColor = accentColor
                )
            )
            batteryRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
            batteryRow.addContent(
                buildBatteryCapsule(
                    imageResourceId = ID_ICON_CASE,
                    imageSizeDp = 22f,
                    label = "Estuche",
                    percent = casePct,
                    isCharging = state.caseBattery.isCharging,
                    valueColor = COLOR_SONY_GOLD
                )
            )
        }

        // 3. Contenido Central (Título + Espaciador + Fila de Baterías)
        val centerColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(mainModifiers)
            .addContent(
                Text.Builder(applicationContext, titleText)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(if (state.isConnected) accentColor else COLOR_TEXT_MUTED))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(batteryRow.build())
            .build()

        layoutBuilder.setContent(centerColumn)

        // 4. Fila Inferior: Chips táctiles de Control de Ruido
        if (state.modelFamily.hasAnc) {
            val ancActive = state.noiseMode == NoiseControlMode.ANC
            val ambActive = state.noiseMode == NoiseControlMode.AMBIENT

            val ancChip = CompactChip.Builder(
                applicationContext,
                "ANC",
                createActivityClickable(applicationContext, WearMainActivity.ACTION_SET_ANC),
                deviceParams
            )
                .setChipColors(
                    ChipColors(
                        argb(if (ancActive) accentColor else COLOR_SURFACE_CARD),
                        argb(if (ancActive) COLOR_BLACK else COLOR_TEXT_PRIMARY)
                    )
                )
                .build()

            val ambChip = CompactChip.Builder(
                applicationContext,
                "Ambiente",
                createActivityClickable(applicationContext, WearMainActivity.ACTION_SET_AMBIENT),
                deviceParams
            )
                .setChipColors(
                    ChipColors(
                        argb(if (ambActive) accentColor else COLOR_SURFACE_CARD),
                        argb(if (ambActive) COLOR_BLACK else COLOR_TEXT_PRIMARY)
                    )
                )
                .build()

            val actionsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .addContent(ancChip)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
                .addContent(ambChip)
                .build()

            layoutBuilder.setPrimaryChipContent(actionsRow)
        } else {
            val openChip = CompactChip.Builder(
                applicationContext,
                "Abrir",
                mainClickable,
                deviceParams
            )
                .setChipColors(ChipColors(argb(COLOR_SURFACE_CARD), argb(accentColor)))
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
        isCharging: Boolean,
        valueColor: Int
    ): LayoutElementBuilders.LayoutElement {
        val boltSuffix = if (isCharging) " ⚡" else ""
        val displayText = "$percent$boltSuffix"

        val imageElement = LayoutElementBuilders.Image.Builder()
            .setResourceId(imageResourceId)
            .setWidth(dp(imageSizeDp))
            .setHeight(dp(imageSizeDp))
            .build()

        val pctText = Text.Builder(applicationContext, displayText)
            .setTypography(Typography.TYPOGRAPHY_TITLE3)
            .setColor(argb(valueColor))
            .build()

        val labelText = Text.Builder(applicationContext, label)
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_TEXT_MUTED))
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(COLOR_SURFACE_CARD))
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
                    .addContent(pctText)
                    .addContent(labelText)
                    .build()
            )
            .build()
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
            .setId(action ?: "main_activity")
            .setOnClick(launchActionBuilder.build())
            .build()
    }

    companion object {
        const val ID_ICON_LEFT_BUD = "id_icon_left_bud"
        const val ID_ICON_RIGHT_BUD = "id_icon_right_bud"
        const val ID_ICON_CASE = "id_icon_case"
        const val ID_ICON_HEADPHONE = "id_icon_headphone"

        private const val COLOR_BLACK = 0xFF000000.toInt()
        private const val COLOR_SURFACE_CARD = 0xFF181A22.toInt()
        private const val COLOR_SONY_GOLD = 0xFFD4AF37.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_MUTED = 0xFFA0A0B0.toInt()
    }
}
