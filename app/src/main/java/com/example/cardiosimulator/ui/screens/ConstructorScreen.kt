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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.display.ekgGrid
import com.example.cardiosimulator.ui.panels.ReferenceImagePanel
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.panels.SignificantPointPanel
import com.example.cardiosimulator.ui.panels.SignificantPointSelector
import com.example.cardiosimulator.ui.panels.ToolModePanel
import com.example.cardiosimulator.ui.utils.TraceExtractor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.ToolMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()

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

    val rhythmDrawer = @Composable {
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
    }

    val pointsDrawer = @Composable {
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

    Row(modifier = Modifier.fillMaxSize()) {
        if (isDrawerFixed) {
            rhythmDrawer()
        }
        Box(modifier = Modifier.weight(1f)) {
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

                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.constructor_anchor_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            IconButton(onClick = { showCalculateDerivedDialog = true }) {
                                Icon(
                                    Icons.Default.Calculate,
                                    contentDescription = stringResource(R.string.constructor_generate_derived)
                                )
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
                                        .padding(start = if (isDrawerFixed) 0.dp else 24.dp),
                                    monitorViewModel = monitorViewModel,
                                    staticGrid = true,
                                    showGridBackground = referenceImageUri == null,
                                    showGridLines = false,
                                    gesturesEnabled = toolMode == ToolMode.Select
                                ) { _, _, xOffset, scheme ->
                                    if (stream != null) {
                                        val isEditable = constructorViewModel.isLeadEditable(focusedLead)
                                        val scrollState = rememberScrollState()
                                        LaunchedEffect(scrollState.maxValue) {
                                            if (scrollState.maxValue > 0) {
                                                scrollState.scrollTo(scrollState.maxValue / 2)
                                            }
                                        }
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val viewWidthPx = constraints.maxWidth.toFloat()
                                            val viewHeightPx = constraints.maxHeight.toFloat()
                                            val scale = LocalPixelScale.current

                                            Column(modifier = Modifier.fillMaxSize()) {
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                        .verticalScroll(scrollState)
                                                ) {
                                                    Spacer(modifier = Modifier.height(64.dp))

                                                    EditableLead(
                                                        stream = stream,
                                                        significantPoints = file.significantPoints,
                                                        baseline = baseline,
                                                        selectedIndex = selectedIndex,
                                                        onIndexSelected = { constructorViewModel.selectIndex(it) },
                                                        isEditable = isEditable,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .ekgGrid(
                                                                scheme = scheme,
                                                                showBackground = referenceImageUri == null
                                                            ),
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
                                                }

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
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .ekgGrid(scheme = scheme),
                                                        isRunning = monitorMode.isRunning,
                                                        externalXOffsetPx = xOffset,
                                                        gridScheme = scheme
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(stringResource(R.string.constructor_lead_not_present, focusedLead.name))
                                        }
                                    }
                                }
                            }

                            if (toolMode == ToolMode.Points) {
                                SignificantPointPanel(
                                    significantPoints = file.significantPoints,
                                    selectedIndex = selectedIndex,
                                    sampleRate = monitorMode.calibration.sampleRateHz,
                                    onPointToggle = { idx, type ->
                                        constructorViewModel.toggleSignificantPoint(focusedLead, idx, type)
                                    }
                                )
                            } else if (toolMode == ToolMode.Photo) {
                                ReferenceImagePanel(
                                    referenceImageUri = referenceImageUri,
                                    onLoadImage = { launcher.launch("image/*") },
                                    imageAlpha = imageAlpha,
                                    onAlphaChange = { constructorViewModel.setImageAlpha(it) },
                                    imageLocked = imageLocked,
                                    onLockToggle = { constructorViewModel.setImageLocked(it) },
                                    onResetImage = { constructorViewModel.resetImageTransform() },
                                    showAutoDetect = referenceImageUri != null && ghostTrace == null,
                                    onAutoDetect = {
                                        scope.launch {
                                            val bitmap = withContext(Dispatchers.IO) {
                                                context.contentResolver.openInputStream(referenceImageUri!!)?.use {
                                                    BitmapFactory.decodeStream(it)
                                                }
                                            }
                                            if (bitmap != null && stream != null) {
                                                val pxPerMm = density.density * (160f / 25.4f) * monitorMode.displayScale
                                                val scale = PixelScale(
                                                    pxPerMm = pxPerMm,
                                                    paperSpeedMmPerSec = monitorMode.speed,
                                                    gainZoomY = 1.0f,
                                                    cal = monitorMode.calibration,
                                                    zoom = monitorMode.scale
                                                )

                                                val waveformWidthPx = stream.samples.size * scale.pxPerSample
                                                val waveformHeightPx = 2048 * scale.pxPerAdcCount
                                                val extracted = TraceExtractor.extract(
                                                    bitmap = bitmap,
                                                    sampleCount = stream.samples.size,
                                                    baseline = baseline,
                                                    stepX = scale.pxPerSample,
                                                    stepY = scale.pxPerAdcCount,
                                                    imageOffset = imageOffset,
                                                    imageScale = imageScale,
                                                    imageRotationDeg = imageRotationDeg,
                                                    viewWidth = waveformWidthPx,
                                                    viewHeight = waveformHeightPx
                                                )
                                                constructorViewModel.setGhostTrace(extracted)
                                            }
                                        }
                                    }
                                )
                            }

                            ToolModePanel(
                                currentMode = toolMode,
                                onModeChange = { constructorViewModel.setToolMode(it) }
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
                        if (!isDrawerFixed) {
                            rhythmDrawer()
                        }
                        pointsDrawer()
                    }
                }
            }
        }
    }
}
