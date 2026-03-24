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
fun AppControlPanel(
    modifier: Modifier = Modifier,
    onTab1Click: () -> Unit = {},
    onTab2Click: () -> Unit = {},
    onTab3Click: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.25f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Tab(
                modifier = Modifier.weight(1f),
                text = "Teach",
                onClick = onTab1Click
            )
            Tab(
                modifier = Modifier.weight(1f),
                text = "Education",
                onClick = onTab2Click
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.33f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Tab(
                modifier = Modifier.weight(1f),
                text = "Self-education",
                onClick = onTab3Click
            )
            Tab(
                modifier = Modifier.weight(1f),
                text = "Tips",
                onClick = onTab3Click
            )
        }
    }
}
