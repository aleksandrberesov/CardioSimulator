package com.example.cardiosimulator.ui.panels

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    imageScale: Float,
    onScaleChange: (Float) -> Unit,
    imageRotation: Float,
    onRotationChange: (Float) -> Unit,
    imageLocked: Boolean,
    onLockToggle: (Boolean) -> Unit,
    onResetImage: () -> Unit,
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
                text = stringResource(R.string.image_panel_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onLoadImage) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = stringResource(R.string.constructor_load_reference)
                    )
                }

                IconButton(onClick = { onLockToggle(!imageLocked) }) {
                    Icon(
                        if (imageLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.image_panel_lock)
                    )
                }

                IconButton(onClick = onResetImage, enabled = !imageLocked) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.image_panel_reset)
                    )
                }
            }

            HorizontalDivider()

            if (referenceImageUri != null) {
                // Opacity Adjuster
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.image_panel_opacity),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Slider(
                        value = imageAlpha,
                        onValueChange = onAlphaChange,
                        modifier = Modifier.width(180.dp)
                    )
                }

                // Scale Adjuster
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.image_panel_scale),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Slider(
                        value = imageScale,
                        onValueChange = onScaleChange,
                        valueRange = 0.1f..5f,
                        modifier = Modifier.width(180.dp),
                        enabled = !imageLocked
                    )
                }

                // Rotation Adjuster
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.image_panel_rotation),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Slider(
                        value = imageRotation,
                        onValueChange = onRotationChange,
                        valueRange = -180f..180f,
                        modifier = Modifier.width(180.dp),
                        enabled = !imageLocked
                    )
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
