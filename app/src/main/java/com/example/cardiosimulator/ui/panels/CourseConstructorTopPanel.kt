package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewMode
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
    val isDirty by courseConstructorViewModel.isDirty.collectAsState()
    val isSaving by courseConstructorViewModel.isSaving.collectAsState()
    val viewMode by courseConstructorViewModel.viewMode.collectAsState()

    var showNewCourse by remember { mutableStateOf(false) }
    var showNewLecture by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showImportFullPage by remember { mutableStateOf(false) }

    if (showNewCourse) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_new_course),
            label = stringResource(R.string.course_constructor_title_hint),
            initial = "",
            onConfirm = { title ->
                courseConstructorViewModel.createCourse(generateRandomId(), title)
                showNewCourse = false
            },
            onDismiss = { showNewCourse = false },
        )
    }
    if (showNewLecture) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_new_lecture),
            label = stringResource(R.string.course_constructor_title_hint),
            initial = "",
            onConfirm = { title ->
                courseConstructorViewModel.createLecture(generateRandomId(), title)
                showNewLecture = false
            },
            onDismiss = { showNewLecture = false },
        )
    }
    if (showRename) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_rename),
            label = stringResource(R.string.course_constructor_title_hint),
            initial = lectures.find { it.id == selectedLectureId }?.titleEn.orEmpty(),
            onConfirm = { title -> courseConstructorViewModel.renameLecture(title); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.course_constructor_delete)) },
            text = { Text(stringResource(R.string.course_constructor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { courseConstructorViewModel.deleteLecture(); showDelete = false }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            },
        )
    }
    if (showImportFullPage) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportFullPage = false },
            title = { Text("Import Full Page (HTML)") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    placeholder = { Text("Paste <!DOCTYPE html>... or fragment here") },
                    label = { Text("HTML Source") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    courseConstructorViewModel.importFullPage(importText)
                    showImportFullPage = false
                }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportFullPage = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Course Selector
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) {
                val currentCourse = courses.find { it.id == selectedCourseId }
                Text(
                    text = currentCourse?.titleEn ?: stringResource(R.string.course_drawer_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                courses.forEach { course ->
                    DropdownMenuItem(
                        text = { Text(course.titleEn) },
                        onClick = {
                            courseConstructorViewModel.selectCourse(course.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp))

        TextButton(onClick = { showNewCourse = true }) {
            Text(stringResource(R.string.course_constructor_new_course), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { showNewLecture = true }) {
            Text(stringResource(R.string.course_constructor_new_lecture), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { showImportFullPage = true }) {
            Text("All-in-one", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { showRename = true },
            enabled = selectedLectureId != null
        ) {
            Text(stringResource(R.string.course_constructor_rename), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { showDelete = true },
            enabled = selectedLectureId != null
        ) {
            Text(stringResource(R.string.course_constructor_delete), style = MaterialTheme.typography.labelLarge)
        }

        VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp))

        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val modes = listOf(
                ConstructorViewMode.EDITOR to Icons.Default.EditNote,
                ConstructorViewMode.BOTH to Icons.Default.VerticalSplit,
                ConstructorViewMode.PREVIEW to Icons.Default.Visibility
            )
            modes.forEach { (mode, icon) ->
                val selected = viewMode == mode
                IconButton(
                    onClick = { courseConstructorViewModel.setViewMode(mode) },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(icon, contentDescription = mode.name, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = { courseConstructorViewModel.revert() },
            enabled = isDirty && !isSaving,
        ) {
            Text(stringResource(R.string.course_constructor_revert), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { courseConstructorViewModel.save() },
            enabled = isDirty && !isSaving,
        ) {
            Text(stringResource(R.string.constructor_save), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OneFieldDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.constructor_rename_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.constructor_rename_cancel)) }
        },
    )
}

private fun generateRandomId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..16).map { chars.random() }.joinToString("")
}
