package com.example.cardiosimulator.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.EcgPointType

@Composable
fun EcgPointType.toDisplayString(): String {
    val resId = when (this) {
        EcgPointType.P_START -> R.string.ecg_point_p_start
        EcgPointType.P_PEAK -> R.string.ecg_point_p_peak
        EcgPointType.P_END -> R.string.ecg_point_p_end
        EcgPointType.Q_PEAK -> R.string.ecg_point_q_peak
        EcgPointType.R_PEAK -> R.string.ecg_point_r_peak
        EcgPointType.S_PEAK -> R.string.ecg_point_s_peak
        EcgPointType.QRS_START -> R.string.ecg_point_qrs_start
        EcgPointType.QRS_END -> R.string.ecg_point_qrs_end
        EcgPointType.T_START -> R.string.ecg_point_t_start
        EcgPointType.T_PEAK -> R.string.ecg_point_t_peak
        EcgPointType.T_END -> R.string.ecg_point_t_end
    }
    return stringResource(resId)
}
