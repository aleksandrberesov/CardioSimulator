package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.ComparisonTarget
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun TeachingScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val courses by appViewModel.courses.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val comparisonWaveforms by rhythmViewModel.comparisonWaveforms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }
    var editingPaneIndex by remember { mutableStateOf<Int?>(null) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    val mode by monitorViewModel.monitorMode.collectAsState()

    LaunchedEffect(mode.comparisonTargets) {
        mode.comparisonTargets.forEach { (index, target) ->
            rhythmViewModel.loadComparisonWaveform(index, target.pathologyId, target.lead)
        }
    }

    LaunchedEffect(mode.isCompareMode) {
        if (mode.isCompareMode && mode.comparisonPresets.isNotEmpty() && mode.comparisonTargets.isEmpty()) {
            showPresetsDialog = true
        }
    }

    if (showPresetsDialog) {
        ComparisonPresetsDialog(
            monitorViewModel = monitorViewModel,
            onDismiss = { 
                showPresetsDialog = false
                if (mode.comparisonTargets.isEmpty()) monitorViewModel.toggleCompareMode()
            },
            onNewSchemaClick = { 
                showPresetsDialog = false
            },
            onPresetSelected = { preset ->
                monitorViewModel.applyPreset(preset)
                showPresetsDialog = false
            }
        )
    }

    if (showSavePresetDialog) {
        SaveComparisonPresetDialog(
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                monitorViewModel.saveCurrentAsPreset(name)
                showSavePresetDialog = false
            }
        )
    }

    editingPaneIndex?.let { index ->
        ComparisonTargetDialog(
            appViewModel = appViewModel,
            rhythms = rhythms,
            onDismiss = { editingPaneIndex = null },
            onTargetSelected = { target ->
                monitorViewModel.setComparisonTarget(index, target)
                editingPaneIndex = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().middleSectionCenter(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val displayTitle = if (mode.isCompareMode) {
                stringResource(R.string.monitor_compare)
            } else {
                selectedRhythm?.let {
                    if (selectedLanguage == Language.RU)
                        it.nameRu ?: it.titleEn
                    else
                        it.titleEn
                } ?: stringResource(R.string.constructor_no_pathology_selected)
            }

            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    if (mode.isCompareMode && mode.comparisonTargets.isNotEmpty()) {
                        TextButton(onClick = { showSavePresetDialog = true }) {
                            Text(stringResource(R.string.constructor_save))
                        }
                    }
                }
            }

            Monitor(
                modifier = Modifier.weight(1f).padding(top = 8.dp, start = 24.dp),
                monitorViewModel = monitorViewModel,
            ) { rows, columns, xOffset, scheme ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count,
                ) { index, lead ->
                    if (mode.isCompareMode && index >= 2 && !mode.comparisonTargets.containsKey(index)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray.copy(alpha = 0.5f))
                                .clickable { editingPaneIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.monitor_compare_placeholder),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.DarkGray
                            )
                        }
                    } else {
                        val (displayPoints, displayTitle) = if (mode.isCompareMode) {
                            if (index < 2) {
                                val comparisonLead = if (index == 0) Lead.I else Lead.II
                                val points = waveforms[comparisonLead] ?: Points(emptyList())
                                val mainPathTitle = if (selectedLanguage == Language.RU) selectedRhythm?.nameRu ?: selectedRhythm?.titleEn else selectedRhythm?.titleEn
                                val title = "${mainPathTitle ?: "???"} (${comparisonLead.name})"
                                points to title
                            } else {
                                val target = mode.comparisonTargets[index]
                                val points = comparisonWaveforms[index] ?: Points(emptyList())
                                val title = if (target != null) {
                                    val pathology = rhythms.find { it.id == target.pathologyId }
                                    val pathTitle = if (selectedLanguage == Language.RU) pathology?.nameRu ?: pathology?.titleEn else pathology?.titleEn
                                    "${pathTitle ?: "???"} (${target.lead.name})"
                                } else {
                                    "" // Should not happen due to the placeholder box above
                                }
                                points to title
                            }
                        } else {
                            val points = lead?.let { waveforms[it] }
                                ?.takeIf { it.values.size >= 2 }
                                ?: Points(emptyList<Float>())
                            points to (lead?.name ?: "")
                        }

                        LeadView(
                            points = displayPoints,
                            title = displayTitle,
                            isRunning = mode.isRunning,
                            xOffsetPx = xOffset,
                            gridScheme = scheme,
                            modifier = if (mode.isCompareMode && index >= 2) {
                                Modifier.clickable { editingPaneIndex = index }
                            } else Modifier
                        )
                    }
                }
            }
        }

        SideDrawer(
            isExpanded = isRhythmDrawerExpanded,
            onExpandedChange = { isRhythmDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    rhythms = rhythms,
                    courses = courses,
                    selectedId = selectedRhythm?.id,
                    onRhythmSelect = { rhythmViewModel.selectRhythm(it.id) },
                )
            },
            handlerContent = {
                Text(
                    text = stringResource(R.string.rhythm_drawer_title),
                    modifier = Modifier
                        .requiredWidth(64.dp)
                        .rotate(-90f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            },
            modifier = Modifier.fillMaxHeight().align(Alignment.TopStart)
        )
    }
}
