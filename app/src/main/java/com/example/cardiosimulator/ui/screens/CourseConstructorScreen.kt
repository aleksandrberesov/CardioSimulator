package com.example.cardiosimulator.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgTrace
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.ui.components.LectureWebView
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.LEAD_ORDER
import com.example.cardiosimulator.ui.panels.CourseSelector
import com.example.cardiosimulator.ui.panels.LectureSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
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
    val allCourses by appViewModel.courses.collectAsState()
    val courses = remember(allCourses) {
        allCourses.filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
    }
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val selectedCourseId by courseConstructorViewModel.selectedCourseId.collectAsState()
    val lectures by courseConstructorViewModel.lectures.collectAsState()
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val draft by courseConstructorViewModel.draft.collectAsState()
    val previewLecture by courseConstructorViewModel.previewLecture.collectAsState()
    val isDirty by courseConstructorViewModel.isDirty.collectAsState()
    val isSaving by courseConstructorViewModel.isSaving.collectAsState()
    val answers by courseConstructorViewModel.answers.collectAsState()
    var isCourseDrawerExpanded by remember { mutableStateOf(false) }
    var isLectureDrawerExpanded by remember { mutableStateOf(false) }

    val pathologyRepo = appViewModel.repository
    val resolveEcg = remember(pathologyRepo) {
        { pathologyId: String, lead: Lead? ->
            val leads = if (lead != null) listOf(lead) else LEAD_ORDER
            leads.mapNotNull { l -> pathologyRepo?.leadWaveform(pathologyId, l)?.let { EcgTrace(l, it) } }
        }
    }

    LaunchedEffect(Unit) {
        courseConstructorViewModel.setLanguage(selectedLanguage.tag)
        courseConstructorViewModel.restore()
    }
    LaunchedEffect(selectedLanguage) {
        courseConstructorViewModel.setLanguage(selectedLanguage.tag)
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    )
                    Row {
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
                OutlinedTextField(
                    value = draft,
                    onValueChange = { courseConstructorViewModel.setHtml(it) },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    enabled = selectedLectureId != null,
                )
                VerticalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val preview = previewLecture
                    if (preview != null) {
                        LectureWebView(
                            lecture = preview,
                            resolveEcg = resolveEcg,
                            answers = answers,
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
                )
            },
            handlerModifier = Modifier.offset(y = 40.dp),
            modifier = Modifier.fillMaxHeight(),
        )
    }
}
