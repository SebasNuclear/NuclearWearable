package com.nuclear.soundconnect.widget

import android.content.Context

object NuclearWidgetManager {
    fun updateAllWidgets(context: Context) {
        SonyBudsWidgetProvider.updateAllWidgets(context)
        NuclearBatteryWidgetProvider.updateAllWidgets(context)
        NuclearNoiseWidgetProvider.updateAllWidgets(context)
    }
}
