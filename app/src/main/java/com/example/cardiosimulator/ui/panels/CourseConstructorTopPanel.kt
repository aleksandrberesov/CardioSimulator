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
import com.example.cardiosimulator.ui.components.Tab
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Course Selector
        var courseExpanded by remember { mutableStateOf(false) }
        Box {
            Tab(
                text = remember(selectedCourseId, courses, selectedLanguage) {
                    val currentCourse = courses.find { it.id == selectedCourseId }
                    if (currentCourse != null) {
                        if (selectedLanguage == Language.RU) currentCourse.nameRu ?: currentCourse.titleEn else currentCourse.titleEn
                    } else {
                        "Select Course..."
                    }
                },
                onClick = { courseExpanded = true },
                modifier = Modifier.padding(horizontal = 4.dp).width(200.dp)
            )
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

        // Lecture Selector
        if (selectedCourseId != null) {
            var lectureExpanded by remember { mutableStateOf(false) }
            Box {
                Tab(
                    text = remember(selectedLectureId, lectures, selectedLanguage) {
                        val currentLecture = lectures.find { it.id == selectedLectureId }
                        if (currentLecture != null) {
                            if (selectedLanguage == Language.RU) currentLecture.nameRu ?: currentLecture.titleEn else currentLecture.titleEn
                        } else {
                            "Select Lecture..."
                        }
                    },
                    onClick = { lectureExpanded = true },
                    modifier = Modifier.padding(horizontal = 4.dp).width(200.dp)
                )
                DropdownMenu(
                    expanded = lectureExpanded,
                    onDismissRequest = { lectureExpanded = false },
                    modifier = Modifier.width(300.dp).height(400.dp)
                ) {
                    LectureSelector(
                        lectures = lectures,
                        language = selectedLanguage,
                        selectedLectureId = selectedLectureId,
                        modifier = Modifier.requiredSize(width = 300.dp, height = 400.dp),
                        onLectureSelect = {
                            courseConstructorViewModel.selectLecture(it.id)
                            lectureExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
