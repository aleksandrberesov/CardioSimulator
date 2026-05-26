package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmChoosingDrawer
import com.example.cardiosimulator.ui.panels.SignificantPointsDrawer
import com.example.cardiosimulator.ui.utils.toDisplayString
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
                text = stringResource(R.string.editor_significant_points),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            if (selectedIndex != null) {
                Text(
                    text = stringResource(R.string.editor_sample_label, selectedIndex),
                    style = MaterialTheme.typography.bodySmall
                )

                // Group points by wave type for better organization
                val waves = listOf(
                    stringResource(R.string.editor_p_wave) to listOf(EcgPointType.P_START, EcgPointType.P_PEAK, EcgPointType.P_END),
                    stringResource(R.string.editor_qrs_complex) to listOf(EcgPointType.QRS_START, EcgPointType.Q_PEAK, EcgPointType.R_PEAK, EcgPointType.S_PEAK, EcgPointType.QRS_END),
                    stringResource(R.string.editor_t_wave) to listOf(EcgPointType.T_START, EcgPointType.T_PEAK, EcgPointType.T_END)
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
                        text = stringResource(R.string.editor_select_point_hint),
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
                    text = stringResource(R.string.editor_rhythms_title), // Use existing "Rhythms" title or new one?
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rPeaks.windowed(2).forEach { (r1, r2) ->
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
 * Rebuilt Editor on the unified rendering pipeline.
 * Edits raw ADC samples directly.
 */
@Composable
fun EditorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    editorViewModel: EditorViewModel,
) {
    val targetFile by editorViewModel.targetFile
    val focusedLead by editorViewModel.focusedLead.collectAsState()
    val selectedIndex by editorViewModel.selectedIndex.collectAsState()
    val dirtyLeads by editorViewModel.dirtyLeads.collectAsState()
    val isMetadataDirty by editorViewModel.isMetadataDirty.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val monitorMode by monitorViewModel.monitorMode.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showCalculateConfirmDialog by remember { mutableStateOf(false) }
    val rhythmListState = rememberLazyListState()

    var referenceImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    referenceImage = bitmap?.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showCalculateConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCalculateConfirmDialog = false },
            title = { Text(stringResource(R.string.editor_generate_derived)) },
            text = { Text(stringResource(R.string.editor_calculate_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    editorViewModel.calculateDerivedLeads()
                    showCalculateConfirmDialog = false
                }) {
                    Text(stringResource(R.string.editor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalculateConfirmDialog = false }) {
                    Text(stringResource(R.string.editor_rename_cancel))
                }
            }
        )
    }

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
                    } ?: stringResource(R.string.editor_no_pathology_selected)
                    
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    // Playback controls
                    IconButton(onClick = { monitorViewModel.setIsRunning(!monitorMode.isRunning) }) {
                        Icon(
                            imageVector = if (monitorMode.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (monitorMode.isRunning) stringResource(R.string.cd_stop) else stringResource(R.string.cd_start)
                        )
                    }

                    if (targetFile != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_rename))
                        }

                        IconButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Icon(Icons.Default.Image, contentDescription = stringResource(R.string.editor_add_reference_image))
                        }

                        OutlinedButton(onClick = { showCalculateConfirmDialog = true }) {
                            Text(stringResource(R.string.editor_generate_derived))
                        }
                    }
                    
                    if (dirtyLeads.isNotEmpty() || isMetadataDirty) {
                        Button(onClick = { editorViewModel.save() }) {
                            Text(stringResource(R.string.editor_save))
                        }
                        if (dirtyLeads.isNotEmpty()) {
                            OutlinedButton(onClick = { editorViewModel.revertLead(focusedLead) }) {
                                Text(stringResource(R.string.editor_revert_lead_btn))
                            }
                        }
                    }
                }
            }

            // Lead Tabs
            SecondaryTabRow(
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
                        ) { _, _, scrollOffsetPx ->
                            if (stream != null) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        referenceImage?.let { img ->
                                            Image(
                                                bitmap = img,
                                                contentDescription = "Reference Image",
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
                                                contentScale = ContentScale.FillWidth,
                                                alpha = 0.5f
                                            )
                                        }
                                        EditableLead(
                                            stream = stream,
                                            significantPoints = file.significantPoints,
                                            baseline = baseline,
                                            selectedIndex = selectedIndex,
                                            onIndexSelected = { editorViewModel.selectIndex(it) },
                                            scrollOffsetPx = scrollOffsetPx,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

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
                                            isRunning = monitorMode.isRunning,
                                            scrollOffsetPx = scrollOffsetPx,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.editor_lead_not_present, focusedLead.name))
                                }
                            }
                        }

                        // Right Side Panel for marking points
                        SignificantPointPanel(
                            significantPoints = file.significantPoints,
                            selectedIndex = selectedIndex,
                            sampleRate = monitorMode.calibration.sampleRateHz,
                            onPointToggle = { idx, type -> 
                                editorViewModel.toggleSignificantPoint(focusedLead, idx, type) 
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.editor_select_from_panel_hint))
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
            listState = rhythmListState,
            modifier = Modifier.align(Alignment.CenterStart).offset(y = (-40).dp)
        )

        // Left Panel: Significant Points List (Drawer)
        SignificantPointsDrawer(
            editorViewModel = editorViewModel,
            sampleRateHz = monitorMode.calibration.sampleRateHz,
            modifier = Modifier.align(Alignment.CenterStart).offset(y = 40.dp)
        )
    }
}
