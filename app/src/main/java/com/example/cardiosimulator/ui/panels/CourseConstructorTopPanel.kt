package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel

@Composable
fun CourseConstructorTopPanel(
    appViewModel: AppViewModel,
    courseConstructorViewModel: CourseConstructorViewModel,
    modifier: Modifier = Modifier,
) {
    val allCourses by appViewModel.courses.collectAsState()
    val courses = remember(allCourses) {
        allCourses.filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
    }
    val selectedCourseId by courseConstructorViewModel.selectedCourseId.collectAsState()
    val lectures by courseConstructorViewModel.lectures.collectAsState()
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Course Selector
        var courseExpanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { courseExpanded = true }) {
                val currentCourse = courses.find { it.id == selectedCourseId }
                val title = if (currentCourse != null) {
                    if (selectedLanguage == Language.RU) currentCourse.nameRu ?: currentCourse.titleEn else currentCourse.titleEn
                } else {
                    stringResource(R.string.course_drawer_title)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = courseExpanded, onDismissRequest = { courseExpanded = false }) {
                courses.forEach { course ->
                    val title = if (selectedLanguage == Language.RU) course.nameRu ?: course.titleEn else course.titleEn
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            courseConstructorViewModel.selectCourse(course.id)
                            courseExpanded = false
                        }
                    )
                }
            }
        }

        VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp))

        // Lecture Selector
        var lectureExpanded by remember { mutableStateOf(false) }
        Box {
            TextButton(
                onClick = { lectureExpanded = true },
                enabled = selectedCourseId != null
            ) {
                val currentLecture = lectures.find { it.id == selectedLectureId }
                val title = if (currentLecture != null) {
                    if (selectedLanguage == Language.RU) currentLecture.nameRu ?: currentLecture.titleEn else currentLecture.titleEn
                } else {
                    stringResource(R.string.lecture_selector_title)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = lectureExpanded, onDismissRequest = { lectureExpanded = false }) {
                lectures.forEach { lecture ->
                    val title = if (selectedLanguage == Language.RU) lecture.nameRu ?: lecture.titleEn else lecture.titleEn
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            courseConstructorViewModel.selectLecture(lecture.id)
                            lectureExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
