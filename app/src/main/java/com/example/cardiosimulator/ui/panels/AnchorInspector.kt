package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AnchorPoint
import com.example.cardiosimulator.domain.EasingCurve

/**
 * Side panel for editing a single selected [AnchorPoint]. X/Y are in
 * source coordinates; the curve flag controls interpolation from the
 * preceding anchor. Buttons mirror RP5's Segments-tab actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorInspector(
    anchor: AnchorPoint?,
    onEditX: (Float) -> Unit,
    onEditY: (Float) -> Unit,
    onCurveChange: (EasingCurve) -> Unit,
    onInsertBefore: () -> Unit,
    onInsertAfter: () -> Unit,
    onDelete: () -> Unit,
    onCenterY: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = anchor == null
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text = "Anchor",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (empty) {
            Text("Select an anchor to edit.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        anchor!!
        var xRaw by remember(anchor) { mutableStateOf(anchor.x.toString()) }
        var yRaw by remember(anchor) { mutableStateOf(anchor.y.toString()) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = xRaw,
                onValueChange = { v ->
                    xRaw = v
                    v.toFloatOrNull()?.let(onEditX)
                },
                label = { Text(stringResource(R.string.editor_anchor_x)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = yRaw,
                onValueChange = { v ->
                    yRaw = v
                    v.toFloatOrNull()?.let(onEditY)
                },
                label = { Text(stringResource(R.string.editor_anchor_y)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // +/- nudge buttons for X
            OutlinedButton(onClick = {
                onEditX(anchor.x - 1f); xRaw = (anchor.x - 1f).toString()
            }) { Text("X-") }
            OutlinedButton(onClick = {
                onEditX(anchor.x + 1f); xRaw = (anchor.x + 1f).toString()
            }) { Text("X+") }
            OutlinedButton(onClick = {
                onEditY(anchor.y - 1f); yRaw = (anchor.y - 1f).toString()
            }) { Text("Y-") }
            OutlinedButton(onClick = {
                onEditY(anchor.y + 1f); yRaw = (anchor.y + 1f).toString()
            }) { Text("Y+") }
        }
        Spacer(Modifier.height(8.dp))
        // Curve dropdown
        var expanded by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${stringResource(R.string.editor_anchor_curve)}: ${anchor.curve.name.lowercase()}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EasingCurve.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name.lowercase()) },
                    onClick = { onCurveChange(c); expanded = false },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(onClick = onInsertBefore) {
                Text(stringResource(R.string.editor_anchor_insert_before))
            }
            OutlinedButton(onClick = onInsertAfter) {
                Text(stringResource(R.string.editor_anchor_insert_after))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(onClick = onCenterY) {
                Text(stringResource(R.string.editor_anchor_center_y))
            }
            OutlinedButton(onClick = onDelete) {
                Text(stringResource(R.string.editor_anchor_delete))
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.editor_undo))
        }
    }
}
