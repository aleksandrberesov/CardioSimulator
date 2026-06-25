package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.ui.components.LectureWebView
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.components.WelcomeOverlay
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
    val courses by appViewModel.courses.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    val selectedCourseId by courseViewerViewModel.selectedCourseId.collectAsState()
    val appSelectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val lectures by courseViewerViewModel.lectures.collectAsState()
    val selectedLectureId by courseViewerViewModel.selectedLectureId.collectAsState()
    val viewerLecture by courseViewerViewModel.lecture.collectAsState()
    val showMonitorOverlay by appViewModel.showMonitorOverlay.collectAsState()

    val welcomeShown by appViewModel.prefs?.welcomeShown?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }
    var showWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(welcomeShown) {
        if (!welcomeShown) {
            showWelcome = true
        }
    }

    var lastBuiltMode by rememberSaveable { mutableStateOf<OperatingMode?>(null) }
    LaunchedEffect(Unit) {
        if (lastBuiltMode != OperatingMode.Teaching) {
            appViewModel.selectCourse(AppViewModel.ALL_RHYTHMS_ID)
            lastBuiltMode = OperatingMode.Teaching
        }
    }

    val pathologyRepo = appViewModel.repository
    val resolveEcg = remember(pathologyRepo) {
        { pathologyId: String, leads: List<Lead> ->
            val requested = leads.ifEmpty { LEAD_ORDER }
            requested.mapNotNull { l -> pathologyRepo?.leadWaveform(pathologyId, l)?.let { EcgTrace(l, it) } }
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
        } else {
            courseViewerViewModel.closeLecture()
        }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        val isAllRhythms = appSelectedCourseId == AppViewModel.ALL_RHYTHMS_ID || appSelectedCourseId == null

        if (isAllRhythms) {
            MonitorOverlay(
                monitorViewModel = monitorViewModel,
                rhythmViewModel = rhythmViewModel,
                appViewModel = appViewModel,
                onClose = null
            )
        } else {
            CourseViewerOverlay(
                appViewModel = appViewModel,
                rhythmViewModel = rhythmViewModel,
                courses = courses,
                selectedCourseId = appSelectedCourseId,
                lectures = lectures,
                selectedLectureId = selectedLectureId,
                lecture = viewerLecture,
                language = selectedLanguage,
                resolveEcg = resolveEcg,
                onLectureSelect = { courseViewerViewModel.selectLecture(it.id) },
                onClose = { /* Not used in main mode */ },
                onMonitorClick = { appViewModel.setShowMonitorOverlay(true) }
            )

            if (showMonitorOverlay) {
                MonitorOverlay(
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    appViewModel = appViewModel,
                    onClose = { appViewModel.setShowMonitorOverlay(false) }
                )
            }
        }

        if (showWelcome) {
            WelcomeOverlay(
                onDismiss = {
                    showWelcome = false
                    appViewModel.setWelcomeShown(true)
                }
            )
        }
    }
}

@Composable
private fun PathologyDescription(
    pathology: com.example.cardiosimulator.domain.PathologyEntry?,
    language: Language,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (pathology != null) {
            Text(
                text = if (language == Language.RU) pathology.nameRu ?: pathology.titleEn else pathology.titleEn,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.constructor_id_label, pathology.id),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp).width(200.dp))
            Text(
                text = "Описание патологии будет доступно в следующих обновлениях.", // Placeholder
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.constructor_no_pathology_selected),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonitorOverlay(
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    appViewModel: AppViewModel,
    onClose: (() -> Unit)? = null,
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val comparisonWaveforms by rhythmViewModel.comparisonWaveforms.collectAsState()
    val significantPoints by rhythmViewModel.significantPoints.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()

    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }
    var editingPaneIndex by remember { mutableStateOf<Int?>(null) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            monitorViewModel.setShowElectrodes(false)
            monitorViewModel.setShow3D(false)
            monitorViewModel.setShowEos(false)
            monitorViewModel.setShowTips(false)
        }
    }

    if (mode.showElectrodes) {
        com.example.cardiosimulator.ui.components.ElectrodesDialog(onDismiss = { monitorViewModel.setShowElectrodes(false) })
    }

    if (mode.show3D) {
        com.example.cardiosimulator.ui.components.Heart3DDialog(onDismiss = { monitorViewModel.setShow3D(false) })
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isDrawerFixed) {
                rhythmDrawer()
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            }
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().middleSectionCenter(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (onClose != null || (mode.isCompareMode && mode.comparisonTargets.isNotEmpty())) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (mode.isCompareMode && mode.comparisonTargets.isNotEmpty()) {
                                    TextButton(onClick = { showSavePresetDialog = true }) {
                                        Text(stringResource(R.string.constructor_save))
                                    }
                                }
                                if (onClose != null) {
                                    IconButton(onClick = onClose) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.cd_close)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Monitor(
                            modifier = Modifier.fillMaxSize().padding(
                                top = 8.dp,
                                start = 0.dp
                            ),
                            monitorViewModel = monitorViewModel,
                        ) { rows, columns, xOffset, scheme ->
                            LeadsGrid(
                                rows = rows,
                                columns = columns,
                                itemCount = mode.count,
                            ) { index, lead ->
                                // ... (already updated LeadView call)
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
                                            ""
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
                                        significantPoints = if (mode.isCompareMode) emptyList() else significantPoints,
                                        showImpulseLabels = mode.showImpulseLabels,
                                        modifier = if (mode.isCompareMode) {
                                            Modifier.clickable { editingPaneIndex = index }
                                        } else Modifier
                                    )
                                }
                            }
                        }

                        if (mode.showEos) {
                            com.example.cardiosimulator.ui.components.EosOverlay(
                                onClose = { monitorViewModel.setShowEos(false) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }

                        if (mode.showTips) {
                            com.example.cardiosimulator.ui.components.TipsOverlay(
                                selectedKind = mode.selectedTipKind,
                                onKindSelected = { monitorViewModel.setSelectedTipKind(it) },
                                onClose = { monitorViewModel.setShowTips(false) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                if (!isDrawerFixed) {
                    Box(modifier = Modifier.fillMaxHeight().align(Alignment.TopStart)) {
                        rhythmDrawer()
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseViewerOverlay(
    appViewModel: AppViewModel,
    rhythmViewModel: RhythmViewModel,
    courses: List<CourseEntry>,
    selectedCourseId: String?,
    lectures: List<LectureEntry>,
    selectedLectureId: String?,
    lecture: Lecture?,
    language: Language,
    resolveEcg: (String, List<Lead>) -> List<EcgTrace>,
    onLectureSelect: (LectureEntry) -> Unit,
    onClose: () -> Unit,
    onMonitorClick: () -> Unit = {},
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    appViewModel.updateOperatingMode(
                        appViewModel.operatingModes.find { it.id == OperatingMode.Examination }!!
                    )
                }) {
                    Icon(Icons.Default.Quiz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.teaching_take_test))
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onMonitorClick) {
                    Icon(
                        imageVector = Icons.Default.MonitorHeart,
                        contentDescription = stringResource(R.string.monitor_overlay_title)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (lecture != null) {
                    LectureWebView(
                        lecture = lecture,
                        resolveEcg = resolveEcg,
                        onMonitorClick = onMonitorClick,
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

private fun courseDisplayName(course: CourseEntry, language: Language): String =
    if (language == Language.RU) course.nameRu ?: course.titleEn else course.titleEn
