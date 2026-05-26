package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel

@Composable
fun SignificantPointsControlPanel(
    constructorViewModel: ConstructorViewModel,
    modifier: Modifier = Modifier
) {
    val targetFile by constructorViewModel.targetFile
    val selectedIndex by constructorViewModel.selectedIndex.collectAsState()
    val focusedLead by constructorViewModel.focusedLead.collectAsState()

    val pointTypes = listOf(
        EcgPointType.P_START, EcgPointType.P_PEAK, EcgPointType.P_END,
        EcgPointType.QRS_START, EcgPointType.Q_PEAK, EcgPointType.R_PEAK, EcgPointType.S_PEAK, EcgPointType.QRS_END,
        EcgPointType.T_START, EcgPointType.T_PEAK, EcgPointType.T_END
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(
            text = stringResource(R.string.constructor_significant_points),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp).weight(2f)
        )

        ControlPanelDivider()

        pointTypes.forEach { type ->
            val points = targetFile?.significantPoints?.filter { it.type == type } ?: emptyList()
            val isAtPoint = points.any { it.index == selectedIndex }
            val isPresent = points.isNotEmpty()
            
            // Strip HTML-like tags for the tab text
            val cleanLabel = type.label
                .replace("<sub>s</sub>", "s")
                .replace("<sub>e</sub>", "e")
                .replace("<sub>", "")
                .replace("</sub>", "")

            Tab(
                text = cleanLabel,
                onClick = { 
                    constructorViewModel.selectSignificantPoint(type)
                },
                backgroundColor = when {
                    isAtPoint -> MaterialTheme.colorScheme.primaryContainer
                    isPresent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
