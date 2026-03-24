package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.ui.screens.verticalScrollbar

@Composable
fun RhythmChoosingPanel(
    modifier: Modifier = Modifier,
    onRhythmSelect: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val rhythms = listOf(
        "Sinus Rhythm", "Sinus Tachycardia", "Sinus Bradycardia", "Sinus Arrhythmia",
        "Atrial Fibrillation", "Atrial Flutter", "SVT", "Atrial Tachycardia",
        "Ventricular Tachycardia", "Ventricular Fibrillation", "Asystole", "PEA",
        "Junctional Rhythm", "Idioventricular Rhythm", "First Degree AV Block",
        "Mobitz I (Wenckebach)", "Mobitz II", "Third Degree AV Block"
    )
    val heartIssues = listOf(
        "Heart Failure", "Heart Attack", "Heart Valve Disease"
    )

    val rhythmsListState = rememberLazyListState()
    val heartIssuesListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Search (weight 1)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search rhythm...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true
            )
        }

        // Row 2: Rhythms List (weight 5)
        Row(
            modifier = Modifier.fillMaxWidth().weight(5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyColumn(
                state = rhythmsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScrollbar(rhythmsListState)
            ) {
                items(rhythms.filter { it.contains(searchQuery, ignoreCase = true) }) { rhythm ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRhythmSelect(rhythm) }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = rhythm,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        // Row 3: Heart Issues List (weight 5)
        Row(
            modifier = Modifier.fillMaxWidth().weight(5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyColumn(
                state = heartIssuesListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScrollbar(heartIssuesListState)
            ) {
                items(heartIssues.filter { it.contains(searchQuery, ignoreCase = true) }) { heartIssue ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRhythmSelect(heartIssue) }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = heartIssue,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
