package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgTrace
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.ui.components.LectureWebView
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.LEAD_ORDER
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.LectureSelector
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseViewerViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun TeachingScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    courseViewerViewModel: CourseViewerViewModel,
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val courses by appViewModel.courses.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val comparisonWaveforms by rhythmViewModel.comparisonWaveforms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }
    var editingPaneIndex by remember { mutableStateOf<Int?>(null) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    val mode by monitorViewModel.monitorMode.collectAsState()
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()
    
    val selectedCourseId by courseViewerViewModel.selectedCourseId.collectAsState()
    val appSelectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val lectures by courseViewerViewModel.lectures.collectAsState()
    val selectedLectureId by courseViewerViewModel.selectedLectureId.collectAsState()
    val viewerLecture by courseViewerViewModel.lecture.collectAsState()
    var showCourseOverlay by remember { mutableStateOf(false) }

    val pathologyRepo = appViewModel.repository
    val resolveEcg = remember(pathologyRepo) {
        { pathologyId: String, lead: Lead? ->
            val leads = if (lead != null) listOf(lead) else LEAD_ORDER
            leads.mapNotNull { l -> pathologyRepo?.leadWaveform(pathologyId, l)?.let { EcgTrace(l, it) } }
        }
    }

    LaunchedEffect(Unit) {
        courseViewerViewModel.setLanguage(selectedLanguage.tag)
        courseViewerViewModel.restore()
    }
    LaunchedEffect(selectedLanguage) {
        courseViewerViewModel.setLanguage(selectedLanguage.tag)
    }

    LaunchedEffect(appSelectedCourseId) {
        if (appSelectedCourseId != null && appSelectedCourseId != AppViewModel.ALL_RHYTHMS_ID) {
            courseViewerViewModel.selectCourse(appSelectedCourseId!!)
        }
    }

    LaunchedEffect(mode.comparisonTargets) {
        mode.comparisonTargets.forEach { (index, target) ->
            rhythmViewModel.loadComparisonWaveform(index, target.pathologyId, target.lead)
        }
    }

    LaunchedEffect(mode.isCompareMode) {
        if (mode.isCompareMode && mode.comparisonPresets.isNotEmpty() && mode.comparisonTargets.isEmpty()) {
            showPresetsDialog = true
        }
    }

    if (showPresetsDialog) {
        ComparisonPresetsDialog(
            monitorViewModel = monitorViewModel,
            onDismiss = { 
                showPresetsDialog = false
                if (mode.comparisonTargets.isEmpty()) monitorViewModel.toggleCompareMode()
            },
            onNewSchemaClick = { 
                showPresetsDialog = false
            },
            onPresetSelected = { preset ->
                monitorViewModel.applyPreset(preset)
                showPresetsDialog = false
            }
        )
    }

    if (showSavePresetDialog) {
        SaveComparisonPresetDialog(
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                monitorViewModel.saveCurrentAsPreset(name)
                showSavePresetDialog = false
            }
        )
    }

    editingPaneIndex?.let { index ->
        val currentTarget = mode.comparisonTargets[index]
        ComparisonTargetDialog(
            appViewModel = appViewModel,
            rhythms = rhythms,
            onDismiss = { editingPaneIndex = null },
            onTargetSelected = { target ->
                monitorViewModel.setComparisonTarget(index, target)
                editingPaneIndex = null
            },
            initialPathologyId = currentTarget?.pathologyId,
            initialLead = currentTarget?.lead
        )
    }

    val rhythmDrawer = @Composable {
        SideDrawer(
            isExpanded = isRhythmDrawerExpanded,
            onExpandedChange = { isRhythmDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    rhythms = rhythms,
                    selectedId = selectedRhythm?.id,
                    onRhythmSelect = { rhythmViewModel.selectRhythm(it.id) },
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
            modifier = Modifier.fillMaxHeight()
        )
    }

    Row(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        if (isDrawerFixed) {
            rhythmDrawer()
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().middleSectionCenter(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val displayTitle = if (mode.isCompareMode) {
                    stringResource(R.string.monitor_compare_mode)
                } else {
                    selectedRhythm?.let {
                        if (selectedLanguage == Language.RU)
                            it.nameRu ?: it.titleEn
                        else
                            it.titleEn
                    } ?: stringResource(R.string.constructor_no_pathology_selected)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        if (mode.isCompareMode && mode.comparisonTargets.isNotEmpty()) {
                            TextButton(onClick = { showSavePresetDialog = true }) {
                                Text(stringResource(R.string.constructor_save))
                            }
                        }
                    }
                }

                Monitor(
                    modifier = Modifier.weight(1f).padding(
                        top = 8.dp,
                        start = if (isDrawerFixed) 0.dp else 24.dp
                    ),
                    monitorViewModel = monitorViewModel,
                ) { rows, columns, xOffset, scheme ->
                    LeadsGrid(
                        rows = rows,
                        columns = columns,
                        itemCount = mode.count,
                    ) { index, lead ->
                        if (mode.isCompareMode && !mode.comparisonTargets.containsKey(index)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray.copy(alpha = 0.5f))
                                    .clickable { editingPaneIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.monitor_compare_placeholder),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.DarkGray
                                )
                            }
                        } else {
                            val (displayPoints, displayTitle) = if (mode.isCompareMode) {
                                val target = mode.comparisonTargets[index]
                                val points = comparisonWaveforms[index] ?: Points(emptyList())
                                val title = if (target != null) {
                                    val pathology = rhythms.find { it.id == target.pathologyId }
                                    val pathTitle = if (selectedLanguage == Language.RU) pathology?.nameRu ?: pathology?.titleEn else pathology?.titleEn
                                    "${pathTitle ?: "???"} (${target.lead.name})"
                                } else {
                                    "" // Should not happen due to the placeholder box above
                                }
                                points to title
                            } else {
                                val points = lead?.let { waveforms[it] }
                                    ?.takeIf { it.values.size >= 2 }
                                    ?: Points(emptyList<Float>())
                                points to (lead?.name ?: "")
                            }

                            LeadView(
                                points = displayPoints,
                                title = displayTitle,
                                isRunning = mode.isRunning,
                                xOffsetPx = xOffset,
                                gridScheme = scheme,
                                isCompareMode = mode.isCompareMode,
                                modifier = if (mode.isCompareMode) {
                                    Modifier.clickable { editingPaneIndex = index }
                                } else Modifier
                            )
                        }
                    }
                }
            }

            if (!isDrawerFixed) {
                Box(modifier = Modifier.fillMaxHeight().align(Alignment.TopStart)) {
                    rhythmDrawer()
                }
            }

            // Course-viewer entry point — additive; does not touch the monitor.
            IconButton(
                onClick = { showCourseOverlay = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = stringResource(R.string.course_drawer_title),
                    tint = Color.White
                )
            }

            var hasOpenedCourseViewer by remember { mutableStateOf(false) }
            if (showCourseOverlay) hasOpenedCourseViewer = true

            if (hasOpenedCourseViewer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Using translation and alpha to hide/show while keeping the WebView in composition
                            // so that its scroll position and state are preserved.
                            translationX = if (showCourseOverlay) 0f else 10000f
                            alpha = if (showCourseOverlay) 1f else 0f
                        }
                ) {
                    CourseViewerOverlay(
                        courses = courses,
                        selectedCourseId = selectedCourseId,
                        lectures = lectures,
                        selectedLectureId = selectedLectureId,
                        lecture = viewerLecture,
                        language = selectedLanguage,
                        resolveEcg = resolveEcg,
                        onLectureSelect = { courseViewerViewModel.selectLecture(it.id) },
                        onClose = { showCourseOverlay = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseViewerOverlay(
    courses: List<CourseEntry>,
    selectedCourseId: String?,
    lectures: List<LectureEntry>,
    selectedLectureId: String?,
    lecture: Lecture?,
    language: Language,
    resolveEcg: (String, Lead?) -> List<EcgTrace>,
    onLectureSelect: (LectureEntry) -> Unit,
    onClose: () -> Unit,
) {
    var isLecturesDrawerExpanded by remember { mutableStateOf(false) }

    val filteredCourses = remember(courses) {
        courses.filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = lecture?.frontMatter?.title?.takeIf { it.isNotBlank() }
                            ?: filteredCourses.find { it.id == selectedCourseId }?.let { courseDisplayName(it, language) }
                            ?: stringResource(R.string.course_drawer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (lecture != null) {
                    LectureWebView(
                        lecture = lecture,
                        resolveEcg = resolveEcg,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.course_viewer_select_lecture),
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SideDrawer(
                    isExpanded = isLecturesDrawerExpanded,
                    onExpandedChange = { isLecturesDrawerExpanded = it },
                    drawerWidth = 300.dp,
                    drawerContent = {
                        LectureSelector(
                            lectures = lectures,
                            language = language,
                            selectedLectureId = selectedLectureId,
                            onLectureSelect = {
                                onLectureSelect(it)
                                isLecturesDrawerExpanded = false
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    handlerContent = {
                        Text(
                            text = stringResource(R.string.lecture_selector_title),
                            modifier = Modifier
                                .requiredWidth(64.dp)
                                .rotate(-90f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.fillMaxHeight().align(Alignment.TopStart)
                )
            }
        }
    }
}

private fun courseDisplayName(course: CourseEntry, language: Language): String =
    if (language == Language.RU) course.nameRu ?: course.titleEn else course.titleEn
