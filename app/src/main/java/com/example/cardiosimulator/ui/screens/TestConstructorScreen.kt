package com.example.cardiosimulator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.data.TestImageStore
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.QuestionStimulus
import com.example.cardiosimulator.domain.TestQuestion
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.components.Tab as CustomTab
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorTab
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.TestConstructorViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    testConstructorViewModel: TestConstructorViewModel
) {
    val activeTab by testConstructorViewModel.activeTab.collectAsState()
    
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Monitor Panel (Left)
        Box(modifier = Modifier.weight(1f).middleSectionLeft()) {
            Monitor(
                modifier = Modifier.fillMaxSize(),
                monitorViewModel = monitorViewModel,
            ) { rows, columns, xOffset, scheme ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count,
                    leadOrder = mode.leadOrder ?: com.example.cardiosimulator.ui.display.LEAD_ORDER
                ) { _, lead ->
                    val points = lead?.let { waveforms[it] } ?: Points(emptyList<Float>())
                    LeadView(
                        points = points,
                        title = lead?.name ?: "",
                        isRunning = mode.isRunning,
                        xOffsetPx = xOffset,
                        gridScheme = scheme,
                        artifacts = mode.artifacts,
                        filterType = mode.filterType,
                        calibration = mode.calibration
                    )
                }
            }
        }

        VerticalDivider()

        // Editor Panel (Right)
        Box(modifier = Modifier.width(500.dp).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (activeTab == ConstructorTab.TEST) {
                    TestEditor(appViewModel, monitorViewModel, rhythmViewModel, testConstructorViewModel)
                } else {
                    BankEditor(appViewModel, monitorViewModel, rhythmViewModel, testConstructorViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestEditor(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    viewModel: TestConstructorViewModel
) {
    val tests = appViewModel.testRepository?.tests() ?: emptyList()
    val testId by viewModel.testId.collectAsState()
    val title by viewModel.title.collectAsState()
    val time by viewModel.questionTimeSeconds.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val themes by viewModel.themes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = tests.find { it.testId == testId }?.title ?: "Select Test",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.test_ctor_tests_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    tests.forEach { test ->
                        DropdownMenuItem(
                            text = { Text(test.title) },
                            onClick = {
                                viewModel.load(test.testId)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.newTest() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.test_ctor_new))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { viewModel.setTitle(it) },
            label = { Text(stringResource(R.string.test_ctor_title_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = if (time == 0) "" else time.toString(),
            onValueChange = { viewModel.setQuestionTimeSeconds(it.toIntOrNull() ?: 0) },
            label = { Text(stringResource(R.string.test_ctor_time_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        questions.forEach { question ->
            QuestionEditorCard(
                appViewModel = appViewModel,
                question = question,
                rhythms = rhythms,
                themes = themes,
                onUpdate = { transform -> viewModel.updateQuestion(question.id, transform) },
                onRemove = { viewModel.removeQuestion(question.id) },
                onAddOption = { viewModel.addOption(question.id) },
                onRemoveOption = { optId -> viewModel.removeOption(question.id, optId) },
                onPreview = { pathologyId ->
                    if (pathologyId != null) {
                        rhythmViewModel.selectRhythm(pathologyId, persist = false)
                        appViewModel.sendStartCommand(pathologyId)
                    } else {
                        appViewModel.sendStopCommand()
                    }
                },
                onToggleAssembly = { isAssembly -> viewModel.toggleAssembly(question.id, isAssembly) },
                onBuildAssembly = { sourceId, lead, partCount ->
                    viewModel.buildAssembly(question.id, appViewModel.repository!!, sourceId, lead, partCount)
                },
                extraActions = {
                    TextButton(onClick = { viewModel.saveToBank(question) }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.test_ctor_to_bank))
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.addQuestion() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.test_ctor_add_question))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.saveTest() }) {
                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.test_ctor_save))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.deleteTest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.test_ctor_delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankEditor(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    viewModel: TestConstructorViewModel
) {
    val bankQuestions by viewModel.bankQuestions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val themes by viewModel.themes.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()

    val courseEntries by appViewModel.courses.collectAsState()
    val lang by appViewModel.selectedLanguage.collectAsState()

    val courseThemeNames = courseEntries
        .filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
        .map { if (lang == Language.RU) (it.nameRu ?: it.titleEn) else it.titleEn }
        .filter { name -> name.isNotBlank() && themes.none { it.equals(name, ignoreCase = true) } }

    var showThemeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text(stringResource(R.string.test_ctor_search_hint)) },
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showThemeDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.test_ctor_bank_themes))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ScrollableTabRow(
            selectedTabIndex = if (selectedTheme == null) 0 else themes.indexOf(selectedTheme) + 1,
            edgePadding = 0.dp
        ) {
            Tab(
                selected = selectedTheme == null,
                onClick = { viewModel.setSelectedTheme(null) },
                text = { Text("Все") }
            )
            themes.forEach { theme ->
                Tab(
                    selected = selectedTheme == theme,
                    onClick = { viewModel.setSelectedTheme(theme) },
                    text = { Text(theme) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val filtered = bankQuestions.filter { q ->
            (selectedTheme == null || q.theme == selectedTheme) &&
            (searchQuery.isBlank() || q.text.contains(searchQuery, ignoreCase = true) || q.tagList.any { it.contains(searchQuery, ignoreCase = true) })
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { question ->
                QuestionEditorCard(
                    appViewModel = appViewModel,
                    question = question,
                    rhythms = rhythms,
                    themes = themes,
                    onUpdate = { transform -> viewModel.updateBankQuestion(question.id, transform) },
                    onRemove = { viewModel.deleteFromBank(question.id) },
                    onAddOption = { viewModel.addOption(question.id) }, // Note: this needs careful implementation for bank
                    onRemoveOption = { optId -> viewModel.removeOption(question.id, optId) },
                    onPreview = { pathologyId ->
                        if (pathologyId != null) {
                            rhythmViewModel.selectRhythm(pathologyId, persist = false)
                            appViewModel.sendStartCommand(pathologyId)
                        } else {
                            appViewModel.sendStopCommand()
                        }
                    },
                    onToggleAssembly = { isAssembly -> viewModel.toggleAssembly(question.id, isAssembly) },
                    onBuildAssembly = { sourceId, lead, partCount ->
                        viewModel.buildAssembly(question.id, appViewModel.repository!!, sourceId, lead, partCount)
                    },
                    extraActions = {
                        TextButton(onClick = { viewModel.addFromBank(question) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.test_ctor_add_from_bank))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let {
                val json = viewModel.exportBank()
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(json.toByteArray())
                }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                if (json != null) viewModel.importBank(json)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.test_ctor_bank_import))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { exportLauncher.launch("question_bank.json") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.test_ctor_bank_export))
            }
        }
    }

    if (showThemeDialog) {
        ThemeManagerDialog(
            themes = themes,
            courses = courseThemeNames,
            onAdd = { viewModel.addTheme(it) },
            onDelete = { viewModel.deleteTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorCard(
    appViewModel: AppViewModel,
    question: TestQuestion,
    rhythms: List<PathologyEntry>,
    themes: List<String>,
    onUpdate: ((TestQuestion) -> TestQuestion) -> Unit,
    onRemove: () -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (String) -> Unit,
    onPreview: (String?) -> Unit,
    onToggleAssembly: (Boolean) -> Unit = {},
    onBuildAssembly: (String, com.example.cardiosimulator.domain.Lead, Int) -> Unit = { _, _, _ -> },
    extraActions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = TestImageStore.copyImageToBank(
                context, it, File(context.filesDir, AppViewModel.TEST_IMAGES_DIR)
            )
            if (fileName != null) {
                onUpdate { q -> q.copy(imagePath = fileName) }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ID: ${question.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }

            // Stimulus Kind
            val stimulus = question.stimulus
            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = stimulus == QuestionStimulus.Text,
                    onClick = { 
                        onUpdate { it.copy(imagePath = null, pathologyId = null) }
                        onPreview(null)
                    },
                    label = { Text(stringResource(R.string.test_ctor_stimulus_text)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = stimulus == QuestionStimulus.Image,
                    onClick = { 
                        onUpdate { it.copy(imagePath = "pending", pathologyId = null) }
                        onPreview(null)
                    },
                    label = { Text(stringResource(R.string.test_ctor_stimulus_image)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = stimulus == QuestionStimulus.Ecg && !question.isAssembly,
                    onClick = { 
                        onUpdate { it.copy(imagePath = null, pathologyId = rhythms.firstOrNull()?.id, assemble = null) }
                        onPreview(rhythms.firstOrNull()?.id)
                    },
                    label = { Text(stringResource(R.string.test_ctor_stimulus_ecg)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = question.isAssembly,
                    onClick = { 
                        onToggleAssembly(!question.isAssembly)
                        onPreview(null)
                    },
                    label = { Text(stringResource(R.string.assemble_type_label)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = question.text,
                onValueChange = { text -> onUpdate { it.copy(text = text) } },
                label = { Text(stringResource(R.string.test_ctor_question_text)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (stimulus == QuestionStimulus.Ecg && !question.isAssembly) {
                Spacer(modifier = Modifier.height(8.dp))
                var showEcgPicker by remember { mutableStateOf(false) }
                val selected = rhythms.find { it.id == question.pathologyId }
                val currentLanguage by appViewModel.selectedLanguage.collectAsState()
                val label = selected?.let {
                    if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
                } ?: stringResource(R.string.test_ctor_ecg_none)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = label, onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.test_ctor_ecg)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.weight(1f).clickable { showEcgPicker = true }
                    )
                    if (question.pathologyId != null) {
                        IconButton(onClick = { onUpdate { it.copy(pathologyId = null) }; onPreview(null) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.test_ctor_ecg_none))
                        }
                    }
                }
                if (showEcgPicker) {
                    AlertDialog(
                        onDismissRequest = { showEcgPicker = false },
                        title = { Text(stringResource(R.string.test_ctor_ecg)) },
                        text = {
                            com.example.cardiosimulator.ui.panels.RhythmSelector(
                                appViewModel = appViewModel,
                                modifier = Modifier.fillMaxHeight(0.7f),
                                rhythms = rhythms,
                                selectedId = question.pathologyId,
                                showPinButton = false,
                                onRhythmSelect = { entry ->
                                    onUpdate { it.copy(pathologyId = entry.id) }
                                    onPreview(entry.id)
                                    showEcgPicker = false
                                },
                            )
                        },
                        confirmButton = { TextButton(onClick = { showEcgPicker = false }) { Text(stringResource(R.string.data_source_close)) } },
                    )
                }
            } else if (stimulus == QuestionStimulus.Image && !question.isAssembly) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.test_ctor_image_select))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = question.imagePath ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            } else if (question.isAssembly) {
                AssembleEditor(
                    appViewModel = appViewModel,
                    question = question,
                    rhythms = rhythms,
                    onUpdate = onUpdate,
                    onBuild = onBuildAssembly
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options
            if (!question.isAssembly) {
                question.options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = question.correctOptionId == option.id,
                        onClick = { onUpdate { it.copy(correctOptionId = option.id) } }
                    )
                    OutlinedTextField(
                        value = option.text,
                        onValueChange = { text ->
                            onUpdate { q ->
                                q.copy(options = q.options.map { if (it.id == option.id) it.copy(text = text) else it })
                            }
                        },
                        label = { Text(stringResource(R.string.test_ctor_option_format, index + 1)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemoveOption(option.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

            TextButton(onClick = onAddOption) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.test_ctor_add_option))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Theme & Tags
            Row {
                var themeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = !themeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = question.theme ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.test_ctor_theme_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("—") },
                            onClick = {
                                onUpdate { it.copy(theme = null) }
                                themeExpanded = false
                            }
                        )
                        themes.forEach { theme ->
                            DropdownMenuItem(
                                text = { Text(theme) },
                                onClick = {
                                    onUpdate { it.copy(theme = theme) }
                                    themeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = question.tags ?: "",
                    onValueChange = { tags -> onUpdate { it.copy(tags = tags) } },
                    label = { Text(stringResource(R.string.test_ctor_tags_label)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = question.comment,
                onValueChange = { comment -> onUpdate { it.copy(comment = comment) } },
                label = { Text(stringResource(R.string.test_ctor_comment)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                extraActions()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManagerDialog(
    themes: List<String>,
    courses: List<String>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTheme by remember { mutableStateOf("") }
    var selectedCourse by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.test_ctor_manage_themes), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                themes.forEach { theme ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(theme, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDelete(theme) }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTheme,
                        onValueChange = { newTheme = it },
                        label = { Text(stringResource(R.string.test_ctor_theme_new_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newTheme.isNotBlank()) {
                            onAdd(newTheme)
                            newTheme = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                if (courses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.test_ctor_theme_from_course), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedCourse,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.test_ctor_theme_from_course_hint)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                courses.forEach { course ->
                                    DropdownMenuItem(
                                        text = { Text(course) },
                                        onClick = {
                                            selectedCourse = course
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            if (selectedCourse.isNotBlank()) {
                                onAdd(selectedCourse)
                                selectedCourse = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssembleEditor(
    appViewModel: AppViewModel,
    question: TestQuestion,
    rhythms: List<PathologyEntry>,
    onUpdate: ((TestQuestion) -> TestQuestion) -> Unit,
    onBuild: (String, com.example.cardiosimulator.domain.Lead, Int) -> Unit
) {
    val assemble = question.assemble ?: return
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()

    // We need a way to track the desired part count if not yet built
    var desiredPartCount by remember(question.id) { mutableIntStateOf(if (assemble.parts.isEmpty()) 4 else assemble.parts.size.coerceIn(3, 6)) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.assemble_ctor_hint), style = MaterialTheme.typography.bodySmall, color = com.example.cardiosimulator.ui.theme.TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        // Source Rhythm
        var showSourcePicker by remember { mutableStateOf(false) }
        val source = rhythms.find { it.id == assemble.sourcePathologyId }
        val sourceLabel = source?.let { getDisplayNameForCtor(it, currentLanguage, false) } ?: stringResource(R.string.assemble_ctor_none)

        OutlinedTextField(
            value = sourceLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.assemble_ctor_source)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable { showSourcePicker = true }
        )

        if (showSourcePicker) {
            AlertDialog(
                onDismissRequest = { showSourcePicker = false },
                title = { Text(stringResource(R.string.assemble_ctor_source)) },
                text = {
                    com.example.cardiosimulator.ui.panels.RhythmSelector(
                        appViewModel = appViewModel,
                        modifier = Modifier.fillMaxHeight(0.7f),
                        rhythms = rhythms,
                        selectedId = assemble.sourcePathologyId,
                        showPinButton = false,
                        onRhythmSelect = { entry ->
                            onBuild(entry.id, assemble.sliceLead, desiredPartCount)
                            showSourcePicker = false
                        },
                    )
                },
                confirmButton = { TextButton(onClick = { showSourcePicker = false }) { Text(stringResource(R.string.cd_close)) } },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lead
        var leadExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = leadExpanded,
            onExpandedChange = { leadExpanded = !leadExpanded }
        ) {
            OutlinedTextField(
                value = assemble.sliceLead.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.assemble_ctor_lead)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leadExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = leadExpanded,
                onDismissRequest = { leadExpanded = false }
            ) {
                com.example.cardiosimulator.domain.Lead.entries.forEach { lead ->
                    DropdownMenuItem(
                        text = { Text(lead.name) },
                        onClick = {
                            if (assemble.sourcePathologyId != null) {
                                onBuild(assemble.sourcePathologyId, lead, desiredPartCount)
                            } else {
                                onUpdate { q -> q.copy(assemble = q.assemble?.copy(sliceLead = lead)) }
                            }
                            leadExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Part Count (3-6)
        Text(stringResource(R.string.assemble_ctor_parts), style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = desiredPartCount.toFloat(),
                onValueChange = { 
                    desiredPartCount = it.toInt()
                    if (assemble.sourcePathologyId != null) {
                        onBuild(assemble.sourcePathologyId, assemble.sliceLead, desiredPartCount)
                    }
                },
                valueRange = 3f..6f,
                steps = 2,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = desiredPartCount.toString(),
                modifier = Modifier.padding(start = 16.dp),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (assemble.parts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.assemble_ctor_built_format, assemble.parts.size),
                style = MaterialTheme.typography.labelSmall,
                color = com.example.cardiosimulator.ui.theme.Positive
            )
        } else if (assemble.sourcePathologyId != null) {
            Text(
                text = stringResource(R.string.assemble_ctor_build_failed),
                style = MaterialTheme.typography.labelSmall,
                color = com.example.cardiosimulator.ui.theme.Negative
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmPickerDialog(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    onSelect: (PathologyEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()

    // Precompute labels for performance
    val rhythmsWithLabels: List<Pair<PathologyEntry, String>> = remember(rhythms, currentLanguage) {
        rhythms.map { it to getDisplayNameForCtor(it, currentLanguage, false) }
    }

    val filtered: List<Pair<PathologyEntry, String>> = remember(rhythmsWithLabels, searchQuery) {
        if (searchQuery.isBlank()) rhythmsWithLabels
        else rhythmsWithLabels.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.id.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.test_ctor_ecg)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.test_ctor_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(items = filtered) { item: Pair<PathologyEntry, String> ->
                        val entry = item.first
                        val label = item.second
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_close)) }
        }
    )
}

fun getDisplayNameForCtor(entry: PathologyEntry, language: Language, isClinicalMode: Boolean): String {
    return if (isClinicalMode) {
        entry.clinicalCase?.split(',')?.firstOrNull { it.trim().startsWith("title=") }?.substringAfter("title=") 
            ?: (if (language == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn)
    } else {
        if (language == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn
    }
}
