package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R

@Composable
fun DrawPanel(
    onAutoDetect: () -> Unit,
    showAutoDetect: Boolean,
    hasGhostTrace: Boolean = false,
    onApplyGhostTrace: () -> Unit = {},
    onCancelGhostTrace: () -> Unit = {},
    onUndo: () -> Unit = {},
    canUndo: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(240.dp)
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
                text = stringResource(R.string.tool_mode_trace),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAutoDetect,
                    enabled = showAutoDetect
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(R.string.constructor_auto_detect),
                        tint = if (showAutoDetect) MaterialTheme.colorScheme.tertiary else LocalContentColor.current.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = onUndo,
                    enabled = canUndo
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = stringResource(R.string.constructor_undo)
                    )
                }
            }

            HorizontalDivider()

            if (hasGhostTrace) {
                Spacer(Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.constructor_apply_ghost_trace),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = onApplyGhostTrace) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_apply), tint = Color(0xFF2E7D32))
                            }
                            IconButton(onClick = onCancelGhostTrace) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
