package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgTrace
import com.example.cardiosimulator.domain.HtmlBlock
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.ui.components.HtmlBlockEditor
import com.example.cardiosimulator.ui.components.LectureWebView
import com.example.cardiosimulator.ui.display.LEAD_ORDER
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewMode
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun CourseConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    courseConstructorViewModel: CourseConstructorViewModel,
) {
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val lectures by courseConstructorViewModel.lectures.collectAsState()
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val blocks by courseConstructorViewModel.blocks.collectAsState()
    val previewLecture by courseConstructorViewModel.previewLecture.collectAsState()
    val answers by courseConstructorViewModel.answers.collectAsState()
    val focusedBlockId by courseConstructorViewModel.focusedBlockId.collectAsState()
    val viewMode by courseConstructorViewModel.viewMode.collectAsState()
    
    var showAddBlockMenu by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (viewMode == ConstructorViewMode.EDITOR || viewMode == ConstructorViewMode.BOTH) {
                        HtmlBlockEditor(
                            appViewModel = appViewModel,
                            rhythms = rhythms,
                            blocks = blocks,
                            onUpdateBlock = { id, updated -> courseConstructorViewModel.updateBlock(id, updated) },
                            onDeleteBlock = { id -> courseConstructorViewModel.deleteBlock(id) },
                            onMoveBlock = { id, delta -> courseConstructorViewModel.moveBlock(id, delta) },
                            onImportImage = { name, bytes -> courseConstructorViewModel.importImage(name, bytes) },
                            scrollToBlockId = focusedBlockId,
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
                                    scrollToBlockId = focusedBlockId,
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
    }
}
