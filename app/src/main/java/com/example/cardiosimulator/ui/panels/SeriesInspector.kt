package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.EditableSeries
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

/**
 * Property inspector for a single series: title / lead / pathology / params.
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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = series.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = series.pathology.orEmpty(),
            onValueChange = onPathologyChange,
            label = { Text("Pathology") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = series.params,
            onValueChange = onParamsChange,
            label = { Text("Params") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SeriesInspectorSelectedPreview() {
    val sampleSeries = EditableSeries(
        identy = "sample_id",
        title = "Sample Series Title",
        displayName = "Sample Display Name",
        lead = Lead.II,
        pathology = "Sinus Rhythm",
        params = "HR:60; RR:1000",
        aMax = 200,
        aValue = 2,
        partRefs = mutableListOf(),
        center = null,
        source = null,
        fileName = "sample.ecg"
    )
    CardioSimulatorTheme {
        SeriesInspector(
            series = sampleSeries,
            onTitleChange = {},
            onDisplayNameChange = {},
            onPathologyChange = {},
            onParamsChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SeriesInspectorEmptyPreview() {
    CardioSimulatorTheme {
        SeriesInspector(
            series = null,
            onTitleChange = {},
            onDisplayNameChange = {},
            onPathologyChange = {},
            onParamsChange = {}
        )
    }
}
