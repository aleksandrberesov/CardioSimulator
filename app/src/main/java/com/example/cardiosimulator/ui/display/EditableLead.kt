package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun EditableLead(
    points: Points,
    onPointsChange: (Points) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    val scale = LocalPixelScale.current

    Lead(
        points = points,
        title = title,
        modifier = modifier
            .pointerInput(points, scale) {
                detectDragGestures { change, _ ->
                    val x = change.position.x
                    val y = change.position.y

                    val stepX = scale.pxPerSample
                    val stepY = scale.pxPerAdcCount
                    val baselineY = size.height / 2f

                    val index = (x / stepX).toInt().coerceIn(points.values.indices)
                    val newValue = (baselineY - y) / stepY

                    val newList = points.values.toMutableList()
                    newList[index] = newValue
                    onPointsChange(Points(newList))
                }
            }
    )
}

@Preview(showBackground = true, widthDp = 600, heightDp = 150)
@Composable
fun EditableLeadPreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val previewScale = PixelScale(
        pxPerMm = 6.3f,
        paperSpeedMmPerSec = 25f,
        gainZoomY = 1f,
        cal = EcgCalibration(),
    )
    CardioSimulatorTheme {
        CompositionLocalProvider(LocalPixelScale provides previewScale) {
            Box(modifier = Modifier.ekgGrid()) {
                EditableLead(
                    points = samplePoints,
                    onPointsChange = {},
                    title = "I",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
