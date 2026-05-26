package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.MockCourses
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseConstructorScreen(
    appViewModel: AppViewModel,
) {
    var courses by remember { mutableStateOf(MockCourses.courses) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var editingLecture by remember { mutableStateOf<Pair<Course, Lecture>?>(null) }

    Row(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Left Column: Course List
        Surface(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Courses", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        val newCourse = Course(id = "new_${courses.size}", title = "New Course")
                        courses = courses + newCourse
                        selectedCourse = newCourse
                        editingCourse = newCourse
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Course")
                    }
                }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(courses) { course ->
                        ListItem(
                            headlineContent = { Text(course.title) },
                            supportingContent = { Text("${course.lectures.size} lectures") },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editingCourse = course }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { 
                                        courses = courses.filter { it.id != course.id }
                                        if (selectedCourse?.id == course.id) selectedCourse = null
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { selectedCourse = course },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selectedCourse?.id == course.id) 
                                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // Right Column: Lecture List & Content
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            if (selectedCourse != null) {
                val course = selectedCourse!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(course.title, style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = {
                        val newLecture = Lecture(id = "new_lec_${course.lectures.size}", title = "New Lecture")
                        val updatedCourse = course.copy(lectures = course.lectures + newLecture)
                        courses = courses.map { if (it.id == course.id) updatedCourse else it }
                        selectedCourse = updatedCourse
                        editingLecture = updatedCourse to newLecture
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Lecture")
                    }
                }

                Text(course.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(course.lectures) { lecture ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { editingLecture = course to lecture }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lecture.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${lecture.attachedPathologyIds.size} pathologies attached", style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                    IconButton(onClick = { editingLecture = course to lecture }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = {
                                        val updatedCourse = course.copy(lectures = course.lectures.filter { it.id != lecture.id })
                                        courses = courses.map { if (it.id == course.id) updatedCourse else it }
                                        selectedCourse = updatedCourse
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a course to edit", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    // Dialogs for editing
    if (editingCourse != null) {
        var title by remember { mutableStateOf(editingCourse!!.title) }
        var description by remember { mutableStateOf(editingCourse!!.description) }
        
        AlertDialog(
            onDismissRequest = { editingCourse = null },
            title = { Text("Edit Course") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = editingCourse!!.copy(title = title, description = description)
                    courses = courses.map { if (it.id == updated.id) updated else it }
                    if (selectedCourse?.id == updated.id) selectedCourse = updated
                    editingCourse = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingCourse = null }) { Text("Cancel") }
            }
        )
    }

    if (editingLecture != null) {
        val (course, lecture) = editingLecture!!
        var title by remember { mutableStateOf(lecture.title) }
        var textContent by remember { mutableStateOf(lecture.textContent) }
        
        AlertDialog(
            onDismissRequest = { editingLecture = null },
            title = { Text("Edit Lecture") },
            modifier = Modifier.fillMaxWidth(0.8f),
            text = {
                Column(
                    modifier = Modifier.height(400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = textContent, 
                        onValueChange = { textContent = it }, 
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updatedLecture = lecture.copy(title = title, textContent = textContent)
                    val updatedCourse = course.copy(lectures = course.lectures.map { if (it.id == updatedLecture.id) updatedLecture else it })
                    courses = courses.map { if (it.id == updatedCourse.id) updatedCourse else it }
                    selectedCourse = updatedCourse
                    editingLecture = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingLecture = null }) { Text("Cancel") }
            }
        )
    }
}
