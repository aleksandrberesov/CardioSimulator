package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.EcgSeries
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun EditorScreen(viewModel: AppViewModel) {
    val allSeries by viewModel.allSeries.collectAsState()
    var selectedSeries by remember { mutableStateOf<EcgSeries?>(null) }

    Row(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        // Left Panel: Series List
        Box(
            modifier = Modifier.weight(1.5f).middleSectionLeft(),
        ) {
            Column {
                Text(
                    text = "ECG Series",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(allSeries) { series ->
                        SeriesItem(
                            series = series,
                            isSelected = selectedSeries?.identy == series.identy,
                            onClick = { selectedSeries = series }
                        )
                    }
                }
            }
        }

        // Center Panel: Series Details and Parts
        Box(
            modifier = Modifier.weight(3.5f).middleSectionCenter(),
            contentAlignment = Alignment.TopStart
        ) {
            if (selectedSeries != null) {
                SeriesDetailView(series = selectedSeries!!)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a series to inspect")
                }
            }
        }
    }
}

@Composable
fun SeriesItem(series: EcgSeries, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(
            text = series.displayName.ifBlank { series.title },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        Text(
            text = "Id: ${series.identy}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun SeriesDetailView(series: EcgSeries) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Details: ${series.title}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Identity: ${series.identy}")
        Text(text = "Lead: ${series.lead}")
        Text(text = "Pathology: ${series.pathology ?: "None"}")

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Parts (Refs):", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        LazyColumn {
            items(series.partRefs) { ref ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = ref.partIdenty)
                    Text(text = "Offset: ${ref.offset}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
