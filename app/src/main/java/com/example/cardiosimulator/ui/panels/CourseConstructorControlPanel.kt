package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.TopicEntry
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewMode
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

@Composable
fun CourseConstructorControlPanel(
    appViewModel: AppViewModel,
    courseConstructorViewModel: CourseConstructorViewModel,
    modifier: Modifier = Modifier,
) {
    val lectures by courseConstructorViewModel.lectures.collectAsState()
    val topics by courseConstructorViewModel.topics.collectAsState()
    val selectedTopicId by courseConstructorViewModel.selectedTopicId.collectAsState()
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val isDirty by courseConstructorViewModel.isDirty.collectAsState()
    val isSaving by courseConstructorViewModel.isSaving.collectAsState()
    val viewMode by courseConstructorViewModel.viewMode.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    var showNewCourse by remember { mutableStateOf(false) }
    var showNewTopic by remember { mutableStateOf(false) }
    var showRenameTopic by remember { mutableStateOf(false) }
    var showDeleteTopic by remember { mutableStateOf(false) }
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
    if (showNewTopic) {
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_new_topic),
            label = stringResource(R.string.course_constructor_topic_title_hint),
            initial = "",
            onConfirm = { title ->
                courseConstructorViewModel.createTopic(generateRandomId(), title)
                showNewTopic = false
            },
            onDismiss = { showNewTopic = false },
        )
    }
    if (showRenameTopic) {
        val topic = topics.find { it.id == selectedTopicId }
        OneFieldDialog(
            title = stringResource(R.string.course_constructor_rename_topic),
            label = stringResource(R.string.course_constructor_topic_title_hint),
            initial = topic?.let { if (selectedLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn }.orEmpty(),
            onConfirm = { title ->
                selectedTopicId?.let { courseConstructorViewModel.renameTopic(it, title) }
                showRenameTopic = false
            },
            onDismiss = { showRenameTopic = false },
        )
    }
    if (showDeleteTopic) {
        val topicName = topics.find { it.id == selectedTopicId }?.let { if (selectedLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn } ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteTopic = false },
            title = { Text(stringResource(R.string.course_constructor_delete_topic_title)) },
            text = { Text(stringResource(R.string.course_constructor_delete_topic_body, topicName)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTopicId?.let { courseConstructorViewModel.deleteTopic(it) }
                    showDeleteTopic = false
                }) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTopic = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            },
        )
    }
    if (showNewLecture) {
        SubtopicDialog(
            title = stringResource(R.string.course_constructor_new_lecture),
            initialTitle = "",
            initialTopicId = selectedTopicId,
            topics = topics,
            language = selectedLanguage,
            onConfirm = { title, topicId ->
                courseConstructorViewModel.createLecture(generateRandomId(), title, topicId)
                showNewLecture = false
            },
            onDismiss = { showNewLecture = false },
        )
    }
    if (showRename) {
        val lecture = lectures.find { it.id == selectedLectureId }
        SubtopicDialog(
            title = stringResource(R.string.course_constructor_rename),
            initialTitle = lecture?.titleEn.orEmpty(), // Using titleEn for rename, maybe should use RU if RU is active?
            initialTopicId = lecture?.topic,
            topics = topics,
            language = selectedLanguage,
            onConfirm = { title, topicId ->
                courseConstructorViewModel.renameLecture(title, topicId)
                showRename = false
            },
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
                    label = { Text("HTML Source") },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None
                    )
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
        modifier = modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = { showNewCourse = true }) {
            Text(stringResource(R.string.course_constructor_new_course), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { showNewTopic = true }) {
            Text(stringResource(R.string.course_constructor_new_topic), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { showNewLecture = true }) {
            Text(stringResource(R.string.course_constructor_new_lecture), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { showImportFullPage = true }) {
            Text("All-in-one", style = MaterialTheme.typography.labelLarge)
        }

        ControlPanelDivider()

        TextButton(
            onClick = { showRenameTopic = true },
            enabled = selectedTopicId != null
        ) {
            Text(stringResource(R.string.course_constructor_rename_topic), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { showDeleteTopic = true },
            enabled = selectedTopicId != null
        ) {
            Text(stringResource(R.string.course_constructor_delete_topic), style = MaterialTheme.typography.labelLarge)
        }

        ControlPanelDivider()

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

        ControlPanelDivider()

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
private fun SubtopicDialog(
    title: String,
    initialTitle: String,
    initialTopicId: String?,
    topics: List<TopicEntry>,
    language: Language,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialTitle) }
    var selectedTopicId by remember { mutableStateOf(initialTopicId) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.course_constructor_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None
                    )
                )

                Text(text = stringResource(R.string.topic_selector_title), style = MaterialTheme.typography.labelSmall)

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val topic = topics.find { it.id == selectedTopicId }
                        val topicName = topic?.let { if (language == Language.RU) it.nameRu ?: it.titleEn else it.titleEn }
                            ?: stringResource(R.string.course_constructor_no_topic)
                        Text(topicName)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.course_constructor_no_topic)) },
                            onClick = { selectedTopicId = null; expanded = false }
                        )
                        topics.forEach { topic ->
                            val name = if (language == Language.RU) topic.nameRu ?: topic.titleEn else topic.titleEn
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { selectedTopicId = topic.id; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, selectedTopicId) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.constructor_rename_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.constructor_rename_cancel)) }
        },
    )
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
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None
                )
            )
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
