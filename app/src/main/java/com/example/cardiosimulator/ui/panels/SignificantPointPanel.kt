package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.utils.toDisplayString

/**
 * A side panel for marking significant ECG points on the selected sample.
 */
@Composable
fun SignificantPointPanel(
    significantPoints: List<SignificantPoint>,
    selectedIndex: Int?,
    sampleRate: Float,
    onPointToggle: (Int, EcgPointType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.constructor_significant_points),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            if (selectedIndex != null) {
                Text(
                    text = stringResource(R.string.constructor_sample_label, selectedIndex),
                    style = MaterialTheme.typography.bodySmall
                )

                val waves = listOf(
                    stringResource(R.string.constructor_p_wave) to listOf(EcgPointType.P_START, EcgPointType.P_PEAK, EcgPointType.P_END),
                    stringResource(R.string.constructor_qrs_complex) to listOf(EcgPointType.QRS_START, EcgPointType.Q_PEAK, EcgPointType.R_PEAK, EcgPointType.S_PEAK, EcgPointType.QRS_END),
                    stringResource(R.string.constructor_t_wave) to listOf(EcgPointType.T_START, EcgPointType.T_PEAK, EcgPointType.T_END)
                )

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    waves.forEach { (title, points) ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        points.forEach { type ->
                            val isSet = significantPoints.any { it.index == selectedIndex && it.type == type }
                            FilterChip(
                                selected = isSet,
                                onClick = { onPointToggle(selectedIndex, type) },
                                label = {
                                    Text(
                                        text = type.toDisplayString(),
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.constructor_select_point_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            val rPeaks = significantPoints.filter { it.type == EcgPointType.R_PEAK }.sortedBy { it.index }
            if (rPeaks.size >= 2) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.constructor_rhythms_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rPeaks.windowed(2).forEach { (r1, r2) ->
                    val durationS = (r2.index - r1.index).toFloat() / sampleRate
                    Text(
                        text = stringResource(R.string.ecg_rr_value_format, durationS),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}
