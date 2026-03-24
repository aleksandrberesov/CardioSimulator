package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.utils.padWithFiveSpaces

@Composable
fun SeriesControlPanel(
    modifier: Modifier = Modifier,
    onTab1Click: () -> Unit = {},
    onTab2Click: () -> Unit = {},
    onTab3Click: () -> Unit = {},
    onTab4Click: () -> Unit = {},
    onTab5Click: () -> Unit = {},
    onRulerClick: () -> Unit = {},
    onPauseClick: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Tab(
            text = "4x",
            onClick = onTab1Click
        )
        Tab(
            text = "12x",
            onClick = onTab2Click
        )
        Tab(
            text = "Compare".padWithFiveSpaces(),
            onClick = onTab3Click
        )
        Tab(
            text = "25",
            subText = "mm/s",
            onClick = onTab4Click
        )
        Tab(
            text = "50",
            subText = "mm/s",
            onClick = onTab5Click
        )
        Tab(
            text = "Electrodes".padWithFiveSpaces(),
            onClick = onTab5Click
        )
        Tab(
            icon = Icons.Default.Straighten,
            iconModifier = Modifier.rotate(-45f),
            onClick = onRulerClick
        )
        Label(
            text = "EOS",
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.Red,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 24.sp
        )
        Tab(
            icon = Icons.Default.Pause,
            onClick = onPauseClick
        )
        Label(
            text = "HR 160",
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            backgroundColor = androidx.compose.ui.graphics.Color.Black,
            fontSize = 24.sp
        )
        Tab(
            text = "EMD/EBPA".padWithFiveSpaces(),
            onClick = onTab5Click
        )
        Tab(
            text = "Muscle".padWithFiveSpaces(),
            onClick = onTab5Click
        )
        Tab(
            icon = Icons.Default.Hub,
            onClick = onPauseClick,
            borderWidth = 0.dp
        )
    }
}
