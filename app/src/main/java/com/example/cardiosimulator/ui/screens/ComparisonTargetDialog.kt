package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.ComparisonTarget
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun ComparisonTargetDialog(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    onDismiss: () -> Unit,
    onTargetSelected: (ComparisonTarget) -> Unit
) {
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    var selectedPathology by remember { mutableStateOf<PathologyEntry?>(null) }
    var selectedLead by remember { mutableStateOf<Lead?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.monitor_compare),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(modifier = Modifier.weight(1f)) {
                    // Left side: Pathology selection
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.rhythm_selector_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(end = 8.dp)
                        ) {
                            items(rhythms) { rhythm ->
                                val title = if (currentLanguage == Language.RU)
                                    rhythm.nameRu ?: rhythm.titleEn
                                else
                                    rhythm.titleEn
                                
                                val isSelected = selectedPathology?.id == rhythm.id
                                Text(
                                    text = title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPathology = rhythm }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyLarge
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right side: Lead selection
                    Column(modifier = Modifier.weight(0.6f)) {
                        Text(
                            text = stringResource(R.string.constructor_lead_label, ""),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(Lead.entries) { lead ->
                                val isSelected = selectedLead == lead
                                Surface(
                                    onClick = { selectedLead = lead },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Box(
                                        contentAlignment = androidx.compose.ui.Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = lead.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.constructor_rename_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedPathology?.let { p ->
                                selectedLead?.let { l ->
                                    onTargetSelected(ComparisonTarget(p.id, l))
                                }
                            }
                        },
                        enabled = selectedPathology != null && selectedLead != null
                    ) {
                        Text(stringResource(R.string.constructor_rename_ok))
                    }
                }
            }
        }
    }
}
