package com.example.cardiosimulator.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.data.displayScaleFactor
import com.example.cardiosimulator.data.wfdb.WfdbConverter
import com.example.cardiosimulator.data.wfdb.WfdbHeaderParser
import com.example.cardiosimulator.data.wfdb.WfdbReader
import com.example.cardiosimulator.data.wfdb.WfdbRecord
import com.example.cardiosimulator.domain.DerivedLeads
import com.example.cardiosimulator.domain.EcgFilterType
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.MonitorModeModel
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.domain.TipOverlay
import com.example.cardiosimulator.domain.TipOverlayKind
import com.example.cardiosimulator.network.PhysioNetClient
import com.example.cardiosimulator.signals.biosppy.EcgFilters
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.components.SynthesizerDialog
import com.example.cardiosimulator.ui.components.UnsavedChangesDialog
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.display.ekgGrid
import com.example.cardiosimulator.ui.panels.DrawPanel
import com.example.cardiosimulator.ui.panels.PanPanel
import com.example.cardiosimulator.ui.panels.PositionPanel
import com.example.cardiosimulator.ui.panels.ReferenceImagePanel
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.panels.SelectPanel
import com.example.cardiosimulator.ui.panels.SignificantPointPanel
import com.example.cardiosimulator.ui.panels.TipsPanel
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

    val selectedTipKind by constructorViewModel.selectedTipKind.collectAsState()
    val selectedTipEndCap by constructorViewModel.selectedTipEndCap.collectAsState()
    val selectedTipLead by constructorViewModel.selectedTipLead.collectAsState()
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()

    LaunchedEffect(targetFile?.id, targetFile?.clinicalCase) {
        val f = targetFile
        if (f != null) appViewModel.setClinicalMode(!f.clinicalCase.isNullOrBlank())
    }

    var showTipCommentsDialog by remember { mutableStateOf(false) }
    var showTipCaptionDialog by remember { mutableStateOf(false) }
    var pendingTip by remember { mutableStateOf<TipOverlay?>(null) }

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
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showClinicalDialog by remember { mutableStateOf(false) }
    var showCalculateDerivedDialog by remember { mutableStateOf(false) }
    var showAllLeads by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSynthesizerDialog by remember { mutableStateOf(false) }
    var pendingSwitchId by remember { mutableStateOf<String?>(null) }

    val pendingMode by appViewModel.pendingMode.collectAsState()

    DisposableEffect(Unit) {
        appViewModel.leaveGuard = { !constructorViewModel.hasUnsavedChanges }
        onDispose { appViewModel.leaveGuard = null }
    }

    if (pendingSwitchId != null) {
        UnsavedChangesDialog(
            onSave = {
                constructorViewModel.save()
                constructorViewModel.selectPathology(pendingSwitchId!!)
                pendingSwitchId = null
            },
            onDiscard = {
                constructorViewModel.selectPathology(pendingSwitchId!!)
                pendingSwitchId = null
            },
            onCancel = { pendingSwitchId = null }
        )
    }

    if (pendingMode != null) {
        UnsavedChangesDialog(
            onSave = {
                constructorViewModel.save()
                appViewModel.confirmPendingMode()
            },
            onDiscard = {
                constructorViewModel.discardChanges()
                appViewModel.confirmPendingMode()
            },
            onCancel = { appViewModel.cancelPendingMode() }
        )
    }

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
                                val record = PhysioNetClient.downloadRecord(physioNetProject, physioNetRecord)
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

    if (showSynthesizerDialog) {
        SynthesizerDialog(
            onDismiss = { showSynthesizerDialog = false },
            onGenerate = { bpm, ap, ar, asVal, at, variance ->
                constructorViewModel.generateSynthesizedBeat(
                    bpm = bpm,
                    ap = ap,
                    ar = ar,
                    asVal = asVal,
                    at = at,
                    variance = variance,
                    sampleRate = monitorMode.calibration.sampleRateHz.toDouble()
                )
            }
        )
    }

    if (showClinicalDialog && targetFile != null) {
        ClinicalCaseDialog(
            initialClinicalCase = targetFile?.clinicalCase,
            onDismiss = { showClinicalDialog = false },
            onSave = {
                constructorViewModel.setClinicalCase(it)
                showClinicalDialog = false
            }
        )
    }

    if (showTipCaptionDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTipCaptionDialog = false; pendingTip = null },
            title = { Text(stringResource(R.string.constructor_tips_text_prompt)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTip?.let { constructorViewModel.addTip(it.copy(text = text)) }
                    showTipCaptionDialog = false
                    pendingTip = null
                }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            }
        )
    }

    if (showTipCommentsDialog) {
        val comments = targetFile?.tipComments?.joinToString("\n") ?: ""
        var text by remember { mutableStateOf(comments) }
        AlertDialog(
            onDismissRequest = { showTipCommentsDialog = false },
            title = { Text(stringResource(R.string.constructor_tips_comments)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.constructor_tips_comments_help),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        placeholder = { Text("Example:\n1. Sharp P wave\n2. QRS widening") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    constructorViewModel.setTipComments(text.split('\n').filter { it.isNotBlank() })
                    showTipCommentsDialog = false
                }) {
                    Text(stringResource(R.string.constructor_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTipCommentsDialog = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showRenameDialog && targetFile != null) {
        var titleEn by remember { mutableStateOf(targetFile?.titleEn ?: "") }
        var nameRu by remember { mutableStateOf(targetFile?.nameRu ?: "") }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.constructor_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = titleEn,
                        onValueChange = { titleEn = it },
                        label = { Text(stringResource(R.string.constructor_import_label_en)) },
                        singleLine = true
                    )
                    TextField(
                        value = nameRu,
                        onValueChange = { nameRu = it },
                        label = { Text(stringResource(R.string.constructor_import_label_ru)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    constructorViewModel.rename(titleEn, Language.EN)
                    constructorViewModel.rename(nameRu, Language.RU)
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

    if (showDescriptionDialog && targetFile != null) {
        var descriptionText by remember { mutableStateOf(targetFile?.description ?: "") }
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text(stringResource(R.string.description_edit_title)) },
            text = {
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text(stringResource(R.string.pathology_description_label)) },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    minLines = 4,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    constructorViewModel.setDescription(descriptionText)
                    showDescriptionDialog = false
                }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showGroupDialog && targetFile != null) {
        val groups = rhythmViewModel.repository.groups
        val currentGroup = targetFile?.group
        val currentAcronym = targetFile?.acronyms?.joinToString(",")
        val availableKeys = groups.getOrderedKeys()

        var selectedKey by remember { mutableStateOf(currentGroup) }
        var newGroupName by remember { mutableStateOf("") }
        var acronymText by remember { mutableStateOf(currentAcronym ?: "") }
        var dropdownExpanded by remember { mutableStateOf(false) }
        var acronymSuggestionsExpanded by remember { mutableStateOf(false) }

        val acronymSuggestions = remember(acronymText) {
            if (acronymText.isBlank()) emptyList<com.example.cardiosimulator.domain.TaxonomyEntry>()
            else com.example.cardiosimulator.domain.Taxonomy.shared.allEntries.filter {
                it.acronym.contains(acronymText, ignoreCase = true) ||
                        it.nameRu.contains(acronymText, ignoreCase = true)
            }.take(5)
        }

        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text(stringResource(R.string.constructor_group_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Acronym Field
                    Box {
                        OutlinedTextField(
                            value = acronymText,
                            onValueChange = {
                                acronymText = it
                                acronymSuggestionsExpanded = it.isNotBlank()
                            },
                            label = { Text(stringResource(R.string.test_ctor_acronyms)) },
                            placeholder = { Text(stringResource(R.string.test_ctor_acronyms_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (acronymText.isNotEmpty()) {
                                    IconButton(onClick = { acronymText = ""; acronymSuggestionsExpanded = false }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                }
                            }
                        )

                        if (acronymSuggestionsExpanded && acronymSuggestions.isNotEmpty()) {
                            DropdownMenu(
                                expanded = acronymSuggestionsExpanded,
                                onDismissRequest = { acronymSuggestionsExpanded = false },
                            ) {
                                acronymSuggestions.forEach { entry ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(entry.acronym, fontWeight = FontWeight.Bold)
                                                Text(entry.nameRu, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        onClick = {
                                            acronymText = entry.acronym
                                            acronymSuggestionsExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

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
                    constructorViewModel.setAcronyms(if (acronymText.isBlank()) emptyList() else acronymText.split(",").map { it.trim() }.filter { it.isNotEmpty() })
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

    val rhythmDrawer = @Composable {
        val editedRhythms = remember(rhythms, targetFile) {
            if (targetFile == null) rhythms
            else rhythms.map {
                if (it.id == targetFile?.id) {
                    it.copy(
                        titleEn = targetFile!!.titleEn,
                        nameRu = targetFile!!.nameRu,
                        group = targetFile!!.group,
                        clinicalCase = targetFile!!.clinicalCase
                    )
                } else it
            }
        }

        SideDrawer(
            isExpanded = isRhythmDrawerExpanded,
            onExpandedChange = { isRhythmDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    rhythms = editedRhythms,
                    selectedId = targetFile?.id,
                    onRhythmSelect = { entry ->
                        if (entry.id != targetFile?.id && constructorViewModel.hasUnsavedChanges) {
                            pendingSwitchId = entry.id
                        } else {
                            constructorViewModel.selectPathology(entry.id)
                        }
                    },
                )
            },
            handlerContent = {
                Text(
                    text = stringResource(R.string.rhythm_drawer_title),
                    modifier = Modifier
                        .requiredWidth(64.dp)
                        .rotate(-90f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            },
            handlerModifier = Modifier.offset(y = (-40).dp),
            modifier = Modifier.fillMaxHeight()
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isDrawerFixed) {
            rhythmDrawer()
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                val displayTitle = targetFile?.let { file ->
                    val title = if (selectedLanguage == Language.RU)
                        file.nameRu ?: file.titleEn
                    else
                        file.titleEn
                    file.number?.let { "$it $title" } ?: title
                } ?: stringResource(R.string.constructor_no_pathology_selected)

                // Toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = { constructorViewModel.createNewPathology() }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.constructor_new_pathology)
                                )
                            }

                            IconButton(onClick = { showSynthesizerDialog = true }) {
                                Icon(Icons.Default.GraphicEq, contentDescription = "Synthesizer")
                            }

                            var showImportMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showImportMenu = true }) {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = stringResource(R.string.constructor_import_wfdb)
                                    )
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
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = stringResource(R.string.constructor_undo)
                                        )
                                    }
                                    IconButton(onClick = { constructorViewModel.redo(focusedLead) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = stringResource(R.string.cd_redo)
                                        )
                                    }
                                }
                            }

                            if (targetFile != null) {
                                IconButton(onClick = { showRenameDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_rename))
                                }

                                IconButton(onClick = { showDescriptionDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = stringResource(R.string.description_edit_tooltip)
                                    )
                                }

                                IconButton(onClick = { showGroupDialog = true }) {
                                    Icon(
                                        Icons.Default.Label,
                                        contentDescription = stringResource(R.string.constructor_group_title)
                                    )
                                }

                                IconButton(onClick = { showClinicalDialog = true }) {
                                    Icon(
                                        // Person = "patient clinical case"; matches the Windows U+E77B Contact glyph.
                                        imageVector = Icons.Default.Person,
                                        contentDescription = stringResource(R.string.clinical_edit_tooltip)
                                    )
                                }

                                IconButton(onClick = { constructorViewModel.duplicateCurrentPathology() }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_copy)
                                    )
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
                                    OutlinedButton(onClick = {
                                        constructorViewModel.revertLead(
                                            focusedLead
                                        )
                                    }) {
                                        Text(stringResource(R.string.constructor_revert_lead_btn))
                                    }
                                }
                            }
                        }
                    }
                }

                // Lead Tabs (+ trailing "All leads" button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabRow(
                        modifier = Modifier.weight(1f),
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
                                        color = if (dirtyLeads.contains(lead)) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            )
                        }
                    }

                    if (targetFile != null) {
                        IconButton(onClick = { showAllLeads = true }) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = stringResource(R.string.constructor_view_all_leads)
                            )
                        }
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
                                    gesturesEnabled = toolMode == ToolMode.Select || toolMode == ToolMode.Pan
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
                                                        ghostTrace = ghostTrace,
                                                        tips = targetFile?.tips ?: emptyList(),
                                                        selectedTipKind = selectedTipKind,
                                                        selectedTipEndCap = selectedTipEndCap,
                                                        selectedTipLead = selectedTipLead,
                                                        onTipPlaced = { tip ->
                                                            if (tip.kind == TipOverlayKind.Arrow ||
                                                                tip.kind == TipOverlayKind.Label) {
                                                                pendingTip = tip
                                                                showTipCaptionDialog = true
                                                            } else {
                                                                constructorViewModel.addTip(tip)
                                                            }
                                                        }
                                                    )
                                                }

                                                val points = remember(stream, baseline, monitorMode.filterType, monitorMode.calibration) {
                                                    val zeroed = stream.samples.map { (it - baseline).toFloat() }
                                                    if (monitorMode.filterType == EcgFilterType.NONE || zeroed.size < 50) {
                                                        Points(zeroed)
                                                    } else {
                                                        val filtered = EcgFilters.apply(
                                                            zeroed.map { it.toDouble() }.toDoubleArray(),
                                                            monitorMode.filterType,
                                                            monitorMode.calibration.sampleRateHz.toDouble()
                                                        )
                                                        Points(filtered.map { it.toFloat() })
                                                    }
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
                                    },
                                    onAutoDetect = {
                                        constructorViewModel.autoDetectLandmarks(
                                            lead = focusedLead,
                                            samplingRate = monitorMode.calibration.sampleRateHz.toDouble()
                                        )
                                    },
                                    onPointSelect = { constructorViewModel.selectIndex(it.index) }
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
                                ToolMode.Pan -> PanPanel(
                                    onResetView = { monitorViewModel.resetView() }
                                )
                                ToolMode.Tips -> TipsPanel(
                                    selectedKind = selectedTipKind,
                                    onKindSelected = { constructorViewModel.setSelectedTipKind(it) },
                                    selectedEndCap = selectedTipEndCap,
                                    onEndCapSelected = { constructorViewModel.setSelectedTipEndCap(it) },
                                    selectedLead = selectedTipLead,
                                    onLeadSelected = { constructorViewModel.setSelectedTipLead(it) },
                                    onUndo = { constructorViewModel.removeLastTip() },
                                    onClearAll = { constructorViewModel.clearTips() },
                                    onEditComments = { showTipCommentsDialog = true },
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }

                            ToolModePanel(
                                currentMode = toolMode,
                                onModeChange = { constructorViewModel.setToolMode(it) }
                            )
                        }

                        if (showAllLeads) {
                            AllLeadsPreviewOverlay(
                                targetFile = targetFile!!,
                                monitorMode = monitorMode,
                                baseline = rhythmViewModel.repository.manifest()?.baseline ?: 1024,
                                titleName = displayTitle,
                                onClose = { showAllLeads = false }
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

/**
 * Dialog for editing clinical case metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalCaseDialog(
    initialClinicalCase: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val params = remember(initialClinicalCase) {
        initialClinicalCase?.split(',')?.associate {
            val parts = it.split('=')
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
        }?.filterKeys { it.isNotEmpty() } ?: emptyMap()
    }

    var title by remember { mutableStateOf(params["title"] ?: "") }
    var description by remember { mutableStateOf(params["description"] ?: "") }
    var name by remember { mutableStateOf(params["name"] ?: "") }
    var age by remember { mutableStateOf(params["age"] ?: "") }
    var gender by remember { mutableStateOf(params["gender"] ?: "") }
    var hr by remember { mutableStateOf(params["hr"] ?: "") }
    var bp by remember { mutableStateOf(params["bp"] ?: "") }
    var others by remember {
        mutableStateOf(params.filterKeys { it !in listOf("title", "description", "name", "age", "gender", "hr", "bp") }
            .map { "${it.key}=${it.value}" }
            .joinToString(", "))
    }

    var genderExpanded by remember { mutableStateOf(false) }
    val genderUnset = stringResource(R.string.clinical_gender_unset)
    val genderOptions = listOf(stringResource(R.string.gender_male), stringResource(R.string.gender_female))

    var clearAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clinical_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = clearAll,
                        onCheckedChange = {
                            clearAll = it
                            if (it) {
                                title = ""; description = ""; name = ""; age = ""
                                gender = ""; hr = ""; bp = ""; others = ""
                            }
                        }
                    )
                    Text(stringResource(R.string.clinical_clear_all))
                }

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.clinical_label_title)) },
                    singleLine = true
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.clinical_label_description)) },
                    singleLine = true
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.clinical_label_patient_name)) },
                    singleLine = true
                )

                TextField(
                    value = age,
                    onValueChange = { if (it.all { char -> char.isDigit() }) age = it },
                    label = { Text(stringResource(R.string.clinical_label_age)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Box {
                    OutlinedTextField(
                        value = gender,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.clinical_label_gender)) },
                        placeholder = { Text(genderUnset) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier.fillMaxWidth().clickable { genderExpanded = true }
                    )
                    DropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        DropdownMenuItem(
                            text = { Text(genderUnset) },
                            onClick = {
                                gender = ""
                                genderExpanded = false
                            }
                        )
                        genderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    gender = option
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = hr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) hr = it },
                    label = { Text(stringResource(R.string.clinical_label_hr)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                TextField(
                    value = bp,
                    onValueChange = { bp = it },
                    label = { Text(stringResource(R.string.clinical_label_bp)) },
                    singleLine = true
                )
                TextField(
                    value = others,
                    onValueChange = { others = it },
                    label = { Text(stringResource(R.string.clinical_label_others)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newParams = mutableMapOf<String, String>()
                if (title.isNotBlank()) newParams["title"] = title
                if (description.isNotBlank()) {
                    // clinical_case is stored raw & comma-delimited — strip separators so it stays parseable.
                    newParams["description"] = description.replace(Regex("[,;\r\n]"), " ").trim()
                }
                if (name.isNotBlank()) newParams["name"] = name
                if (age.isNotBlank()) newParams["age"] = age
                if (gender.isNotBlank()) {
                    // Normalize gender for storage
                    val maleStr = "Male" // stringResource(R.string.gender_male) -- can't use here if we want canonical
                    val femaleStr = "Female"

                    val normalizedGender = if (gender == genderOptions[0]) "Male" else "Female"
                    newParams["gender"] = normalizedGender
                }
                if (hr.isNotBlank()) newParams["hr"] = hr
                if (bp.isNotBlank()) newParams["bp"] = bp

                val othersList = others.split(',').map { it.trim() }.filter { it.contains('=') }
                othersList.forEach {
                    val parts = it.split('=')
                    newParams[parts[0].trim()] = parts[1].trim()
                }

                onSave(newParams.map { "${it.key}=${it.value}" }.joinToString(","))
            }) {
                Text(stringResource(R.string.constructor_rename_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.constructor_rename_cancel))
            }
        }
    )
}

@Composable
private fun BoxScope.AllLeadsPreviewOverlay(
    targetFile: PathologyFile,
    monitorMode: MonitorModeModel,
    baseline: Int,
    titleName: String,
    onClose: () -> Unit,
) {
    // Build the 12-lead map from the *edited* file (reflects unsaved edits). remember(targetFile,…)
    // is what makes it refresh when a different rhythm is picked in the still-visible list.
    val map = remember(targetFile, baseline) {
        buildMap<Lead, Points> {
            fun zeroed(l: Lead): List<Float>? =
                targetFile.leads[l]?.samples?.map { (it - baseline).toFloat() }
            for (lead in Lead.entries) {
                val direct = zeroed(lead)
                if (direct != null) {
                    put(lead, Points(direct))
                    continue
                }
                val synth = when (lead) {
                    Lead.III, Lead.aVR, Lead.aVL, Lead.aVF -> {
                        val i = zeroed(Lead.I)
                        val ii = zeroed(Lead.II)
                        if (i != null && ii != null)
                            DerivedLeads.combineIII_aVR_aVL_aVF(i, ii, lead) else null
                    }
                    Lead.V1, Lead.V3, Lead.V4, Lead.V5 -> {
                        val v2 = zeroed(Lead.V2)
                        val v6 = zeroed(Lead.V6)
                        if (v2 != null && v6 != null)
                            DerivedLeads.combineV1_V3_V4_V5(v2, v6, lead) else null
                    }
                    else -> null
                }
                if (!synth.isNullOrEmpty()) put(lead, Points(synth))
            }
        }
    }

    val scheme = monitorMode.gridScheme
    val density = LocalDensity.current
    // 12-lead layout ⇒ displayScaleFactor(12); mirrors Monitor.kt's pxPerMm formula.
    val pxPerMm = density.density * (160f / 25.4f) * monitorMode.displayScale * displayScaleFactor(12)
    val pixelScale = remember(pxPerMm, monitorMode.speed, monitorMode.calibration) {
        PixelScale(
            pxPerMm = pxPerMm, paperSpeedMmPerSec = monitorMode.speed,
            gainZoomY = 1f, cal = monitorMode.calibration, zoom = 1f
        )
    }

    Surface(modifier = Modifier.matchParentSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: pathology title + close.
            Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        titleName, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            }
            // 12-lead static grid (4×3). No transformable/pointerInput ⇒ read-only.
            CompositionLocalProvider(LocalPixelScale provides pixelScale) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .ekgGrid(scheme = scheme, showBackground = true)
                ) {
                    LeadsGrid(rows = 3, columns = 4, itemCount = 12) { _, lead ->
                        LeadView(
                            points = lead?.let { map[it] }?.takeIf { it.values.size >= 2 }
                                ?: Points(emptyList()),
                            title = lead?.name ?: "",
                            isRunning = false,
                            xOffsetPx = 0f,
                            gridScheme = scheme,
                            significantPoints = targetFile.significantPoints,
                            calibration = monitorMode.calibration,
                            tips = targetFile.tips,
                            showTips = true,
                            lead = lead,
                            filterType = monitorMode.filterType
                        )
                    }
                }
            }
        }
    }
}
