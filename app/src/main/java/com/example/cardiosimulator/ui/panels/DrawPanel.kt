package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R

@Composable
fun DrawPanel(
    onAutoDetect: () -> Unit,
    showAutoDetect: Boolean,
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
                text = stringResource(R.string.tool_mode_draw),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            if (showAutoDetect) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onAutoDetect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.constructor_auto_detect), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
