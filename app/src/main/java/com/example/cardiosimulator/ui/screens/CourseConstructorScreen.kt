package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.components.MarkdownRenderer
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.panels.CourseSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color

@Composable
fun CourseConstructorScreen(
    appViewModel: AppViewModel,
    courseConstructorViewModel: CourseConstructorViewModel,
) {
    val pathologyRepository = appViewModel.repository ?: return
    
    val courses by appViewModel.courses.collectAsState()
    val selectedCourseId by courseConstructorViewModel.selectedCourseId.collectAsState()
    val selectedCourse by courseConstructorViewModel.selectedCourse.collectAsState()
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val targetLecture by courseConstructorViewModel.targetLecture
    val lectureEntries by courseConstructorViewModel.lectureEntries.collectAsState()
    
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    var isDrawerExpanded by remember { mutableStateOf(false) }
    var showCreateLectureDialog by remember { mutableStateOf(false) }

    if (showCreateLectureDialog) {
        var newId by remember { mutableStateOf("") }
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateLectureDialog = false },
            title = { Text("Create New Lecture") },
            text = {
                Column {
                    TextField(value = newId, onValueChange = { newId = it }, label = { Text("ID (e.g. lecture_01)") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Title") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newId.isNotEmpty() && newTitle.isNotEmpty()) {
                        courseConstructorViewModel.createLecture(newId, newTitle)
                        showCreateLectureDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateLectureDialog = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main editor area
            if (targetLecture != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: Editor
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TextField(
                            value = targetLecture!!.rawMarkdown,
                            onValueChange = { courseConstructorViewModel.setMarkdown(it) },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("Start typing Markdown...") }
                        )
                    }
                    
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    
                    // Right: Preview
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        MarkdownRenderer(
                            lecture = targetLecture!!,
                            pathologyRepository = pathologyRepository
                        )
                    }
                }
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

        // Navigation drawer (same as TeachingScreen but wired to CourseConstructorViewModel)
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
                            onCourseSelect = { courseConstructorViewModel.selectCourse(it.id) }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { courseConstructorViewModel.selectCourse(null) }
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
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            lectureEntries.forEach { lecture ->
                                val title = if (currentLanguage == Language.RU)
                                    lecture.nameRu ?: lecture.titleEn
                                else
                                    lecture.titleEn
                                
                                val isSelected = lecture.id == selectedLectureId
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { courseConstructorViewModel.selectLecture(lecture.id) }
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .padding(vertical = 4.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        modifier = Modifier.weight(1f),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    IconButton(onClick = { courseConstructorViewModel.deleteLecture(lecture.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        OutlinedButton(
                            onClick = { showCreateLectureDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Lecture")
                        }
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
