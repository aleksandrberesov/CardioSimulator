package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.CourseBlock

/**
 * Basic GFM table renderer.
 *
 * Current implementation:
 * - Splits by '|' and '\n'.
 * - Skips alignment separator lines (`|---|`).
 * - If [CourseBlock.EditableTable.editable] is true, renders [OutlinedTextField]s.
 * - Otherwise renders plain [Text].
 */
@Composable
fun EditableTable(
    block: CourseBlock.EditableTable,
    modifier: Modifier = Modifier,
    onValueChange: (Int, Int, String) -> Unit = { _, _, _ -> }
) {
    val rows = remember(block.raw) {
        block.raw.lines()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith('|') && !trimmed.contains("---")
            }
            .map { line ->
                line.trim('|').split('|').map { it.trim() }
            }
    }

    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        rows.forEachIndexed { rowIndex, cells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                cells.forEachIndexed { colIndex, cell ->
                    val isHeader = rowIndex == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .padding(8.dp)
                    ) {
                        if (block.editable && !isHeader) {
                            OutlinedTextField(
                                value = cell,
                                onValueChange = { onValueChange(rowIndex, colIndex, it) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = cell,
                                style = if (isHeader) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                else MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Box(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        content()
    }
}
