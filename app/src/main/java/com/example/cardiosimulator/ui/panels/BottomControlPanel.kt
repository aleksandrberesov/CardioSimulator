package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun BottomControlPanel(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 0.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            ControlPanelDivider()
            Tab(
                icon = Icons.Default.Settings,
                iconContentDescription = stringResource(R.string.cd_settings),
                onClick = onSettingsClick,
                //borderWidth = 0.dp
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 100)
@Composable
fun BottomControlPanelPreview() {
    CardioSimulatorTheme {
        BottomControlPanel()
    }
}
