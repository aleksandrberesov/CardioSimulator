package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.cardiosimulator.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmChoosingDrawer
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

/**
 * A side panel for marking significant ECG points on the selected sample.
 */
@Composable
fun SignificantPointPanel(
    significantPoints: List<SignificantPoint>,
    selectedIndex: Int?,
    onPointToggle: (Int, EcgPointType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(120.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Significant Points",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            if (selectedIndex != null) {
                Text(
                    text = "Sample: $selectedIndex",
                    style = MaterialTheme.typography.bodySmall
                )

                // Group points by wave type for better organization
                val waves = listOf(
                    "P Wave" to listOf(EcgPointType.P_START, EcgPointType.P_PEAK, EcgPointType.P_END),
                    "QRS Complex" to listOf(EcgPointType.QRS_START, EcgPointType.Q_PEAK, EcgPointType.R_PEAK, EcgPointType.S_PEAK, EcgPointType.QRS_END),
                    "T Wave" to listOf(EcgPointType.T_START, EcgPointType.T_PEAK, EcgPointType.T_END)
                )

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    waves.forEach { (title, points) ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        points.forEach { type ->
                            val isSet = significantPoints.any { it.index == selectedIndex && it.type == type }
                            FilterChip(
                                selected = isSet,
                                onClick = { onPointToggle(selectedIndex, type) },
                                label = {
                                    Text(
                                        text = type.name.replace("_", " "),
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Select a point on the chart to mark it",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Rebuilt Editor on the unified rendering pipeline.
 * Edits raw ADC samples directly.
 */
@Composable
fun EditorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel(),
    editorViewModel: EditorViewModel = viewModel(),
) {
    val targetFile by editorViewModel.targetFile
    val focusedLead by editorViewModel.focusedLead.collectAsState()
    val selectedIndex by editorViewModel.selectedIndex.collectAsState()
    val dirtyLeads by editorViewModel.dirtyLeads.collectAsState()
    val isMetadataDirty by editorViewModel.isMetadataDirty.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog && targetFile != null) {
        var newName by remember {
            mutableStateOf(
                if (selectedLanguage == com.example.cardiosimulator.domain.Language.RU)
                    targetFile?.nameRu ?: ""
                else
                    targetFile?.titleEn ?: ""
            )
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.editor_rename_title)) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.editor_rename_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editorViewModel.rename(newName, selectedLanguage)
                    showRenameDialog = false
                }) {
                    Text(stringResource(R.string.editor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.editor_rename_cancel))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Area
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val displayTitle = targetFile?.let {
                        if (selectedLanguage == com.example.cardiosimulator.domain.Language.RU) 
                            it.nameRu ?: it.titleEn 
                        else 
                            it.titleEn
                    } ?: "No pathology selected"
                    
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    if (targetFile != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                    }
                    
                    if (dirtyLeads.isNotEmpty() || isMetadataDirty) {
                        Button(onClick = { editorViewModel.save() }) {
                            Text("Save")
                        }
                        if (dirtyLeads.isNotEmpty()) {
                            OutlinedButton(onClick = { editorViewModel.revertLead(focusedLead) }) {
                                Text("Revert Lead")
                            }
                        }
                    }
                }
            }

            // Lead Tabs
            TabRow(
                selectedTabIndex = Lead.entries.indexOf(focusedLead),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Lead.entries.forEach { lead ->
                    Tab(
                        selected = focusedLead == lead,
                        onClick = { editorViewModel.selectLead(lead) },
                        text = {
                            Text(
                                text = lead.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (dirtyLeads.contains(lead)) Color.Red else Color.Unspecified
                            )
                        }
                    )
                }
            }

            // Monitor / Editor Canvas
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val file = targetFile
                if (file != null) {
                    val stream = file.leads[focusedLead]
                    val baseline = rhythmViewModel.repository.manifest()?.baseline ?: 1024
                    
                    Row(modifier = Modifier.fillMaxSize()) {
                        Monitor(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 24.dp),
                            monitorViewModel = monitorViewModel
                        ) { _, _ ->
                            if (stream != null) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    EditableLead(
                                        stream = stream,
                                        baseline = baseline,
                                        selectedIndex = selectedIndex,
                                        onIndexSelected = { editorViewModel.selectIndex(it) },
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Looping Preview at the bottom of the monitor
                                    val points = remember(stream, baseline) {
                                        Points(stream.samples.map { (it - baseline).toFloat() })
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        tonalElevation = 4.dp,
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        PreviewPane(
                                            points = points,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Lead ${focusedLead.name} not present in file.")
                                }
                            }
                        }

                        // Right Side Panel for marking points
                        SignificantPointPanel(
                            significantPoints = stream?.significantPoints ?: emptyList(),
                            selectedIndex = selectedIndex,
                            onPointToggle = { idx, type -> 
                                editorViewModel.toggleSignificantPoint(focusedLead, idx, type) 
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a pathology from the left panel to edit.")
                    }
                }
            }
        }

        // Left Panel: Rhythm List (Drawer)
        RhythmChoosingDrawer(
            appViewModel = appViewModel,
            rhythms = rhythms,
            selectedId = targetFile?.id,
            onRhythmSelect = { editorViewModel.selectPathology(it.id) },
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
