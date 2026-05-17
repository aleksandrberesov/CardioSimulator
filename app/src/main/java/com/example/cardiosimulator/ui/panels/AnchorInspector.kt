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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AnchorPoint
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

/**
 * Side panel for editing a single selected [AnchorPoint]. X/Y are in
 * source coordinates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorInspector(
    anchor: AnchorPoint?,
    onEditX: (Float) -> Unit,
    onEditY: (Float) -> Unit,
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
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Label(
                text = xRaw,
                modifier = Modifier.weight(1f),
                borderWidth = 1.dp,
                borderColor = Color.Black,
                cornerRadius = 4.dp
            )
            Label(
                text = yRaw,
                modifier = Modifier.weight(1f),
                borderWidth = 1.dp,
                borderColor = Color.Black,
                cornerRadius = 4.dp
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // +/- nudge buttons for X
            Tab(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = { onEditX(anchor.x - 1f); xRaw = (anchor.x - 1f).toString() },
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = { onEditX(anchor.x + 1f); xRaw = (anchor.x + 1f).toString() },
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = Icons.Default.ArrowUpward,
                onClick = { onEditY(anchor.y - 1f); yRaw = (anchor.y - 1f).toString() },
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = Icons.Default.ArrowDownward,
                onClick = { onEditY(anchor.y + 1f); yRaw = (anchor.y + 1f).toString() },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                text = stringResource(R.string.editor_anchor_insert_before),
                onClick = onInsertBefore,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = stringResource(R.string.editor_anchor_insert_after),
                onClick = onInsertAfter,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                text = stringResource(R.string.editor_anchor_center_y),
                onClick = onCenterY,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = stringResource(R.string.editor_anchor_delete),
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Tab(
            text = stringResource(R.string.editor_undo),
            onClick = onUndo,
            modifier = Modifier.fillMaxWidth().height(40.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AnchorInspectorSelectedPreview() {
    val sampleAnchor = AnchorPoint(
        x = 100f,
        y = 50f,
    )
    CardioSimulatorTheme {
        AnchorInspector(
            anchor = sampleAnchor,
            onEditX = {},
            onEditY = {},
            onInsertBefore = {},
            onInsertAfter = {},
            onDelete = {},
            onCenterY = {},
            onUndo = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AnchorInspectorEmptyPreview() {
    CardioSimulatorTheme {
        AnchorInspector(
            anchor = null,
            onEditX = {},
            onEditY = {},
            onInsertBefore = {},
            onInsertAfter = {},
            onDelete = {},
            onCenterY = {},
            onUndo = {}
        )
    }
}
