package com.nuclear.soundconnect.wear.presentation.util

import com.nuclear.soundconnect.bluetooth.SonyModelFamily
import com.nuclear.soundconnect.wear.R

/**
 * Mapeador de recursos vectoriales específicos para cada familia de audífonos Sony en Wear OS.
 */
object SonyModelDrawables {

    data class ModelDrawables(
        val leftBud: Int,
        val rightBud: Int,
        val case: Int,
        val headphone: Int
    )

    fun getDrawablesForFamily(family: SonyModelFamily): ModelDrawables {
        return when (family) {
            SonyModelFamily.WF_1000XM6 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.WF_1000XM5 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm5_bud_left,
                rightBud = R.drawable.ic_wf1000xm5_bud_right,
                case = R.drawable.ic_wf1000xm5_case,
                headphone = R.drawable.ic_wh1000xm5_headphone
            )
            SonyModelFamily.WF_1000XM4 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm4_bud_left,
                rightBud = R.drawable.ic_wf1000xm4_bud_right,
                case = R.drawable.ic_wf1000xm4_case,
                headphone = R.drawable.ic_wh1000xm4_headphone
            )
            SonyModelFamily.WF_1000XM3 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm3_bud_left,
                rightBud = R.drawable.ic_wf1000xm3_bud_right,
                case = R.drawable.ic_wf1000xm3_case,
                headphone = R.drawable.ic_wh1000xm3_headphone
            )
            SonyModelFamily.WH_1000XM6 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.WH_1000XM5 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm5_bud_left,
                rightBud = R.drawable.ic_wf1000xm5_bud_right,
                case = R.drawable.ic_wf1000xm5_case,
                headphone = R.drawable.ic_wh1000xm5_headphone
            )
            SonyModelFamily.WH_1000XM4 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm4_bud_left,
                rightBud = R.drawable.ic_wf1000xm4_bud_right,
                case = R.drawable.ic_wf1000xm4_case,
                headphone = R.drawable.ic_wh1000xm4_headphone
            )
            SonyModelFamily.WH_1000XM3 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm3_bud_left,
                rightBud = R.drawable.ic_wf1000xm3_bud_right,
                case = R.drawable.ic_wf1000xm3_case,
                headphone = R.drawable.ic_wh1000xm3_headphone
            )
            SonyModelFamily.LINKBUDS_OPEN -> ModelDrawables(
                leftBud = R.drawable.ic_linkbuds_open_bud_left,
                rightBud = R.drawable.ic_linkbuds_open_bud_right,
                case = R.drawable.ic_linkbuds_open_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.LINKBUDS_S -> ModelDrawables(
                leftBud = R.drawable.ic_linkbuds_s_bud_left,
                rightBud = R.drawable.ic_linkbuds_s_bud_right,
                case = R.drawable.ic_linkbuds_s_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.WH_ULT900N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_ultwear_headphone
            )
            SonyModelFamily.WH_CH720N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whch720n_headphone
            )
            SonyModelFamily.WH_CH520 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whch520_headphone
            )
            SonyModelFamily.WH_XB910N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whxb910n_headphone
            )
            SonyModelFamily.WF_C700N -> ModelDrawables(
                leftBud = R.drawable.ic_wfc700n_bud_left,
                rightBud = R.drawable.ic_wfc700n_bud_right,
                case = R.drawable.ic_wfc700n_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.WF_C500 -> ModelDrawables(
                leftBud = R.drawable.ic_wfc500_bud_left,
                rightBud = R.drawable.ic_wfc500_bud_right,
                case = R.drawable.ic_wfc500_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.WF_SP800N -> ModelDrawables(
                leftBud = R.drawable.ic_wfsp800n_bud_left,
                rightBud = R.drawable.ic_wfsp800n_bud_right,
                case = R.drawable.ic_wfsp800n_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.WI_1000X -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wi1000x_neckband
            )
            SonyModelFamily.WI_C100 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_wic100_neckband
            )
            SonyModelFamily.INZONE_BUDS -> ModelDrawables(
                leftBud = R.drawable.ic_inzone_buds_bud_left,
                rightBud = R.drawable.ic_inzone_buds_bud_right,
                case = R.drawable.ic_inzone_buds_case,
                headphone = R.drawable.ic_wh1000xm6_headphone
            )
            SonyModelFamily.MDR_XB950 -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_mdrxb950_headphone
            )
            SonyModelFamily.WH_H910N -> ModelDrawables(
                leftBud = R.drawable.ic_wf1000xm6_bud_left,
                rightBud = R.drawable.ic_wf1000xm6_bud_right,
                case = R.drawable.ic_wf1000xm6_case,
                headphone = R.drawable.ic_whh910n_headphone
            )
            SonyModelFamily.GENERIC_OVER_EAR -> ModelDrawables(
                leftBud = R.drawable.ic_generic_bud_left,
                rightBud = R.drawable.ic_generic_bud_right,
                case = R.drawable.ic_generic_case,
                headphone = R.drawable.ic_generic_headphone
            )
            SonyModelFamily.GENERIC_TWS -> ModelDrawables(
                leftBud = R.drawable.ic_generic_bud_left,
                rightBud = R.drawable.ic_generic_bud_right,
                case = R.drawable.ic_generic_case,
                headphone = R.drawable.ic_generic_headphone
            )
        }
    }
}
