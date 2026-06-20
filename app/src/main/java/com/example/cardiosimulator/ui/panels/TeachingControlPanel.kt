package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseViewerViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun TeachingControlPanel(
    appViewModel: AppViewModel,
    courseViewerViewModel: CourseViewerViewModel,
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel = viewModel(),
    onStartStopClick: (Boolean) -> Unit = {},
) {
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val courses by appViewModel.courses.collectAsState()
    val selectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()

    val lectures by courseViewerViewModel.lectures.collectAsState()
    val selectedLectureId by courseViewerViewModel.selectedLectureId.collectAsState()
    val selectedLecture = lectures.find { it.id == selectedLectureId }

    Row(
        //modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var courseExpanded by remember { mutableStateOf(false) }
        val selectedCourse = courses.find { it.id == selectedCourseId }
        val selectedLabel = selectedCourse?.let { courseDisplayName(it, currentLanguage) } ?: ""

        Box {
            Tab(
                text = selectedLabel,
                onClick = { if (courses.isNotEmpty()) courseExpanded = true },
                modifier = Modifier.padding(horizontal = 4.dp).width(200.dp)
            )
            if (courses.isNotEmpty()) {
                DropdownMenu(
                    expanded = courseExpanded,
                    onDismissRequest = { courseExpanded = false }
                ) {
                    courses.forEach { course ->
                        DropdownMenuItem(
                            text = { Text(courseDisplayName(course, currentLanguage)) },
                            onClick = {
                                appViewModel.selectCourse(course.id)
                                courseExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedCourseId != AppViewModel.ALL_RHYTHMS_ID && selectedCourseId != null) {
            var lectureExpanded by remember { mutableStateOf(false) }
            val lectureLabel = selectedLecture?.let { if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn }
                ?: stringResource(R.string.lecture_selector_title)

            Box {
                Tab(
                    text = lectureLabel,
                    onClick = { if (lectures.isNotEmpty()) lectureExpanded = true },
                    modifier = Modifier.padding(horizontal = 4.dp).width(200.dp)
                )
                if (lectures.isNotEmpty()) {
                    DropdownMenu(
                        expanded = lectureExpanded,
                        onDismissRequest = { lectureExpanded = false },
                        modifier = Modifier.width(300.dp).height(400.dp)
                    ) {
                        LectureSelector(
                            lectures = lectures,
                            language = currentLanguage,
                            selectedLectureId = selectedLectureId,
                            modifier = Modifier.requiredSize(width = 300.dp, height = 400.dp),
                            onLectureSelect = {
                                courseViewerViewModel.selectLecture(it.id)
                                lectureExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Tab(
            icon = if (monitorMode.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            iconContentDescription = if (monitorMode.isRunning) stringResource(R.string.cd_stop) else stringResource(R.string.cd_start),
            onClick = {
                val newState = !monitorMode.isRunning
                monitorViewModel.setIsRunning(newState)
                onStartStopClick(newState)
            },
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

private fun courseDisplayName(course: CourseEntry, language: Language): String =
    if (language == Language.RU) course.nameRu ?: course.titleEn else course.titleEn

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000)
@Composable
fun TeachingControlPanelPreview() {
    val mockRepo = com.example.cardiosimulator.data.CourseRepository(
        source = object : com.example.cardiosimulator.data.CourseSource {
            override fun readManifest(): com.example.cardiosimulator.domain.CourseManifest? = null
            override fun readCourse(courseId: String): com.example.cardiosimulator.domain.Course? = null
            override fun readLecture(courseId: String, lectureId: String, language: String): com.example.cardiosimulator.domain.Lecture? = null
            override fun listCourses(): List<String> = emptyList()
            override fun listLectures(courseId: String): List<String> = emptyList()
        }
    )

    val previewAppViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    com.example.cardiosimulator.domain.AppBuilder().addMode(
                        com.example.cardiosimulator.domain.OperatingModeModel(com.example.cardiosimulator.domain.OperatingMode.Teaching)
                    ).build(),
                ) as T
            }
        },
    )

    // Using a factory or just a fake object if possible, but ViewModel usually needs a factory
    // For preview, we can just pass a remembered mock if the constructor allows it.
    // CourseViewerViewModel is not a @Composable so it can't be 'remembered' easily without a wrapper.
    val previewViewerViewModel = remember {
        CourseViewerViewModel(
            repository = mockRepo,
            mode = com.example.cardiosimulator.domain.OperatingMode.Teaching,
            prefs = null
        )
    }

    CardioSimulatorTheme {
        TeachingControlPanel(
            appViewModel = previewAppViewModel,
            courseViewerViewModel = previewViewerViewModel
        )
    }
}
