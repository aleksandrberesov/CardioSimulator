package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.components.MarkdownRenderer
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.panels.CourseSelector
import com.example.cardiosimulator.ui.panels.LectureSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseViewerViewModel

@Composable
fun TeachingScreen(
    appViewModel: AppViewModel,
) {
    val courseRepository = appViewModel.courseRepository ?: return
    val pathologyRepository = appViewModel.repository ?: return
    val prefs = appViewModel.prefs ?: return
    
    val viewerViewModel: CourseViewerViewModel = viewModel(
        key = "teaching",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return CourseViewerViewModel(courseRepository, prefs, "teaching") as T
            }
        }
    )

    val courses by appViewModel.courses.collectAsState()
    val selectedCourseId by viewerViewModel.selectedCourseId.collectAsState()
    val selectedCourse by viewerViewModel.selectedCourse.collectAsState()
    val selectedLectureId by viewerViewModel.selectedLectureId.collectAsState()
    val lectureContent by viewerViewModel.lectureContent.collectAsState()
    val lectureEntries by viewerViewModel.lectureEntries.collectAsState()
    
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()

    var isDrawerExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            val displayTitle = lectureContent?.let {
                if (currentLanguage == Language.RU) it.frontMatter.title.takeIf { t -> t.isNotEmpty() } ?: it.id else it.frontMatter.title.takeIf { t -> t.isNotEmpty() } ?: it.id
            } ?: selectedCourse?.let {
                if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
            } ?: stringResource(R.string.teaching_no_course_selected)

            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Main Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (lectureContent != null) {
                    MarkdownRenderer(
                        lecture = lectureContent!!,
                        pathologyRepository = pathologyRepository,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedCourse == null) 
                                stringResource(R.string.teaching_pick_course_hint)
                            else 
                                stringResource(R.string.teaching_pick_lecture_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SideDrawer(
            isExpanded = isDrawerExpanded,
            onExpandedChange = { isDrawerExpanded = it },
            drawerWidth = 320.dp,
            drawerContent = {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selectedCourse == null) {
                        Text(
                            text = stringResource(R.string.course_drawer_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        CourseSelector(
                            appViewModel = appViewModel,
                            courses = courses,
                            selectedCourseId = selectedCourseId,
                            onCourseSelect = { viewerViewModel.selectCourse(it.id) }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewerViewModel.selectCourse(null) }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "← ",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (currentLanguage == Language.RU) selectedCourse?.nameRu ?: selectedCourse?.titleEn ?: "" else selectedCourse?.titleEn ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        LectureSelector(
                            appViewModel = appViewModel,
                            lectures = lectureEntries,
                            selectedLectureId = selectedLectureId,
                            onLectureSelect = { 
                                viewerViewModel.selectLecture(it.id)
                                isDrawerExpanded = false
                            }
                        )
                    }
                }
            },
            handlerContent = {
                Text(
                    text = stringResource(if (selectedCourse == null) R.string.course_drawer_title else R.string.lecture_drawer_title),
                    modifier = Modifier
                        .requiredWidth(128.dp)
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
