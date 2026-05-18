package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AnchorClipboard
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun EditorLeftPanel(
    viewModel: AppViewModel,
    rhythmViewModel: RhythmViewModel,
    editorViewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val dirtyParts by viewModel.dirtyParts.collectAsState()
    val dirtySeries by viewModel.dirtySeries.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    
    val focusedLead by editorViewModel.focusedLead.collectAsState()
    val selectedPartIndex by editorViewModel.selectedPartIndex.collectAsState()
    val partFilter by editorViewModel.partFilter.collectAsState()
    val showPartsDropdown by editorViewModel.showPartsDropdown.collectAsState()
    val showSeriesDropdown by editorViewModel.showSeriesDropdown.collectAsState()

    val allParts by rhythmViewModel.allParts.collectAsState()
    val allSeries by rhythmViewModel.allSeries.collectAsState()

    val focusedSeriesId = selectedRhythm?.seriesIdentityByLead?.get(focusedLead)
    val focusedSeries = allSeries.find { it.identy == focusedSeriesId }
    val focusedParts = focusedSeries?.partRefs?.mapNotNull { ref ->
        allParts.find { it.identy == ref.partIdenty }
    } ?: emptyList()
    val focusedPart = selectedPartIndex?.let { focusedParts.getOrNull(it) }
    val focusedEditable = focusedPart?.let { viewModel.editablePart(it.identy) }

    Column(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { viewModel.saveAll() },
            enabled = dirtyParts.isNotEmpty() || dirtySeries.isNotEmpty(),
        ) {
            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.editor_save))
        }
        IconButton(
            onClick = {
                focusedEditable?.let {
                    viewModel.undoPart(it.identy)
                    editorViewModel.setSelectedAnchorIndex(null)
                }
            },
            enabled = focusedEditable != null,
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo))
        }
        IconButton(
            onClick = {
                focusedEditable?.let { AnchorClipboard.set(it.anchors.toList()) }
            },
            enabled = focusedEditable != null,
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
        }
        IconButton(
            onClick = {
                focusedEditable?.let { ep ->
                    if (AnchorClipboard.hasContent) {
                        viewModel.mutatePart(ep.identy) { p ->
                            p.anchors.clear()
                            p.anchors.addAll(AnchorClipboard.get())
                        }
                    }
                }
            },
            enabled = focusedEditable != null && AnchorClipboard.hasContent,
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
        }
        Box {
            IconButton(
                onClick = { editorViewModel.setShowPartsDropdown(true) },
                enabled = focusedParts.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Parts")
            }
            DropdownMenu(
                expanded = showPartsDropdown,
                onDismissRequest = { editorViewModel.setShowPartsDropdown(false) }
            ) {
                focusedParts
                    .filter {
                        partFilter.isBlank() ||
                                it.title.contains(partFilter, ignoreCase = true) ||
                                it.identy.contains(partFilter, ignoreCase = true)
                    }
                    .forEach { part ->
                        val realIndex = focusedParts.indexOf(part)
                        val isSelected = selectedPartIndex == realIndex
                        DropdownMenuItem(
                            text = { Text(part.title) },
                            onClick = {
                                editorViewModel.setSelectedPartIndex(realIndex)
                                editorViewModel.setShowPartsDropdown(false)
                            },
                            modifier = Modifier.background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                        )
                    }
            }
        }
        Box {
            IconButton(
                onClick = { editorViewModel.setShowSeriesDropdown(true) },
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Series")
            }
            DropdownMenu(
                expanded = showSeriesDropdown,
                onDismissRequest = { editorViewModel.setShowSeriesDropdown(false) }
            ) {
                Box(modifier = Modifier.width(300.dp).height(400.dp)) {
                    RhythmChoosingPanel(
                        rhythms = rhythms,
                        selectedPathology = selectedRhythm?.pathology,
                        currentLanguage = selectedLanguage,
                        onRhythmSelect = {
                            rhythmViewModel.selectRhythm(it.pathology)
                            editorViewModel.setShowSeriesDropdown(false)
                        },
                    )
                }
            }
        }
    }
}
