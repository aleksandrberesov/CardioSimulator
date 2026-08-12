package com.example.cardiosimulator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.generators.TestGenType
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.data.TestImageStore
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.QuestionDifficulty
import com.example.cardiosimulator.domain.QuestionStimulus
import com.example.cardiosimulator.domain.TestQuestion
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.components.AcronymPicker
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
    val editingQuestionId by testConstructorViewModel.editingQuestionId.collectAsState()
    
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()

    if (activeTab == ConstructorTab.GENERATOR) {
        GeneratorView(appViewModel, monitorViewModel, rhythmViewModel, testConstructorViewModel)
    } else if (activeTab == ConstructorTab.BANK && editingQuestionId == null) {
        BankBrowseView(appViewModel, monitorViewModel, rhythmViewModel, testConstructorViewModel)
    } else {
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
                    } else if (activeTab == ConstructorTab.BANK && editingQuestionId != null) {
                        SingleQuestionEditor(appViewModel, monitorViewModel, rhythmViewModel, testConstructorViewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleQuestionEditor(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    viewModel: TestConstructorViewModel
) {
    val editingId by viewModel.editingQuestionId.collectAsState()
    val bankQuestions by viewModel.bankQuestions.collectAsState()
    val question = bankQuestions.find { it.id == editingId } ?: return
    
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val themes by viewModel.themes.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.stopEditingQuestion() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            Text("Edit Question", style = MaterialTheme.typography.titleLarge)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        QuestionEditorCard(
            appViewModel = appViewModel,
            question = question,
            rhythms = rhythms,
            themes = themes,
            onUpdate = { transform -> viewModel.updateBankQuestion(question.id, transform) },
            onRemove = { viewModel.deleteFromBank(question.id); viewModel.stopEditingQuestion() },
            onAddOption = { viewModel.addOption(question.id) },
            onRemoveOption = { optId -> viewModel.removeOption(question.id, optId) },
            onAcronymsChange = { acrs -> viewModel.updateBankAcronyms(question.id, acrs) },
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
            }
        )
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
                onAcronymsChange = { acrs -> viewModel.updateAcronyms(question.id, acrs) },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankBrowseView(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    viewModel: TestConstructorViewModel
) {
    val bankQuestions by viewModel.bankQuestions.collectAsState()
    val filteredQuestions by viewModel.filteredBankQuestions.collectAsState()
    val page by viewModel.bankPage.collectAsState()
    val themes by viewModel.themes.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    
    val pageSize = 8
    val totalPages = (filteredQuestions.size + pageSize - 1) / pageSize
    val pagedQuestions = filteredQuestions.drop(page * pageSize).take(pageSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        BankHeader(
            bankCount = bankQuestions.size,
            themeCount = themes.size,
            rhythmCount = rhythms.size
        )

        Spacer(modifier = Modifier.height(24.dp))

        BankFilters(
            viewModel = viewModel,
            themes = themes,
            rhythms = rhythms,
            appViewModel = appViewModel
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (pagedQuestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.test_gen_err_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(pagedQuestions, key = { it.id }) { question ->
                        BankQuestionCard(
                            question = question,
                            onEdit = { viewModel.startEditingQuestion(question.id) },
                            onDelete = { viewModel.deleteFromBank(question.id) },
                            onAdd = { viewModel.addFromBank(question) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PaginationFooter(
            currentPage = page,
            totalPages = totalPages,
            totalItems = filteredQuestions.size,
            bankCount = bankQuestions.size,
            onPageChange = { viewModel.setBankPage(it) }
        )
    }
}

@Composable
fun BankHeader(bankCount: Int, themeCount: Int, rhythmCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stringResource(R.string.bank2_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.bank2_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip(label = stringResource(R.string.bank2_stat_questions), value = bankCount.toString())
            StatChip(label = stringResource(R.string.bank2_stat_themes), value = themeCount.toString())
            StatChip(label = stringResource(R.string.bank2_stat_rhythms), value = rhythmCount.toString())
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankFilters(
    viewModel: TestConstructorViewModel,
    themes: List<String>,
    rhythms: List<PathologyEntry>,
    appViewModel: AppViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val selectedRhythm by viewModel.selectedBankRhythm.collectAsState()
    val selectedTypes by viewModel.selectedBankTypes.collectAsState()
    
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text(stringResource(R.string.bank2_search_placeholder)) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                BankActionButtons(viewModel, context)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Theme Dropdown
                var themeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = !themeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedTheme ?: stringResource(R.string.quick_filter_all),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.bank2_section_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quick_filter_all)) },
                            onClick = { viewModel.setSelectedTheme(null); themeExpanded = false }
                        )
                        themes.forEach { theme ->
                            DropdownMenuItem(
                                text = { Text(theme) },
                                onClick = { viewModel.setSelectedTheme(theme); themeExpanded = false }
                            )
                        }
                    }
                }

                // Rhythm Dropdown
                var rhythmExpanded by remember { mutableStateOf(false) }
                val currentLang by appViewModel.selectedLanguage.collectAsState()
                ExposedDropdownMenuBox(
                    expanded = rhythmExpanded,
                    onExpandedChange = { rhythmExpanded = !rhythmExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    val selectedName = rhythms.find { it.id == selectedRhythm }?.let {
                        if (currentLang == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
                    } ?: stringResource(R.string.bank2_all_rhythms)

                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.test_gen_rhythm_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rhythmExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = rhythmExpanded,
                        onDismissRequest = { rhythmExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bank2_all_rhythms)) },
                            onClick = { viewModel.setSelectedBankRhythm(null); rhythmExpanded = false }
                        )
                        rhythms.forEach { rhythm ->
                            DropdownMenuItem(
                                text = { Text(if (currentLang == Language.RU) rhythm.nameRu ?: rhythm.titleEn else rhythm.titleEn) },
                                onClick = { viewModel.setSelectedBankRhythm(rhythm.id); rhythmExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Type Tags
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bank2_type_label), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.width(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTypes.isEmpty(),
                        onClick = { viewModel.clearBankTypes() },
                        label = { Text(stringResource(R.string.bank2_type_all)) }
                    )
                    
                    val types = listOf(
                        TestGenType.Image to stringResource(R.string.bank2_type_image),
                        TestGenType.Detect to stringResource(R.string.bank2_type_detect),
                        TestGenType.Assemble to stringResource(R.string.bank2_type_assemble),
                        TestGenType.Questions to stringResource(R.string.bank2_type_case)
                    )
                    
                    types.forEach { (type, label) ->
                        FilterChip(
                            selected = selectedTypes.contains(type),
                            onClick = { viewModel.toggleBankType(type) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BankActionButtons(viewModel: TestConstructorViewModel, context: android.content.Context) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val themes by viewModel.themes.collectAsState()

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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.newBankQuestion() }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.test_gen_new))
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.test_ctor_bank_import))
        }
        OutlinedButton(onClick = { exportLauncher.launch("question_bank.json") }) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.test_ctor_bank_export))
        }
        IconButton(onClick = { showThemeDialog = true }) {
            Icon(Icons.Default.Settings, contentDescription = null)
        }
    }

    if (showThemeDialog) {
        ThemeManagerDialog(
            themes = themes,
            courses = emptyList(), // Can be updated if needed
            onAdd = { viewModel.addTheme(it) },
            onDelete = { viewModel.deleteTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun BankQuestionCard(
    question: TestQuestion,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: #index · id, type, difficulty
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${question.number} · ID: ${question.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.weight(1f))
                
                TypeBadge(question)
                Spacer(modifier = Modifier.width(8.dp))
                DifficultyBadge(question.difficulty)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stimulus Placeholder
            StimulusPlaceholder(question)

            Spacer(modifier = Modifier.height(12.dp))

            // Meta Chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (question.theme != null) {
                    MetaChip(icon = Icons.Default.Book, label = question.theme)
                }
                if (question.pathologyId != null) {
                    MetaChip(icon = Icons.Default.Favorite, label = question.pathologyId)
                }
                question.acronyms.forEach { acr ->
                    MetaChip(icon = Icons.Default.Bookmark, label = acr)
                }
                question.tagList.forEach { tag ->
                    MetaChip(icon = Icons.Default.Tag, label = tag)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Answers Preview (2 columns)
            AnswersPreview(question)

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun TypeBadge(question: TestQuestion) {
    val (label, color) = when {
        question.isAssembly -> stringResource(R.string.bank2_type_assemble) to Color(0xFF673AB7)
        question.stimulus == QuestionStimulus.Image -> stringResource(R.string.bank2_type_image) to Color(0xFF2196F3)
        question.stimulus == QuestionStimulus.Ecg -> stringResource(R.string.bank2_type_detect) to Color(0xFFFF9800)
        else -> stringResource(R.string.bank2_type_case) to Color(0xFF4CAF50)
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun DifficultyBadge(difficulty: QuestionDifficulty?) {
    if (difficulty == null) return
    val (label, color) = when (difficulty) {
        QuestionDifficulty.Easy -> stringResource(R.string.diff_easy) to Color(0xFF4CAF50)
        QuestionDifficulty.Medium -> stringResource(R.string.diff_medium) to Color(0xFFFFC107)
        QuestionDifficulty.Hard -> stringResource(R.string.diff_hard) to Color(0xFFF44336)
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun StimulusPlaceholder(question: TestQuestion) {
    val text = when {
        question.isAssembly -> stringResource(R.string.bank2_stimulus_assemble)
        question.stimulus == QuestionStimulus.Image -> stringResource(R.string.bank2_stimulus_image)
        question.stimulus == QuestionStimulus.Ecg -> stringResource(R.string.bank2_stimulus_ecg)
        else -> return
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun MetaChip(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun AnswersPreview(question: TestQuestion) {
    val options = question.options
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.heightIn(max = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false
    ) {
        gridItems(options) { option ->
            val isCorrect = option.id == question.correctOptionId
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isCorrect) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = if (isCorrect) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun PaginationFooter(currentPage: Int, totalPages: Int, totalItems: Int, bankCount: Int, onPageChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.bank2_pagination_format, totalItems, bankCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 0) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
            }
            
            // Simple page buttons
            for (i in 0 until totalPages.coerceAtMost(5)) {
                TextButton(
                    onClick = { onPageChange(i) },
                    colors = if (i == currentPage) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.textButtonColors()
                ) {
                    Text((i + 1).toString())
                }
            }
            if (totalPages > 5) {
                Text("…", style = MaterialTheme.typography.bodyLarge)
                TextButton(
                    onClick = { onPageChange(totalPages - 1) },
                    colors = if (currentPage == totalPages - 1) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.textButtonColors()
                ) {
                    Text(totalPages.toString())
                }
            }

            IconButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < totalPages - 1) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
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
    onAcronymsChange: (List<String>) -> Unit,
    onPreview: (String?) -> Unit,
    onToggleAssembly: (Boolean) -> Unit = {},
    onBuildAssembly: (String, com.example.cardiosimulator.domain.Lead, Int) -> Unit = { _, _, _ -> },
    extraActions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = TestImageStore.copyImageToBank(
                context, it, File(context.filesDir, AppViewModel.TEST_IMAGES_DIR), question.id
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

            Spacer(modifier = Modifier.height(16.dp))

            AcronymPicker(
                selectedAcronyms = question.acronyms,
                onAcronymsChange = onAcronymsChange,
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

                    if (question.imagePath != null && question.imagePath != "pending") {
                        IconButton(onClick = {
                            TestImageStore.deleteImageFromBank(
                                File(context.filesDir, AppViewModel.TEST_IMAGES_DIR),
                                question.id
                            )
                            onUpdate { it.copy(imagePath = null) }
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

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

            // Theme, Difficulty & Tags
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

                var diffExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = diffExpanded,
                    onExpandedChange = { diffExpanded = !diffExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    val label = when (question.difficulty) {
                        QuestionDifficulty.Easy -> stringResource(R.string.diff_easy)
                        QuestionDifficulty.Medium -> stringResource(R.string.diff_medium)
                        QuestionDifficulty.Hard -> stringResource(R.string.diff_hard)
                        null -> stringResource(R.string.diff_unset)
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ls_detail_difficulty)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = diffExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = diffExpanded,
                        onDismissRequest = { diffExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.diff_unset)) },
                            onClick = {
                                onUpdate { it.copy(difficulty = null) }
                                diffExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.diff_easy)) },
                            onClick = {
                                onUpdate { it.copy(difficulty = QuestionDifficulty.Easy) }
                                diffExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.diff_medium)) },
                            onClick = {
                                onUpdate { it.copy(difficulty = QuestionDifficulty.Medium) }
                                diffExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.diff_hard)) },
                            onClick = {
                                onUpdate { it.copy(difficulty = QuestionDifficulty.Hard) }
                                diffExpanded = false
                            }
                        )
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

        // Row 1: Source Rhythm
        Row(modifier = Modifier.fillMaxWidth()) {
            var showSourcePicker by remember { mutableStateOf(false) }
            val source = rhythms.find { it.id == assemble.sourcePathologyId }
            val sourceLabel = source?.let { getDisplayNameForCtor(it, currentLanguage, false) } ?: stringResource(R.string.assemble_ctor_none)

            OutlinedTextField(
                value = sourceLabel,
                onValueChange = {},
                readOnly = true,
                label = { 
                    Text(
                        text = stringResource(R.string.assemble_ctor_source),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .clickable { showSourcePicker = true }
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Lead and Parts
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Lead
            var leadExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = leadExpanded,
                onExpandedChange = { leadExpanded = !leadExpanded },
                modifier = Modifier.weight(1f)
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

            // Part Count (3-6)
            Column(modifier = Modifier.weight(1f)) {
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
            }
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
                    items(filtered) { item: Pair<PathologyEntry, String> ->
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

@Composable
fun GeneratorView(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    viewModel: TestConstructorViewModel
) {
    val tests = appViewModel.testRepository?.tests() ?: emptyList()
    val bankQuestions by viewModel.bankQuestions.collectAsState()
    val themes by viewModel.themes.collectAsState()
    val rhythms by rhythmViewModel.rhythms.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        GenHeader(
            onOpenBank = { viewModel.setTab(ConstructorTab.BANK) },
            onNewTest = { viewModel.newTest() }
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Ready Tests + Stats
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ReadyTestsCard(
                    modifier = Modifier.weight(1f),
                    tests = tests,
                    onEdit = { viewModel.load(it.testId) },
                    onDelete = { viewModel.load(it.testId); viewModel.deleteTest(); appViewModel.testRepository?.reload() }
                )
                BankStatsCard(
                    bankCount = bankQuestions.size,
                    testCount = tests.size,
                    rhythmCount = rhythms.size,
                    themeCount = themes.size
                )
            }

            // Right Column: Generator
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                GenConstructorCard(viewModel, themes, rhythms)
            }
        }
    }
}

@Composable
fun GenHeader(onOpenBank: () -> Unit, onNewTest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stringResource(R.string.test_gen_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.test_gen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenBank) {
                Icon(Icons.Default.Storage, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.test_gen_open_bank))
            }
            Button(onClick = onNewTest) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.test_gen_new))
            }
        }
    }
}

@Composable
fun ReadyTestsCard(
    modifier: Modifier = Modifier,
    tests: List<com.example.cardiosimulator.domain.Test>,
    onEdit: (com.example.cardiosimulator.domain.Test) -> Unit,
    onDelete: (com.example.cardiosimulator.domain.Test) -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.test_gen_ready),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            if (tests.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.test_gen_ready_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tests) { test ->
                        TestItem(test, onEdit, onDelete)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun TestItem(
    test: com.example.cardiosimulator.domain.Test,
    onEdit: (com.example.cardiosimulator.domain.Test) -> Unit,
    onDelete: (com.example.cardiosimulator.domain.Test) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(test.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val minutes = (test.questions.size * test.questionTimeSeconds) / 60
            val meta = if (minutes > 0) {
                stringResource(R.string.test_gen_ready_meta_format, test.questions.size, minutes)
            } else {
                stringResource(R.string.test_gen_ready_untimed_format, test.questions.size)
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = { onEdit(test) }) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { onDelete(test) }) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun BankStatsCard(bankCount: Int, testCount: Int, rhythmCount: Int, themeCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.test_gen_bank_subtitle).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.test_gen_stat_questions), bankCount.toString())
                StatItem(stringResource(R.string.test_gen_stat_tests), testCount.toString())
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.test_gen_stat_rhythms), rhythmCount.toString())
                StatItem(stringResource(R.string.test_gen_stat_themes), themeCount.toString())
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun GenConstructorCard(
    viewModel: TestConstructorViewModel,
    themes: List<String>,
    rhythms: List<PathologyEntry>
) {
    val selectedTypes by viewModel.selectedGenTypes.collectAsState()
    val selectedThemes by viewModel.selectedGenThemes.collectAsState()
    val selectedRhythms by viewModel.selectedGenRhythms.collectAsState()
    val isOrMode by viewModel.isGenOrMode.collectAsState()
    val count by viewModel.genCount.collectAsState()
    val minutes by viewModel.genTimeMinutes.collectAsState()

    Card(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.test_gen_ctor_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.test_gen_step_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.height(24.dp))

            StepIndicator(step = 1, title = stringResource(R.string.test_gen_step1))
            Spacer(Modifier.height(16.dp))
            GenTypeGrid(selectedTypes) { viewModel.toggleGenType(it) }

            Spacer(Modifier.height(32.dp))

            StepIndicator(step = 2, title = stringResource(R.string.test_gen_step2))
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.test_gen_pick_topic), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            // Themes selection
            Text(stringResource(R.string.test_gen_topic_label), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themes.forEach { theme ->
                    FilterChip(
                        selected = selectedThemes.contains(theme),
                        onClick = { viewModel.toggleGenTheme(theme) },
                        label = { Text(theme) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // OR/AND toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.test_gen_mode_label), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(16.dp))
                Switch(checked = !isOrMode, onCheckedChange = { viewModel.setGenOrMode(!it) })
                Spacer(Modifier.width(8.dp))
                Text(if (isOrMode) "OR (или)" else "AND (+)", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))

            // Rhythms selection
            Text(stringResource(R.string.test_gen_rhythm_label), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rhythms.take(12).forEach { rhythm ->
                    FilterChip(
                        selected = selectedRhythms.contains(rhythm.id),
                        onClick = { viewModel.toggleGenRhythm(rhythm.id) },
                        label = { Text(rhythm.titleEn) } // Simplified for now
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Params
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = count.toString(),
                    onValueChange = { viewModel.setGenCount(it.toIntOrNull() ?: 0) },
                    label = { Text(stringResource(R.string.test_gen_count)) },
                    modifier = Modifier.weight(1f),
                    suffix = { Text(stringResource(R.string.test_gen_count_suffix)) }
                )
                OutlinedTextField(
                    value = minutes.toString(),
                    onValueChange = { viewModel.setGenTimeMinutes(it.toIntOrNull() ?: 0) },
                    label = { Text(stringResource(R.string.test_gen_time)) },
                    modifier = Modifier.weight(1f),
                    suffix = { Text(stringResource(R.string.test_gen_time_suffix)) }
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.generateTest() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedTypes.isNotEmpty() && (selectedThemes.isNotEmpty() || selectedRhythms.isNotEmpty())
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.test_gen_generate))
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.test_gen_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun StepIndicator(step: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GenTypeGrid(selected: Set<TestGenType>, onToggle: (TestGenType) -> Unit) {
    val types = listOf(
        TestGenType.Questions to (R.string.test_gen_type_questions to Icons.Default.Quiz),
        TestGenType.Image to (R.string.test_gen_type_image to Icons.Default.Image),
        TestGenType.Detect to (R.string.test_gen_type_detect to Icons.Default.Waves),
        TestGenType.Assemble to (R.string.test_gen_type_assemble to Icons.Default.Extension),
        TestGenType.Clinical to (R.string.test_gen_type_clinical to Icons.Default.Assignment)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(types) { (type, data) ->
            GenTypeCard(
                label = stringResource(data.first),
                icon = data.second,
                isSelected = selected.contains(type),
                onClick = { onToggle(type) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenTypeCard(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        maxItemsInEachRow = maxItemsInEachRow,
        content = content
    )
}
