package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.cardiosimulator.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.panels.SignificantPointSelector
import com.example.cardiosimulator.ui.utils.toDisplayString
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

/**
 * A side panel for marking significant ECG points on the selected sample.
 */
@Composable
fun SignificantPointPanel(
    significantPoints: List<SignificantPoint>,
    selectedIndex: Int?,
    sampleRate: Float,
    onPointToggle: (Int, EcgPointType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(150.dp) // Slightly wider for intervals
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
                text = stringResource(R.string.constructor_significant_points),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            if (selectedIndex != null) {
                Text(
                    text = stringResource(R.string.constructor_sample_label, selectedIndex),
                    style = MaterialTheme.typography.bodySmall
                )

                // Group points by wave type for better organization
                val waves = listOf(
                    stringResource(R.string.constructor_p_wave) to listOf(EcgPointType.P_START, EcgPointType.P_PEAK, EcgPointType.P_END),
                    stringResource(R.string.constructor_qrs_complex) to listOf(EcgPointType.QRS_START, EcgPointType.Q_PEAK, EcgPointType.R_PEAK, EcgPointType.S_PEAK, EcgPointType.QRS_END),
                    stringResource(R.string.constructor_t_wave) to listOf(EcgPointType.T_START, EcgPointType.T_PEAK, EcgPointType.T_END)
                )

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
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
                                        text = type.toDisplayString(),
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
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.constructor_select_point_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Intervals section (Always visible if multiple R peaks exist)
            val rPeaks = significantPoints.filter { it.type == EcgPointType.R_PEAK }.sortedBy { it.index }
            if (rPeaks.size >= 2) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.constructor_rhythms_title), // Use existing "Rhythms" title or new one?
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rPeaks.windowed(2).forEachIndexed { index, (r1, r2) ->
                    val durationS = (r2.index - r1.index).toFloat() / sampleRate
                    Text(
                        text = stringResource(R.string.ecg_rr_value_format, durationS),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32) // Match the overlay color
                    )
                }
            }
        }
    }
}

/**
 * Rebuilt Constructor on the unified rendering pipeline.
 * Constructs raw ADC samples directly.
 */
@Composable
fun ConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel(),
    constructorViewModel: ConstructorViewModel = viewModel(),
) {
    val targetFile by constructorViewModel.targetFile
    val focusedLead by constructorViewModel.focusedLead.collectAsState()
    val selectedIndex by constructorViewModel.selectedIndex.collectAsState()
    val dirtyLeads by constructorViewModel.dirtyLeads.collectAsState()
    val isMetadataDirty by constructorViewModel.isMetadataDirty.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val monitorMode by monitorViewModel.monitorMode.collectAsState()

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
            title = { Text(stringResource(R.string.constructor_rename_title)) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.constructor_rename_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    constructorViewModel.rename(newName, selectedLanguage)
                    showRenameDialog = false
                }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }
    var isPointsDrawerExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Area
        Column(modifier = Modifier.fillMaxSize()) {
            // ... (rest of the Toolbar and Lead Tabs code)
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
                    } ?: stringResource(R.string.constructor_no_pathology_selected)
                    
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    if (targetFile != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_rename))
                        }
                    }
                    
                    if (dirtyLeads.isNotEmpty() || isMetadataDirty) {
                        Button(onClick = { constructorViewModel.save() }) {
                            Text(stringResource(R.string.constructor_save))
                        }
                        if (dirtyLeads.isNotEmpty()) {
                            OutlinedButton(onClick = { constructorViewModel.revertLead(focusedLead) }) {
                                Text(stringResource(R.string.constructor_revert_lead_btn))
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
                        onClick = { constructorViewModel.selectLead(lead) },
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
                                        significantPoints = file.significantPoints,
                                        baseline = baseline,
                                        selectedIndex = selectedIndex,
                                        onIndexSelected = { constructorViewModel.selectIndex(it) },
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
                                    Text(stringResource(R.string.constructor_lead_not_present, focusedLead.name))
                                }
                            }
                        }

                        // Right Side Panel for marking points
                        SignificantPointPanel(
                            significantPoints = file.significantPoints,
                            selectedIndex = selectedIndex,
                            sampleRate = monitorMode.calibration.sampleRateHz,
                            onPointToggle = { idx, type -> 
                                constructorViewModel.toggleSignificantPoint(focusedLead, idx, type) 
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.constructor_select_from_panel_hint))
                    }
                }

                // Side Drawers
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.TopStart)
                ) {
                    // Rhythm List (Drawer)
                    SideDrawer(
                        isExpanded = isRhythmDrawerExpanded,
                        onExpandedChange = { isRhythmDrawerExpanded = it },
                        drawerWidth = 300.dp,
                        drawerContent = {
                            RhythmSelector(
                                appViewModel = appViewModel,
                                rhythms = rhythms,
                                selectedId = targetFile?.id,
                                onRhythmSelect = { constructorViewModel.selectPathology(it.id) },
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
                        handlerModifier = Modifier.offset(y = (-40).dp),
                        modifier = Modifier.fillMaxHeight()
                    )

                    // Significant Points List (Drawer)
                    SideDrawer(
                        isExpanded = isPointsDrawerExpanded,
                        onExpandedChange = { isPointsDrawerExpanded = it },
                        handlerColor = MaterialTheme.colorScheme.secondaryContainer,
                        drawerContent = {
                            SignificantPointSelector(
                                points = targetFile?.significantPoints?.sortedBy { it.index } ?: emptyList(),
                                selectedIndex = selectedIndex,
                                sampleRateHz = monitorMode.calibration.sampleRateHz,
                                onPointSelect = { constructorViewModel.selectIndex(it.index) }
                            )
                        },
                        handlerContent = {
                            Text(
                                text = stringResource(R.string.points_drawer_title),
                                modifier = Modifier
                                    .requiredWidth(64.dp)
                                    .rotate(-90f),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        },
                        handlerModifier = Modifier.offset(y = 40.dp),
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}
