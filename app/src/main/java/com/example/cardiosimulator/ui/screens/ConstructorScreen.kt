package com.example.cardiosimulator.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
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
import com.example.cardiosimulator.ui.utils.TraceExtractor
import com.example.cardiosimulator.ui.utils.toDisplayString
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.ToolMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                                        textAlign = TextAlign.Center
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
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            val rPeaks = significantPoints.filter { it.type == EcgPointType.R_PEAK }.sortedBy { it.index }
            if (rPeaks.size >= 2) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.constructor_rhythms_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rPeaks.windowed(2).forEach { (r1, r2) ->
                    val durationS = (r2.index - r1.index).toFloat() / sampleRate
                    Text(
                        text = stringResource(R.string.ecg_rr_value_format, durationS),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
fun ToolModeSwitcher(
    currentMode: ToolMode,
    onModeChange: (ToolMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ToolMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ToolMode.entries.size),
                label = {
                    Text(
                        text = when (mode) {
                            ToolMode.Select -> stringResource(R.string.tool_mode_select)
                            ToolMode.Trace -> stringResource(R.string.tool_mode_trace)
                            ToolMode.Position -> stringResource(R.string.tool_mode_position)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
fun ImagePositionPanel(
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    isLocked: Boolean,
    onLockToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(200.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.image_panel_title), style = MaterialTheme.typography.labelLarge)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.image_panel_opacity), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = alpha,
                    onValueChange = onAlphaChange,
                    modifier = Modifier.width(100.dp)
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.image_panel_lock), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = isLocked, onCheckedChange = onLockToggle)
            }
            
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLocked
            ) {
                Text(stringResource(R.string.image_panel_reset))
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
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    constructorViewModel: ConstructorViewModel,
) {
    val targetFile by constructorViewModel.targetFile
    val focusedLead by constructorViewModel.focusedLead.collectAsState()
    val selectedIndex by constructorViewModel.selectedIndex.collectAsState()
    val dirtyLeads by constructorViewModel.dirtyLeads.collectAsState()
    val isMetadataDirty by constructorViewModel.isMetadataDirty.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val referenceImageUri by constructorViewModel.referenceImageUri.collectAsState()
    val toolMode by constructorViewModel.toolMode.collectAsState()
    val imageOffset by constructorViewModel.imageOffset.collectAsState()
    val imageScale by constructorViewModel.imageScale.collectAsState()
    val imageRotationDeg by constructorViewModel.imageRotationDeg.collectAsState()
    val imageAlpha by constructorViewModel.imageAlpha.collectAsState()
    val imageLocked by constructorViewModel.imageLocked.collectAsState()
    val ghostTrace by constructorViewModel.ghostTrace.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        constructorViewModel.setReferenceImageUri(uri)
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showCalculateDerivedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.constructor_delete_confirm_title)) },
            text = { Text(stringResource(R.string.constructor_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    constructorViewModel.deleteCurrentPathology()
                    showDeleteConfirmDialog = false
                }) {
                    Text(stringResource(R.string.constructor_anchor_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showCalculateDerivedDialog) {
        AlertDialog(
            onDismissRequest = { showCalculateDerivedDialog = false },
            title = { Text(stringResource(R.string.constructor_calculate_derived_confirm_title)) },
            text = { 
                Text(
                    stringResource(R.string.constructor_calculate_derived_confirm_message) + 
                    stringResource(R.string.constructor_calculate_derived_formulas)
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    constructorViewModel.calculateDerivedLeads()
                    showCalculateDerivedDialog = false
                }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalculateDerivedDialog = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
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
    val rhythmListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
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
                    } ?: stringResource(R.string.constructor_no_pathology_selected)
                    
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    if (referenceImageUri != null) {
                        ToolModeSwitcher(
                            currentMode = toolMode,
                            onModeChange = { constructorViewModel.setToolMode(it) }
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { constructorViewModel.undo(focusedLead) }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.constructor_undo))
                            }
                            IconButton(onClick = { constructorViewModel.redo(focusedLead) }) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.cd_redo))
                            }
                        }
                    }

                    if (targetFile != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_rename))
                        }

                        IconButton(onClick = { constructorViewModel.duplicateCurrentPathology() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.cd_copy))
                        }

                        IconButton(onClick = { launcher.launch("image/*") }) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = stringResource(R.string.cd_load_reference)
                            )
                        }

                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.constructor_anchor_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        OutlinedButton(onClick = { showCalculateDerivedDialog = true }) {
                            Text(stringResource(R.string.constructor_generate_derived))
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
                        Box(modifier = Modifier.weight(1f)) {
                            Monitor(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 24.dp),
                                monitorViewModel = monitorViewModel,
                                staticGrid = true,
                                showGridBackground = referenceImageUri == null,
                                gesturesEnabled = toolMode == ToolMode.Select
                            ) { _, _, xOffset, scheme ->
                                if (stream != null) {
                                    val isEditable = constructorViewModel.isLeadEditable(focusedLead)
                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                        val viewWidthPx = constraints.maxWidth.toFloat()
                                        val viewHeightPx = constraints.maxHeight.toFloat()
                                        val scale = LocalPixelScale.current

                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Spacer(modifier = Modifier.height(132.dp))

                                            EditableLead(
                                                stream = stream,
                                                significantPoints = file.significantPoints,
                                                baseline = baseline,
                                                selectedIndex = selectedIndex,
                                                onIndexSelected = { constructorViewModel.selectIndex(it) },
                                                isEditable = isEditable,
                                                modifier = Modifier.weight(1f),
                                                referenceImageUri = referenceImageUri,
                                                imageOffset = imageOffset,
                                                imageScale = imageScale,
                                                imageRotationDeg = imageRotationDeg,
                                                imageAlpha = imageAlpha,
                                                toolMode = toolMode,
                                                onImageTransform = { offset, s, r ->
                                                    constructorViewModel.setImageOffset(offset)
                                                    constructorViewModel.setImageScale(s)
                                                    constructorViewModel.setImageRotation(r)
                                                },
                                                onStrokeStart = { constructorViewModel.startStroke(focusedLead) },
                                                onTrace = { constructorViewModel.traceSamples(focusedLead, it) },
                                                ghostTrace = ghostTrace,
                                                onApplyGhostTrace = { constructorViewModel.applyGhostTrace() },
                                                onCancelGhostTrace = { constructorViewModel.setGhostTrace(null) }
                                            )

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
                                                    modifier = Modifier.fillMaxSize(),
                                                    isRunning = monitorMode.isRunning,
                                                    externalXOffsetPx = xOffset,
                                                    gridScheme = scheme
                                                )
                                            }
                                        }
                                        
                                        // Auto-detect button overlay
                                        if (referenceImageUri != null && toolMode == ToolMode.Trace && ghostTrace == null) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val bitmap = withContext(Dispatchers.IO) {
                                                            context.contentResolver.openInputStream(referenceImageUri!!)?.use {
                                                                BitmapFactory.decodeStream(it)
                                                            }
                                                        }
                                                        if (bitmap != null) {
                                                            val extracted = TraceExtractor.extract(
                                                                bitmap = bitmap,
                                                                sampleCount = stream.samples.size,
                                                                baseline = baseline,
                                                                stepX = scale.pxPerSample,
                                                                stepY = scale.pxPerAdcCount,
                                                                imageOffset = imageOffset,
                                                                imageScale = imageScale,
                                                                imageRotationDeg = imageRotationDeg,
                                                                viewWidth = viewWidthPx,
                                                                viewHeight = viewHeightPx
                                                            )
                                                            constructorViewModel.setGhostTrace(extracted)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(16.dp)
                                                    .offset(y = (-132).dp) // Above preview pane
                                            ) {
                                                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.constructor_auto_detect))
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.constructor_lead_not_present, focusedLead.name))
                                    }
                                }
                            }
                            
                            if (toolMode == ToolMode.Position && referenceImageUri != null) {
                                ImagePositionPanel(
                                    alpha = imageAlpha,
                                    onAlphaChange = { constructorViewModel.setImageAlpha(it) },
                                    isLocked = imageLocked,
                                    onLockToggle = { constructorViewModel.setImageLocked(it) },
                                    onReset = { constructorViewModel.resetImageTransform() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                )
                            }
                        }

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

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.TopStart)
                ) {
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
                                listState = rhythmListState,
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
