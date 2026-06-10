package com.example.cardiosimulator.ui.panels

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R

@Composable
fun ReferenceImagePanel(
    referenceImageUri: Uri?,
    onLoadImage: () -> Unit,
    imageAlpha: Float,
    onAlphaChange: (Float) -> Unit,
    imageLocked: Boolean,
    onLockToggle: (Boolean) -> Unit,
    onResetImage: () -> Unit,
    onAutoDetect: () -> Unit,
    showAutoDetect: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(180.dp)
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
                text = stringResource(R.string.image_panel_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Button(
                onClick = onLoadImage,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.constructor_load_reference),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (referenceImageUri != null) {
                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.image_panel_opacity),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = imageAlpha,
                        onValueChange = onAlphaChange,
                        modifier = Modifier.width(80.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.image_panel_lock),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(
                        checked = imageLocked,
                        onCheckedChange = onLockToggle,
                        modifier = Modifier.scale(0.7f)
                    )
                }

                OutlinedButton(
                    onClick = onResetImage,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !imageLocked,
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(stringResource(R.string.image_panel_reset), style = MaterialTheme.typography.labelSmall)
                }

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
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.cd_load_reference),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
