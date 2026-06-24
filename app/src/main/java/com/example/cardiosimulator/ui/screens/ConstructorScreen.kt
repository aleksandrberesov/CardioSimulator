package com.example.cardiosimulator.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.data.wfdb.WfdbConverter
import com.example.cardiosimulator.data.wfdb.WfdbHeaderParser
import com.example.cardiosimulator.data.wfdb.WfdbReader
import com.example.cardiosimulator.data.wfdb.WfdbRecord
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.display.ekgGrid
import com.example.cardiosimulator.ui.panels.DrawPanel
import com.example.cardiosimulator.ui.panels.PositionPanel
import com.example.cardiosimulator.ui.panels.ReferenceImagePanel
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.panels.SelectPanel
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val imageVisible by constructorViewModel.imageVisible.collectAsState()
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

    var pendingImportRecord by remember { mutableStateOf<WfdbRecord?>(null) }
    val wfdbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytesMap = mutableMapOf<String, ByteArray>()
                var heaContent: String? = null
                for (uri in uris) {
                    val name = getFileName(context, uri) ?: continue
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                    bytesMap[name] = bytes
                    if (name.lowercase().endsWith(".hea")) {
                        heaContent = String(bytes)
                    }
                }
                if (heaContent != null) {
                    val header = WfdbHeaderParser.parse(heaContent)
                    val record = WfdbReader.readRecord(header) { fileName ->
                        bytesMap[fileName] ?: throw Exception("File $fileName not selected")
                    }
                    pendingImportRecord = record
                }
            } catch (e: Exception) {
                // TODO: Error snackbar
            }
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showCalculateDerivedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var showPhysioNetDialog by remember { mutableStateOf(false) }
    var physioNetProject by remember { mutableStateOf("mitdb/1.0.0") }
    var physioNetRecord by remember { mutableStateOf("100") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

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

    if (showPhysioNetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showPhysioNetDialog = false },
            title = { Text(stringResource(R.string.constructor_download_physionet)) },
            text = {
                Column {
                    TextField(
                        value = physioNetProject,
                        onValueChange = { physioNetProject = it },
                        label = { Text("Project Path (e.g. mitdb/1.0.0)") },
                        enabled = !isDownloading
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = physioNetRecord,
                        onValueChange = { physioNetRecord = it },
                        label = { Text("Record Name (e.g. 100)") },
                        enabled = !isDownloading
                    )
                    if (isDownloading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    }
                    downloadError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDownloading = true
                        downloadError = null
                        scope.launch {
                            try {
                                val record = com.example.cardiosimulator.network.PhysioNetClient.downloadRecord(physioNetProject, physioNetRecord)
                                pendingImportRecord = record
                                showPhysioNetDialog = false
                            } catch (e: Exception) {
                                downloadError = e.message
                            } finally {
                                isDownloading = false
                            }
                        }
                    },
                    enabled = !isDownloading && physioNetProject.isNotBlank() && physioNetRecord.isNotBlank()
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhysioNetDialog = false }, enabled = !isDownloading) {
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
        // ... Existing rename dialog ...
    }

    if (showGroupDialog && targetFile != null) {
        val groups = rhythmViewModel.repository.groups
        val currentGroup = targetFile?.group
        val availableKeys = groups.getOrderedKeys()
        
        var selectedKey by remember { mutableStateOf(currentGroup) }
        var newGroupName by remember { mutableStateOf("") }
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text(stringResource(R.string.constructor_group_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        OutlinedTextField(
                            value = if (selectedKey == null) stringResource(R.string.constructor_group_no_group) 
                                   else groups.displayName(selectedKey!!, selectedLanguage.tag) { null },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.constructor_group_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.constructor_group_no_group)) },
                                onClick = { selectedKey = null; dropdownExpanded = false }
                            )
                            availableKeys.forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(groups.displayName(key, selectedLanguage.tag) { null }) },
                                    onClick = { selectedKey = key; dropdownExpanded = false }
                                )
                            }
                        }
                    }
                    
                    TextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text(stringResource(R.string.constructor_group_new_hint)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newGroupName.isNotBlank()) {
                        constructorViewModel.createAndSetGroup(newGroupName)
                    } else {
                        constructorViewModel.setGroup(selectedKey)
                    }
                    showGroupDialog = false
                }) {
                    Text(stringResource(R.string.constructor_group_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (pendingImportRecord != null) {
        val record = pendingImportRecord!!
        var importNameEn by remember { mutableStateOf(record.header.recordName) }
        var importNameRu by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingImportRecord = null },
            title = { Text(stringResource(R.string.constructor_import_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.constructor_import_stats_format,
                            record.header.numberOfSignals,
                            record.header.numberOfSamplesPerSignal,
                            record.header.samplingFrequency
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = importNameEn,
                        onValueChange = { importNameEn = it },
                        label = { Text(stringResource(R.string.constructor_import_label_en)) }
                    )
                    TextField(
                        value = importNameRu,
                        onValueChange = { importNameRu = it },
                        label = { Text(stringResource(R.string.constructor_import_label_ru)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pathology = WfdbConverter.toPathologyFile(
                        record,
                        record.header.recordName,
                        importNameEn,
                        importNameRu
                    )
                    constructorViewModel.importPathology(pathology)
                    pendingImportRecord = null
                }) {
                    Text(stringResource(R.string.constructor_import_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportRecord = null }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }
    var isPointsDrawerExpanded by remember { mutableStateOf(false) }

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

                        IconButton(onClick = { constructorViewModel.createNewPathology() }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.constructor_new_pathology))
                        }

                        var showImportMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showImportMenu = true }) {
                                Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.constructor_import_wfdb))
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.constructor_import_wfdb) + "…") },
                                    onClick = {
                                        showImportMenu = false
                                        wfdbLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.constructor_download_physionet) + "…") },
                                    onClick = {
                                        showImportMenu = false
                                        showPhysioNetDialog = true
                                    }
                                )
                            }
                        }

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

                            IconButton(onClick = { showGroupDialog = true }) {
                                Icon(Icons.Default.Label, contentDescription = stringResource(R.string.constructor_group_title))
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
                                                        referenceImageUri = if (imageVisible) referenceImageUri else null,
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
                                                        ghostTrace = ghostTrace
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

                            when (toolMode) {
                                ToolMode.Select -> SelectPanel()
                                ToolMode.Trace -> DrawPanel(
                                    showAutoDetect = referenceImageUri != null && ghostTrace == null,
                                    hasGhostTrace = ghostTrace != null,
                                    onApplyGhostTrace = { constructorViewModel.applyGhostTrace() },
                                    onCancelGhostTrace = { constructorViewModel.setGhostTrace(null) },
                                    onUndo = { constructorViewModel.undo(focusedLead) },
                                    canUndo = true, // We could add a flow to check stack size
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
                                ToolMode.Position -> PositionPanel()
                                ToolMode.Points -> SignificantPointPanel(
                                    significantPoints = file.significantPoints,
                                    selectedIndex = selectedIndex,
                                    sampleRate = monitorMode.calibration.sampleRateHz,
                                    onPointToggle = { idx, type ->
                                        constructorViewModel.toggleSignificantPoint(focusedLead, idx, type)
                                    }
                                )
                                ToolMode.Photo -> ReferenceImagePanel(
                                    referenceImageUri = referenceImageUri,
                                    onLoadImage = { launcher.launch("image/*") },
                                    onDeleteImage = { constructorViewModel.setReferenceImageUri(null) },
                                    imageVisible = imageVisible,
                                    onToggleVisibility = { constructorViewModel.setImageVisible(it) },
                                    imageAlpha = imageAlpha,
                                    onAlphaChange = { constructorViewModel.setImageAlpha(it) },
                                    imageScale = imageScale,
                                    onScaleChange = { constructorViewModel.setImageScale(it) },
                                    imageRotation = imageRotationDeg,
                                    onRotationChange = { constructorViewModel.setImageRotation(it) },
                                    imageLocked = imageLocked,
                                    onLockToggle = { constructorViewModel.setImageLocked(it) },
                                    onResetImage = { constructorViewModel.resetImageTransform() }
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

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
