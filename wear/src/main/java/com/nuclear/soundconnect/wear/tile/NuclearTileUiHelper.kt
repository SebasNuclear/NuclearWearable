package com.nuclear.soundconnect.wear.tile

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.TileService
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.WearMainActivity

/**
 * Componentes compartidos entre los Tiles de NuclearWearable:
 * - Botones circulares vectoriales sin texto y con cambio in-place (LoadAction)
 * - Acceso directo a la app para ajuste fino de Sonido Ambiente
 * - Paleta OLED coherente con NuclearUI móvil y atenuación gris al desconectar
 */
internal object NuclearTileUiHelper {

    const val CLICK_SET_ANC = "click_set_anc"
    const val CLICK_SET_AMBIENT = "click_set_ambient"
    const val CLICK_SET_OFF = "click_set_off"
    const val CLICK_OPEN_MAIN = "click_open_main"
    const val CLICK_OPEN_AMBIENT_SETTINGS = "click_open_ambient_settings"

    const val ID_ICON_ANC_ON = "id_icon_anc_on"
    const val ID_ICON_AMBIENT = "id_icon_ambient"
    const val ID_ICON_ANC_OFF = "id_icon_anc_off"
    const val ID_ICON_TUNE = "id_icon_tune"

    const val COLOR_SURFACE_CARD = 0xFF111113.toInt()
    const val COLOR_SURFACE_ELEVATED = 0xFF1F1F23.toInt()
    const val COLOR_INACTIVE_CIRCLE = 0xFF18181B.toInt()
    const val COLOR_OFFLINE_ACTIVE_CIRCLE = 0xFF3A3A3C.toInt()
    const val COLOR_DISCONNECTED_TINT = 0x73505054.toInt()
    const val COLOR_CASE_SILVER = 0xFFE5E5EA.toInt()
    const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val COLOR_TEXT_MUTED = 0xFF8E8E93.toInt()

    fun handleModeClickIfPresent(
        context: Context,
        lastClickableId: String,
        repository: WearHeadsetRepository,
        siblingTileClass: Class<out TileService>
    ) {
        val targetMode = when (lastClickableId) {
            CLICK_SET_ANC -> NoiseControlMode.ANC
            CLICK_SET_AMBIENT -> NoiseControlMode.AMBIENT
            CLICK_SET_OFF -> NoiseControlMode.OFF
            else -> null
        } ?: return

        repository.setNoiseMode(targetMode)
        try {
            TileService.getUpdater(context).requestUpdate(siblingTileClass)
        } catch (_: Exception) {
        }
    }

    fun ResourceBuilders.Resources.Builder.addNoiseControlResources(): ResourceBuilders.Resources.Builder {
        return this
            .addAndroidDrawable(ID_ICON_ANC_ON, R.drawable.ic_anc_on)
            .addAndroidDrawable(ID_ICON_AMBIENT, R.drawable.ic_ambient_sound)
            .addAndroidDrawable(ID_ICON_ANC_OFF, R.drawable.ic_anc_off)
            .addAndroidDrawable(ID_ICON_TUNE, R.drawable.ic_tune)
    }

    fun ResourceBuilders.Resources.Builder.addAndroidDrawable(
        id: String,
        @DrawableRes resId: Int
    ): ResourceBuilders.Resources.Builder {
        return addIdToImageMapping(
            id,
            ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(resId)
                        .build()
                )
                .build()
        )
    }

    fun buildNoiseCirclesRow(
        state: HeadsetState,
        accentColor: Int,
        buttonSizeDp: Float,
        iconSizeDp: Float,
        spacingDp: Float
    ): LayoutElementBuilders.Row {
        val effectiveActiveBg = if (state.isConnected) accentColor else COLOR_OFFLINE_ACTIVE_CIRCLE
        val effectiveActiveTint = if (state.isConnected) COLOR_TEXT_PRIMARY else COLOR_TEXT_MUTED

        val modes = listOf(
            Triple(NoiseControlMode.ANC, ID_ICON_ANC_ON, CLICK_SET_ANC),
            Triple(NoiseControlMode.AMBIENT, ID_ICON_AMBIENT, CLICK_SET_AMBIENT),
            Triple(NoiseControlMode.OFF, ID_ICON_ANC_OFF, CLICK_SET_OFF)
        )

        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        modes.forEachIndexed { index, (mode, iconId, clickId) ->
            val isActive = state.noiseMode == mode
            val bgColor = if (isActive) effectiveActiveBg else COLOR_INACTIVE_CIRCLE
            val iconTint = if (isActive) effectiveActiveTint else COLOR_TEXT_MUTED

            row.addContent(
                buildCircularIconButton(
                    iconResId = iconId,
                    clickId = clickId,
                    sizeDp = buttonSizeDp,
                    iconSizeDp = iconSizeDp,
                    bgColor = bgColor,
                    iconTint = iconTint
                )
            )

            if (index < modes.lastIndex) {
                row.addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setWidth(dp(spacingDp))
                        .build()
                )
            }
        }

        return row.build()
    }

    private fun buildCircularIconButton(
        iconResId: String,
        clickId: String,
        sizeDp: Float,
        iconSizeDp: Float,
        bgColor: Int,
        iconTint: Int
    ): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(bgColor))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(sizeDp / 2f))
                                    .build()
                            )
                            .build()
                    )
                    .setClickable(createLoadClickable(clickId))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(iconResId)
                    .setWidth(dp(iconSizeDp))
                    .setHeight(dp(iconSizeDp))
                    .setColorFilter(
                        LayoutElementBuilders.ColorFilter.Builder()
                            .setTint(argb(iconTint))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /**
     * Píldora discreta para abrir la app directamente en el ajuste fino de Sonido Ambiente.
     */
    fun buildAmbientTunePill(
        context: Context,
        ambientLevel: Int,
        isConnected: Boolean,
        accentColor: Int
    ): LayoutElementBuilders.LayoutElement {
        val labelColor = if (isConnected) accentColor else COLOR_TEXT_MUTED

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(COLOR_SURFACE_ELEVATED))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(999f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(9f))
                            .setEnd(dp(9f))
                            .setTop(dp(3f))
                            .setBottom(dp(3f))
                            .build()
                    )
                    .setClickable(createOpenAppClickable(context, openAmbientSettings = true))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(ID_ICON_TUNE)
                            .setWidth(dp(11f))
                            .setHeight(dp(11f))
                            .setColorFilter(
                                LayoutElementBuilders.ColorFilter.Builder()
                                    .setTint(argb(labelColor))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
                    .addContent(
                        Text.Builder(context, "Nivel $ambientLevel/20")
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(labelColor))
                            .setMaxLines(1)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    fun createLoadClickable(clickId: String): ModifiersBuilders.Clickable {
        return ModifiersBuilders.Clickable.Builder()
            .setId(clickId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
    }

    fun createOpenAppClickable(
        context: Context,
        openAmbientSettings: Boolean = false
    ): ModifiersBuilders.Clickable {
        val activityBuilder = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(WearMainActivity::class.java.name)

        if (openAmbientSettings) {
            activityBuilder.addKeyToExtraMapping(
                WearMainActivity.EXTRA_OPEN_AMBIENT,
                ActionBuilders.AndroidBooleanExtra.Builder()
                    .setValue(true)
                    .build()
            )
        }

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(activityBuilder.build())
            .build()

        return ModifiersBuilders.Clickable.Builder()
            .setId(if (openAmbientSettings) CLICK_OPEN_AMBIENT_SETTINGS else CLICK_OPEN_MAIN)
            .setOnClick(launchAction)
            .build()
    }
}
