package com.example.cardiosimulator.ui.display

import android.net.Uri
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    ghostTrace: IntArray? = null
) {
    val points = Points(stream.samples.map { (it - baseline).toFloat() })
    val scale = LocalPixelScale.current
    val density = LocalDensity.current

    val waveformWidthDp = with(density) {
        (stream.samples.size * scale.pxPerSample).toDp()
    }

    val waveformHeightDp = with(density) {
        (2048 * scale.pxPerAdcCount).toDp()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(waveformHeightDp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label Strip
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stream.lead.name,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = com.example.cardiosimulator.ui.theme.EcgTraceTeal,
                    textAlign = TextAlign.Center
                )
            }

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
                                if (toolMode == ToolMode.Photo) {
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
                        modifier = Modifier.fillMaxSize(),
                        strokeWidthDp = 2.5f
                    )
                }

                ChartCanvas(points = points, modifier = Modifier.fillMaxSize())

                SignificantPointOverlay(
                    points = points,
                    significantPoints = significantPoints,
                    modifier = Modifier.fillMaxSize()
                )

                if (toolMode == ToolMode.Select || toolMode == ToolMode.Points) {
                    SampleHandleOverlay(
                        samples = stream.samples,
                        baseline = baseline,
                        selectedIndex = selectedIndex,
                        onIndexSelected = onIndexSelected,
                        isEditable = isEditable && toolMode == ToolMode.Select,
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
    }
}
