package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.viewmodels.ToolMode

@Composable
fun ToolModePanel(
    currentMode: ToolMode,
    onModeChange: (ToolMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToolMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                IconButton(
                    onClick = { onModeChange(mode) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = when (mode) {
                            ToolMode.Select -> Icons.Default.AdsClick
                            ToolMode.Trace -> Icons.Default.Gesture
                            ToolMode.Position -> Icons.Default.OpenWith
                            ToolMode.Points -> Icons.Default.Place
                            ToolMode.Photo -> Icons.Default.Image
                            ToolMode.Pan -> Icons.Default.PanTool
                        },
                        contentDescription = when (mode) {
                            ToolMode.Select -> stringResource(R.string.tool_mode_select)
                            ToolMode.Trace -> stringResource(R.string.tool_mode_trace)
                            ToolMode.Position -> stringResource(R.string.tool_mode_position)
                            ToolMode.Points -> stringResource(R.string.constructor_significant_points)
                            ToolMode.Photo -> stringResource(R.string.image_panel_title)
                            ToolMode.Pan -> stringResource(R.string.tool_mode_pan)
                        }
                    )
                }
            }
        }
    }
}
