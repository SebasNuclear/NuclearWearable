package com.nuclear.soundconnect.ui

import com.nuclear.soundconnect.bluetooth.SonyModelFamily

/**
 * Mapeador de recursos vectoriales para las 16 familias de modelos Sony en Windows.
 */
object SonyModelIcons {

    data class FamilyIcons(
        val leftBud: String,
        val rightBud: String,
        val case: String,
        val headphone: String
    )

    fun getIcons(family: SonyModelFamily): FamilyIcons = when (family) {
        SonyModelFamily.WF_1000XM6 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.WF_1000XM5 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm5_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm5_bud_right.xml",
            case = "drawable/ic_wf1000xm5_case.xml",
            headphone = "drawable/ic_wh1000xm5_headphone.xml"
        )
        SonyModelFamily.WF_1000XM4 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm4_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm4_bud_right.xml",
            case = "drawable/ic_wf1000xm4_case.xml",
            headphone = "drawable/ic_wh1000xm4_headphone.xml"
        )
        SonyModelFamily.WF_1000XM3 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm3_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm3_bud_right.xml",
            case = "drawable/ic_wf1000xm3_case.xml",
            headphone = "drawable/ic_wh1000xm3_headphone.xml"
        )
        SonyModelFamily.WH_1000XM6 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.WH_1000XM5 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm5_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm5_bud_right.xml",
            case = "drawable/ic_wf1000xm5_case.xml",
            headphone = "drawable/ic_wh1000xm5_headphone.xml"
        )
        SonyModelFamily.WH_1000XM4 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm4_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm4_bud_right.xml",
            case = "drawable/ic_wf1000xm4_case.xml",
            headphone = "drawable/ic_wh1000xm4_headphone.xml"
        )
        SonyModelFamily.WH_1000XM3 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm3_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm3_bud_right.xml",
            case = "drawable/ic_wf1000xm3_case.xml",
            headphone = "drawable/ic_wh1000xm3_headphone.xml"
        )
        SonyModelFamily.LINKBUDS_OPEN -> FamilyIcons(
            leftBud = "drawable/ic_linkbuds_open_bud_left.xml",
            rightBud = "drawable/ic_linkbuds_open_bud_right.xml",
            case = "drawable/ic_linkbuds_open_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.LINKBUDS_S -> FamilyIcons(
            leftBud = "drawable/ic_linkbuds_s_bud_left.xml",
            rightBud = "drawable/ic_linkbuds_s_bud_right.xml",
            case = "drawable/ic_linkbuds_s_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.WH_ULT900N -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_ultwear_headphone.xml"
        )
        SonyModelFamily.WH_CH720N -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_whch720n_headphone.xml"
        )
        SonyModelFamily.WH_CH520 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_whch520_headphone.xml"
        )
        SonyModelFamily.WH_XB910N -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_whxb910n_headphone.xml"
        )
        SonyModelFamily.WF_C700N -> FamilyIcons(
            leftBud = "drawable/ic_wfc700n_bud_left.xml",
            rightBud = "drawable/ic_wfc700n_bud_right.xml",
            case = "drawable/ic_wfc700n_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.WF_C500 -> FamilyIcons(
            leftBud = "drawable/ic_wfc500_bud_left.xml",
            rightBud = "drawable/ic_wfc500_bud_right.xml",
            case = "drawable/ic_wfc500_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.WF_SP800N -> FamilyIcons(
            leftBud = "drawable/ic_wfsp800n_bud_left.xml",
            rightBud = "drawable/ic_wfsp800n_bud_right.xml",
            case = "drawable/ic_wfsp800n_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.WI_1000X -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_wi1000x_neckband.xml"
        )
        SonyModelFamily.WI_C100 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_wic100_neckband.xml"
        )
        SonyModelFamily.INZONE_BUDS -> FamilyIcons(
            leftBud = "drawable/ic_inzone_buds_bud_left.xml",
            rightBud = "drawable/ic_inzone_buds_bud_right.xml",
            case = "drawable/ic_inzone_buds_case.xml",
            headphone = "drawable/ic_wh1000xm6_headphone.xml"
        )
        SonyModelFamily.MDR_XB950 -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_mdrxb950_headphone.xml"
        )
        SonyModelFamily.WH_H910N -> FamilyIcons(
            leftBud = "drawable/ic_wf1000xm6_bud_left.xml",
            rightBud = "drawable/ic_wf1000xm6_bud_right.xml",
            case = "drawable/ic_wf1000xm6_case.xml",
            headphone = "drawable/ic_whh910n_headphone.xml"
        )
        SonyModelFamily.GENERIC_OVER_EAR -> FamilyIcons(
            leftBud = "drawable/ic_generic_bud_left.xml",
            rightBud = "drawable/ic_generic_bud_right.xml",
            case = "drawable/ic_generic_case.xml",
            headphone = "drawable/ic_generic_headphone.xml"
        )
        SonyModelFamily.GENERIC_TWS -> FamilyIcons(
            leftBud = "drawable/ic_generic_bud_left.xml",
            rightBud = "drawable/ic_generic_bud_right.xml",
            case = "drawable/ic_generic_case.xml",
            headphone = "drawable/ic_generic_headphone.xml"
        )
    }
}
