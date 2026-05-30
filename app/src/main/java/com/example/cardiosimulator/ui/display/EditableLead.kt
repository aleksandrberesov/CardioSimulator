package com.example.cardiosimulator.ui.display

import android.net.Uri
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.*
import com.example.cardiosimulator.ui.viewmodels.ToolMode

/**
 * A version of [Lead] that includes draggable handles for editing raw samples
 * and an overlay for marking significant ECG points.
 */
@Composable
fun EditableLead(
    stream: LeadStream,
    significantPoints: List<SignificantPoint>,
    baseline: Int,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onIndexSelected: ((Int) -> Unit)? = null,
    isEditable: Boolean = true,
    referenceImageUri: Uri? = null,
    imageOffset: Offset = Offset.Zero,
    imageScale: Float = 1f,
    imageRotationDeg: Float = 0f,
    imageAlpha: Float = 0.5f,
    toolMode: ToolMode = ToolMode.Select,
    onImageTransform: (Offset, Float, Float) -> Unit = { _, _, _ -> },
    onStrokeStart: () -> Unit = {},
    onTrace: (Map<Int, Int>) -> Unit = {},
    ghostTrace: IntArray? = null,
    onApplyGhostTrace: () -> Unit = {},
    onCancelGhostTrace: () -> Unit = {}
) {
    val points = Points(stream.samples.map { (it - baseline).toFloat() })
    val scale = LocalPixelScale.current
    val density = LocalDensity.current

    val waveformWidthDp = with(density) {
        (stream.samples.size * scale.pxPerSample).toDp()
    }

    Box(
        modifier = modifier
            .leadArea()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calibration Symbol
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
            ) {
                CalibrationPulse(modifier = Modifier.fillMaxSize())
            }

            Box(
                modifier = Modifier
                    .width(waveformWidthDp)
                    .fillMaxHeight()
            ) {
                // Photo layer
                referenceImageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                translationX = imageOffset.x,
                                translationY = imageOffset.y,
                                scaleX = imageScale,
                                scaleY = imageScale,
                                rotationZ = imageRotationDeg,
                                alpha = imageAlpha
                            )
                            .then(
                                if (toolMode == ToolMode.Position) {
                                    Modifier.transformable(
                                        state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
                                            onImageTransform(
                                                imageOffset + offsetChange,
                                                imageScale * zoomChange,
                                                imageRotationDeg + rotationChange
                                            )
                                        }
                                    )
                                } else Modifier
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                // Ghost trace layer (Auto-detect preview)
                ghostTrace?.let { trace ->
                    val ghostPoints = Points(trace.map { (it - baseline).toFloat() })
                    ChartCanvas(
                        points = ghostPoints,
                        color = Color.Magenta.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ChartCanvas(points = points, modifier = Modifier.fillMaxSize())

                SignificantPointOverlay(
                    points = points,
                    significantPoints = significantPoints,
                    modifier = Modifier.fillMaxSize()
                )

                if (toolMode == ToolMode.Select) {
                    SampleHandleOverlay(
                        samples = stream.samples,
                        baseline = baseline,
                        selectedIndex = selectedIndex,
                        onIndexSelected = onIndexSelected,
                        isEditable = isEditable,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (toolMode == ToolMode.Trace && isEditable) {
                    TraceOverlay(
                        sampleCount = stream.samples.size,
                        baseline = baseline,
                        onStrokeStart = onStrokeStart,
                        onTrace = onTrace,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        // Ghost trace controls
        if (ghostTrace != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.constructor_apply_ghost_trace), style = MaterialTheme.typography.labelMedium)
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
