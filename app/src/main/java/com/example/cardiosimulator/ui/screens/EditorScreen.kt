package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

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
    val dirtyLeads by editorViewModel.dirtyLeads.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Panel: Rhythm List
        Surface(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            RhythmChoosingPanel(
                appViewModel = appViewModel,
                rhythms = rhythms,
                selectedId = targetFile?.id,
                onRhythmSelect = { editorViewModel.selectPathology(it.id) }
            )
        }

        // Main Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                    
                    if (dirtyLeads.isNotEmpty()) {
                        Button(onClick = { editorViewModel.save() }) {
                            Text("Save")
                        }
                        OutlinedButton(onClick = { editorViewModel.revertLead(focusedLead) }) {
                            Text("Revert Lead")
                        }
                    }
                }
            }

            // Lead Tabs
            ScrollableTabRow(
                selectedTabIndex = Lead.entries.indexOf(focusedLead),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Lead.entries.forEach { lead ->
                    Tab(
                        selected = focusedLead == lead,
                        onClick = { editorViewModel.selectLead(lead) },
                        text = {
                            Text(
                                text = lead.name,
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
                    
                    Monitor(monitorViewModel = monitorViewModel) { _, _ ->
                        if (stream != null) {
                            EditableLead(
                                stream = stream,
                                baseline = baseline,
                                onSampleChanged = { i, v -> 
                                    editorViewModel.setSample(focusedLead, i, v)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Lead ${focusedLead.name} not present in file.")
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a pathology from the left panel to edit.")
                    }
                }
            }
        }
    }
}
