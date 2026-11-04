package com.nuclear.soundconnect.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.wear.R
import com.nuclear.soundconnect.wear.data.WearHeadsetRepository
import com.nuclear.soundconnect.wear.presentation.WearMainActivity

/**
 * Proveedor de Complicaciones para carátulas de reloj Wear OS (Estilo Samsung Complications).
 * Soporta ranuras compactas de fila única (SHORT_TEXT y RANGED_VALUE) para monitorear
 * la batería y el modo de ruido actual desde cualquier carátula.
 */
class NuclearComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_wf1000xm6_bud_left)).build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("85%").build(),
                    contentDescription = PlainComplicationText.Builder("Nuclear Batería").build()
                )
                    .setTitle(PlainComplicationText.Builder("ANC").build())
                    .setMonochromaticImage(icon)
                    .setTapAction(tapIntent)
                    .build()
            }
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = 85f,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Nuclear Batería").build()
                )
                    .setText(PlainComplicationText.Builder("85%").build())
                    .setTitle(PlainComplicationText.Builder("ANC").build())
                    .setMonochromaticImage(icon)
                    .setTapAction(tapIntent)
                    .build()
            }
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val repository = WearHeadsetRepository.getInstance(applicationContext)
        val state = repository.state.value

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val primaryLevel = if (state.deviceType == DeviceType.OVER_EAR) {
            state.singleBattery.level
        } else {
            if (state.leftBattery.isAvailable && state.rightBattery.isAvailable) {
                (state.leftBattery.level + state.rightBattery.level) / 2
            } else if (state.leftBattery.isAvailable) {
                state.leftBattery.level
            } else if (state.rightBattery.isAvailable) {
                state.rightBattery.level
            } else {
                -1
            }
        }

        val text = if (primaryLevel in 0..100) "$primaryLevel%" else "--%"
        val iconRes = if (state.deviceType == DeviceType.OVER_EAR) {
            R.drawable.ic_generic_headphone
        } else {
            R.drawable.ic_wf1000xm6_bud_left
        }
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, iconRes)).build()

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text).build(),
                    contentDescription = PlainComplicationText.Builder(state.deviceName).build()
                )
                    .setTitle(PlainComplicationText.Builder(state.noiseMode.name).build())
                    .setMonochromaticImage(icon)
                    .setTapAction(tapIntent)
                    .build()
            }
            ComplicationType.RANGED_VALUE -> {
                val clampedVal = if (primaryLevel in 0..100) primaryLevel.toFloat() else 0f
                RangedValueComplicationData.Builder(
                    value = clampedVal,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder(state.deviceName).build()
                )
                    .setText(PlainComplicationText.Builder(text).build())
                    .setTitle(PlainComplicationText.Builder(state.noiseMode.name).build())
                    .setMonochromaticImage(icon)
                    .setTapAction(tapIntent)
                    .build()
            }
            else -> null
        }
    }
}
