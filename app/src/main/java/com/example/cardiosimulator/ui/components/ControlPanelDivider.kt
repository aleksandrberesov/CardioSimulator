package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ControlPanelDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    VerticalDivider(
        modifier = modifier.padding(horizontal = 1.dp, vertical = 1.dp),
        color = color
    )
}
