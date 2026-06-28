package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SynthesizerDialog(
    onDismiss: () -> Unit,
    onGenerate: (bpm: Int, ap: Double, ar: Double, `as`: Double, at: Double, variance: Double) -> Unit
) {
    var bpm by remember { mutableStateOf(60f) }
    var ap by remember { mutableStateOf(0.2f) }
    var ar by remember { mutableStateOf(0.7f) }
    var `as` by remember { mutableStateOf(0.2f) }
    var at by remember { mutableStateOf(0.15f) }
    var variance by remember { mutableStateOf(0.01f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dolinský ECG Synthesizer") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParamSlider(label = "Heart Rate (BPM)", value = bpm, range = 40f..180f, onValueChange = { bpm = it })
                ParamSlider(label = "P-wave Amplitude", value = ap, range = -0.1f..0.4f, onValueChange = { ap = it })
                ParamSlider(label = "R-wave Amplitude", value = ar, range = 0.5f..1.5f, onValueChange = { ar = it })
                ParamSlider(label = "S-wave Amplitude", value = `as`, range = 0.0f..0.8f, onValueChange = { `as` = it })
                ParamSlider(label = "T-wave Amplitude", value = at, range = -0.3f..0.8f, onValueChange = { at = it })
                ParamSlider(label = "Variability", value = variance, range = 0.0f..0.1f, onValueChange = { variance = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                onGenerate(bpm.roundToInt(), ap.toDouble(), ar.toDouble(), `as`.toDouble(), at.toDouble(), variance.toDouble())
                onDismiss()
            }) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = "%.2f".format(value), style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.height(32.dp)
        )
    }
}
