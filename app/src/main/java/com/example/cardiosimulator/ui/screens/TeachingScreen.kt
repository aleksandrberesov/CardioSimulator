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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
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
import com.example.cardiosimulator.domain.ElectrodeFault
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.LectureWebView
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.components.WelcomeOverlay
import com.example.cardiosimulator.ui.components.EosOverlay
import com.example.cardiosimulator.ui.components.TipsOverlay
import com.example.cardiosimulator.ui.dialogs.ComparisonPresetsDialog
import com.example.cardiosimulator.ui.dialogs.ComparisonTargetDialog
import com.example.cardiosimulator.ui.dialogs.SaveComparisonPresetDialog
import com.example.cardiosimulator.ui.dialogs.ElectrodesDialog
import com.example.cardiosimulator.ui.dialogs.Heart3DDialog
import com.example.cardiosimulator.signals.biosppy.EcgFilters
import com.example.cardiosimulator.signals.biosppy.computeSqi
import com.example.cardiosimulator.ui.components.SqiBadge
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

    val welcomeOptOut by appViewModel.prefs?.welcomeOptOut?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }
    var hasDismissedWelcome by rememberSaveable { mutableStateOf(false) }

    val showWelcome = !welcomeOptOut && !hasDismissedWelcome

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
                onDismiss = { dontShowAgain ->
                    hasDismissedWelcome = true
                    appViewModel.setWelcomeOptOut(dontShowAgain)
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
    val description by rhythmViewModel.description.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()

    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }
    var editingPaneIndex by remember { mutableStateOf<Int?>(null) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showRhythmInfo by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            monitorViewModel.setShowElectrodes(false)
            monitorViewModel.setShow3D(false)
            monitorViewModel.setShowEos(false)
            monitorViewModel.setShowTips(false)
            monitorViewModel.setShowRuler(false)
        }
    }

    if (mode.showElectrodes) {
        ElectrodesDialog(
            electrodeState = mode.electrodeState,
            onSelectState = { monitorViewModel.setElectrodeState(it) },
            onDismiss = { monitorViewModel.setShowElectrodes(false) }
        )
    }

    if (mode.show3D) {
        Heart3DDialog(onDismiss = { monitorViewModel.setShow3D(false) })
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
                            val displayWaveforms = remember(waveforms, mode.electrodeState) {
                                ElectrodeFault.apply(waveforms, mode.electrodeState)
                            }
                            LeadsGrid(
                                rows = rows,
                                columns = columns,
                                itemCount = mode.count,
                            ) { index, lead ->
                                // ...
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
                                        val points = lead?.let { displayWaveforms[it] }
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
                                        artifacts = mode.artifacts,
                                        filterType = mode.filterType,
                                        calibration = mode.calibration,
                                        modifier = if (mode.isCompareMode) {
                                            Modifier.clickable { editingPaneIndex = index }
                                        } else Modifier
                                    )
                                }
                            }
                        }

                        val firstLeadForSqi = mode.leadOrder?.firstOrNull() ?: LEAD_ORDER.first()
                        val displayWaveformsForSqi = remember(waveforms, mode.electrodeState) {
                            ElectrodeFault.apply(waveforms, mode.electrodeState)
                        }
                        val sqiSignal = displayWaveformsForSqi[firstLeadForSqi]

                        LaunchedEffect(sqiSignal, mode.calibration.sampleRateHz, mode.filterType, mode.isCompareMode) {
                            val src = sqiSignal
                            if (mode.isCompareMode || src == null || src.values.size <= 100) {
                                monitorViewModel.setSignalQuality(null)
                            } else {
                                val samplingRate = mode.calibration.sampleRateHz.toDouble()
                                val rawSignal = src.values.map { it.toDouble() }.toDoubleArray()
                                val filteredSignal = EcgFilters.apply(rawSignal, mode.filterType, samplingRate)
                                
                                monitorViewModel.setSignalQuality(
                                    computeSqi(filteredSignal, samplingRate)?.copy(lead = firstLeadForSqi)
                                )
                            }
                        }

                        if (mode.showEos) {
                            EosOverlay(
                                onClose = { monitorViewModel.setShowEos(false) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }

                        if (mode.showTips) {
                            TipsOverlay(
                                selectedKind = mode.selectedTipKind,
                                onKindSelected = { monitorViewModel.setSelectedTipKind(it) },
                                onClose = { monitorViewModel.setShowTips(false) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }

                        // Graduation-cap button — standalone "All rhythms" view only (no title bar there). Mirrors the Windows
                        // top-right button; tapping it opens the full-monitor rhythm-info screen below.
                        if (onClose == null && !mode.isCompareMode) {
                            IconButton(
                                onClick = { showRhythmInfo = true },
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School, // graduation-cap (mortarboard) — matches the Windows "Education" glyph
                                    contentDescription = stringResource(R.string.rhythm_info_tooltip)
                                )
                            }
                        }

                        // Full-monitor rhythm-info screen — opaque overlay filling the whole monitor Box. Header (title +
                        // close) over the scrollable details. Mirrors the Windows _infoScreen takeover.
                        if (showRhythmInfo && onClose == null) {
                            RhythmInfoScreen(
                                pathology = selectedRhythm,
                                significantPoints = significantPoints,
                                language = selectedLanguage,
                                description = description,
                                onClose = { showRhythmInfo = false },
                                modifier = Modifier.fillMaxSize()
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
                        appViewModel.operatingModes.find { it.id == OperatingMode.Testing }!!
                    )
                }) {
                    Icon(Icons.Default.Quiz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.teaching_take_test))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    appViewModel.updateOperatingMode(
                        appViewModel.operatingModes.find { it.id == OperatingMode.Examination }!!
                    )
                }) {
                    Icon(Icons.Default.School, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.teaching_take_exam))
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

@Composable
private fun RhythmInfoScreen(
    pathology: PathologyEntry?,
    significantPoints: List<SignificantPoint>,
    language: Language,
    description: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opaque so the monitor behind it is hidden; tonalElevation lifts it above the trace.
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background, tonalElevation = 8.dp) {
        Column(Modifier.fillMaxSize()) {
            // Header: title + close.
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rhythm_info_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            }
            // Scrollable details.
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pathology == null) {
                    Text(text = stringResource(R.string.mode_teaching), style = MaterialTheme.typography.headlineSmall)
                    return@Column
                }
                val primary = if (language == Language.RU) pathology.nameRu ?: pathology.titleEn else pathology.titleEn
                val secondary = if (language == Language.RU) pathology.titleEn else pathology.nameRu
                Text(text = primary, style = MaterialTheme.typography.headlineMedium)
                if (!secondary.isNullOrBlank() && secondary != primary) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.pathology_leads_label)}: ${pathology.leadsCount}",
                    style = MaterialTheme.typography.bodyLarge
                )
                // Distinct marker labels in complex order; the enum's declaration order IS complex order,
                // so sortedBy { ordinal } matches the Windows OrderBy((int)type). Strip <sub> tags.
                val markers = significantPoints
                    .map { it.type }
                    .distinct()
                    .sortedBy { it.ordinal }
                    .joinToString(", ") { it.label.replace("<sub>", "").replace("</sub>", "") }
                if (markers.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.pathology_markers_label)}: $markers",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "${stringResource(R.string.pathology_description_label)}:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
