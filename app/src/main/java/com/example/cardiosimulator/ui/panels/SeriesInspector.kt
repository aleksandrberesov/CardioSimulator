package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.EditableSeries

/**
 * Property inspector for a single series. Mirrors RP5's Series property
 * editor on the Blocks tab: title / lead / pathology / params.
 */
@Composable
fun SeriesInspector(
    series: EditableSeries?,
    onTitleChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPathologyChange: (String) -> Unit,
    onParamsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text = "Series",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (series == null) {
            Text("Select a series to edit.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        OutlinedTextField(
            value = series.title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = series.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = series.pathology.orEmpty(),
            onValueChange = onPathologyChange,
            label = { Text("Pathology") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = series.params,
            onValueChange = onParamsChange,
            label = { Text("Params") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
