package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.domain.Lead

internal val LEAD_ORDER = listOf(
    Lead.I, Lead.II, Lead.III,
    Lead.aVR, Lead.aVL, Lead.aVF,
    Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6,
)

@Composable
fun ColumnScope.LeadsGrid(
    rows: Int,
    columns: Int,
    itemCount: Int,
    leadOrder: List<Lead> = LEAD_ORDER,
    content: @Composable BoxScope.(index: Int, lead: Lead?) -> Unit
) {
    repeat(rows) { rowIndex ->
        Row(modifier = Modifier.weight(1f)) {
            repeat(columns) { colIndex ->
                val itemIndex = colIndex * rows + rowIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (itemIndex < itemCount) {
                        val lead = leadOrder.getOrNull(itemIndex)
                        content(itemIndex, lead)
                    }
                }
            }
        }
    }
}
