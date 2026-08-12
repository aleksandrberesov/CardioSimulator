package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Taxonomy
import com.example.cardiosimulator.domain.TaxonomyEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcronymPicker(
    selectedAcronyms: List<String>,
    onAcronymsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val suggestions = remember(text) {
        if (text.isBlank()) emptyList<TaxonomyEntry>()
        else Taxonomy.shared.allEntries.filter { 
            it.acronym.contains(text, ignoreCase = true) || 
            it.nameRu.contains(text, ignoreCase = true)
        }.take(10)
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.test_ctor_acronyms),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Selected chips
        if (selectedAcronyms.isEmpty()) {
            Text(
                text = stringResource(R.string.test_ctor_acronyms_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        } else {
            FlowRow(spacing = 8.dp) {
                selectedAcronyms.forEach { acr ->
                    val entry = Taxonomy.shared.find(acr)
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(text = acr, fontWeight = FontWeight.Bold)
                            if (entry != null) {
                                Text(
                                    text = " (${entry.nameRu})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onAcronymsChange(selectedAcronyms - acr) }
                            )
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box {
            OutlinedTextField(
                value = text,
                onValueChange = { 
                    text = it
                    expanded = it.isNotBlank()
                },
                placeholder = { Text(stringResource(R.string.test_ctor_acronyms_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = ""; expanded = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            )
            
            if (expanded && suggestions.isNotEmpty()) {
                Popup(
                    alignment = Alignment.BottomStart,
                    onDismissRequest = { expanded = false }
                ) {
                    Surface(
                        modifier = Modifier
                            .width(300.dp)
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            suggestions.forEach { entry ->
                                val isAlreadyAdded = selectedAcronyms.contains(entry.acronym)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAlreadyAdded) {
                                            onAcronymsChange((selectedAcronyms + entry.acronym).distinct())
                                            text = ""
                                            expanded = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.acronym,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
                                        )
                                        Text(
                                            text = entry.nameRu,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else Color.Unspecified
                                        )
                                    }
                                    if (isAlreadyAdded) {
                                        Text(
                                            text = stringResource(R.string.test_gen_dup),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simplified FlowRow copy from QuickTestScreen.kt to avoid dependencies.
 */
@Composable
private fun FlowRow(
    spacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + spacingPx
        }
        rows.add(currentRow)
        
        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * spacingPx
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + spacingPx
                }
                y += rowHeight + spacingPx
            }
        }
    }
}
