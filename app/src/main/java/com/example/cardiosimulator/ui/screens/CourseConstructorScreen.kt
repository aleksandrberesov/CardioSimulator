package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgTrace
import com.example.cardiosimulator.domain.HtmlBlock
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.ui.components.HtmlBlockEditor
import com.example.cardiosimulator.ui.components.LectureWebView
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.LEAD_ORDER
import com.example.cardiosimulator.ui.panels.CourseSelector
import com.example.cardiosimulator.ui.panels.LectureSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

enum class ConstructorViewMode { EDITOR, PREVIEW, BOTH }

@Composable
fun CourseConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    courseConstructorViewModel: CourseConstructorViewModel,
) {
    val allCourses by appViewModel.courses.collectAsState()
    val courses = remember(allCourses) {
        allCourses.filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
    }
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val selectedCourseId by courseConstructorViewModel.selectedCourseId.collectAsState()
    val lectures by courseConstructorViewModel.lectures.collectAsState()
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val draft by courseConstructorViewModel.draft.collectAsState()
    val blocks by courseConstructorViewModel.blocks.collectAsState()
    val previewLecture by courseConstructorViewModel.previewLecture.collectAsState()
    val isDirty by courseConstructorViewModel.isDirty.collectAsState()
    val isSaving by courseConstructorViewModel.isSaving.collectAsState()
    val answers by courseConstructorViewModel.answers.collectAsState()
    val lastAddedBlockId by courseConstructorViewModel.lastAddedBlockId.collectAsState()
    var isCourseDrawerExpanded by remember { mutableStateOf(false) }
    var isLectureDrawerExpanded by remember { mutableStateOf(false) }

    var viewMode by remember { mutableStateOf(ConstructorViewMode.BOTH) }

    var showNewCourse by remember { mutableStateOf(false) }
    var showNewLecture by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddBlockMenu by remember { mutableStateOf(false) }

    if (showNewCourse) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_new_course),
            label = stringResource(R.string.course_constructor_title_hint),
            initial = "",
            onConfirm = { title ->
                courseConstructorViewModel.createCourse(generateRandomId(), title)
                showNewCourse = false
            },
            onDismiss = { showNewCourse = false },
        )
    }
    if (showNewLecture) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_new_lecture),
            label = stringResource(R.string.course_constructor_title_hint),
            initial = "",
            onConfirm = { title ->
                courseConstructorViewModel.createLecture(generateRandomId(), title)
                showNewLecture = false
            },
            onDismiss = { showNewLecture = false },
        )
    }
    if (showRename) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_rename),
            label = stringResource(R.string.course_constructor_title_hint),
            initial = lectures.find { it.id == selectedLectureId }?.titleEn.orEmpty(),
            onConfirm = { title -> courseConstructorViewModel.renameLecture(title); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.course_constructor_delete)) },
            text = { Text(stringResource(R.string.course_constructor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { courseConstructorViewModel.deleteLecture(); showDelete = false }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            },
        )
    }

    val pathologyRepo = appViewModel.repository
    val resolveEcg = remember(pathologyRepo) {
        { pathologyId: String, lead: Lead? ->
            val leads = if (lead != null) listOf(lead) else LEAD_ORDER
            leads.mapNotNull { l -> pathologyRepo?.leadWaveform(pathologyId, l)?.let { EcgTrace(l, it) } }
        }
    }

    val rhythms by rhythmViewModel.rhythms.collectAsState()

    LaunchedEffect(Unit) {
        courseConstructorViewModel.setLanguage(selectedLanguage.tag)
        courseConstructorViewModel.restore()
    }
    LaunchedEffect(selectedLanguage) {
        courseConstructorViewModel.setLanguage(selectedLanguage.tag)
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                if (selectedLectureId != null && viewMode != ConstructorViewMode.PREVIEW) {
                    Box {
                        FloatingActionButton(onClick = { showAddBlockMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Block")
                        }
                        DropdownMenu(
                            expanded = showAddBlockMenu,
                            onDismissRequest = { showAddBlockMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Header (H1)") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.Header(level = 1, text = "")); showAddBlockMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Header (H2)") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.Header(level = 2, text = "")); showAddBlockMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Paragraph") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.Paragraph(html = "")); showAddBlockMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Image") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.Image(src = "", alt = "")); showAddBlockMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("KaTeX Math") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.KaTeX(expression = "", displayMode = true)); showAddBlockMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("ECG Reference") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.Ecg(pathology = "", lead = null, caption = "")); showAddBlockMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Structured Table") },
                                onClick = { courseConstructorViewModel.addBlock(HtmlBlock.Table(rows = listOf(listOf("", ""), listOf("", "")))); showAddBlockMenu = false }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = previewLecture?.frontMatter?.title?.takeIf { it.isNotBlank() }
                                ?: selectedLectureId
                                ?: stringResource(R.string.course_viewer_select_lecture),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showNewCourse = true }) {
                                Text(stringResource(R.string.course_constructor_new_course))
                            }
                            TextButton(onClick = { showNewLecture = true }) {
                                Text(stringResource(R.string.course_constructor_new_lecture))
                            }
                            TextButton(
                                onClick = { showRename = true },
                                enabled = selectedLectureId != null
                            ) {
                                Text(stringResource(R.string.course_constructor_rename))
                            }
                            TextButton(
                                onClick = { showDelete = true },
                                enabled = selectedLectureId != null
                            ) {
                                Text(stringResource(R.string.course_constructor_delete))
                            }
                            
                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 8.dp))

                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val modes = listOf(
                                    ConstructorViewMode.EDITOR to Icons.Default.EditNote,
                                    ConstructorViewMode.BOTH to Icons.Default.VerticalSplit,
                                    ConstructorViewMode.PREVIEW to Icons.Default.Visibility
                                )
                                modes.forEach { (mode, icon) ->
                                    val selected = viewMode == mode
                                    IconButton(
                                        onClick = { viewMode = mode },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(icon, contentDescription = mode.name, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 8.dp))

                            TextButton(
                                onClick = { courseConstructorViewModel.revert() },
                                enabled = isDirty && !isSaving,
                            ) {
                                Text(stringResource(R.string.course_constructor_revert))
                            }
                            TextButton(
                                onClick = { courseConstructorViewModel.save() },
                                enabled = isDirty && !isSaving,
                            ) {
                                Text(stringResource(R.string.constructor_save))
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (viewMode == ConstructorViewMode.EDITOR || viewMode == ConstructorViewMode.BOTH) {
                        HtmlBlockEditor(
                            appViewModel = appViewModel,
                            rhythms = rhythms,
                            blocks = blocks,
                            onUpdateBlock = { id, updated -> courseConstructorViewModel.updateBlock(id, updated) },
                            onDeleteBlock = { id -> courseConstructorViewModel.deleteBlock(id) },
                            onMoveBlock = { id, delta -> courseConstructorViewModel.moveBlock(id, delta) },
                            scrollToBlockId = lastAddedBlockId,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (viewMode == ConstructorViewMode.BOTH) {
                        VerticalDivider()
                    }
                    if (viewMode == ConstructorViewMode.PREVIEW || viewMode == ConstructorViewMode.BOTH) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val preview = previewLecture
                            if (preview != null) {
                                LectureWebView(
                                    lecture = preview,
                                    resolveEcg = resolveEcg,
                                    answers = answers,
                                    scrollToBlockId = lastAddedBlockId,
                                    onCellEdit = { quizId, row, col, value ->
                                        courseConstructorViewModel.setTableCell(quizId, row, col, value)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.course_viewer_select_lecture),
                                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        SideDrawer(
            isExpanded = isCourseDrawerExpanded,
            onExpandedChange = { isCourseDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                CourseSelector(
                    appViewModel = appViewModel,
                    courses = courses,
                    selectedCourseId = selectedCourseId,
                    onCourseSelect = { courseConstructorViewModel.selectCourse(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            handlerContent = {
                Text(
                    text = stringResource(R.string.course_drawer_title),
                    modifier = Modifier.requiredWidth(64.dp).rotate(-90f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            },
            handlerModifier = Modifier.offset(y = (-40).dp),
            modifier = Modifier.fillMaxHeight(),
        )

        SideDrawer(
            isExpanded = isLectureDrawerExpanded,
            onExpandedChange = { isLectureDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                LectureSelector(
                    lectures = lectures,
                    language = selectedLanguage,
                    selectedLectureId = selectedLectureId,
                    onLectureSelect = { courseConstructorViewModel.selectLecture(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            handlerContent = {
                Text(
                    text = stringResource(R.string.lecture_selector_title),
                    modifier = Modifier.requiredWidth(64.dp).rotate(-90f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            },
            handlerModifier = Modifier.offset(y = 40.dp),
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

@Composable
private fun OneFieldDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.constructor_rename_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.constructor_rename_cancel)) }
        },
    )
}

private fun generateRandomId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..16).map { chars.random() }.joinToString("")
}
