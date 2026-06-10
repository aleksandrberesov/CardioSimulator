package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R

@Composable
fun PositionPanel(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(200.dp)
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
                text = stringResource(R.string.tool_mode_position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            // Empty for now
        }
    }
}
