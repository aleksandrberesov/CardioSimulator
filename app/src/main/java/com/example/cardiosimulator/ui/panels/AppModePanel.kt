package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.ui.components.Tab

@Composable
fun AppModePanel(
    modifier: Modifier = Modifier,
    onTab1Click: () -> Unit = {},
    onTab2Click: () -> Unit = {},
    onTab3Click: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.33f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Tab(
                modifier = Modifier.weight(1f),
                text = "Test",
                onClick = onTab1Click
            )
            Tab(
                modifier = Modifier.weight(1f),
                text = "Exam",
                onClick = onTab2Click
            )
            Tab(
                modifier = Modifier.weight(1f),
                text = "OSKE",
                onClick = onTab3Click
            )
        }
    }
}
