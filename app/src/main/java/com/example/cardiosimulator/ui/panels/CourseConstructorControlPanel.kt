package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel

private const val SNIPPET_H2 = "<h2>Heading</h2>"
private const val SNIPPET_FORMULA = "\$\$ \\frac{a}{b} \$\$"
private const val SNIPPET_IMAGE = "<img src=\"assets/image.svg\" alt=\"\">"
private const val SNIPPET_ECG = "<ecg pathology=\"lad\" lead=\"II\"></ecg>"
private val SNIPPET_TABLE = """
<table data-quiz-id="quiz1" data-editable="true">
  <tr><th>Question</th><th>Answer</th></tr>
  <tr><td>Example</td><td><input></td></tr>
</table>
""".trimIndent()

/**
 * Bottom-slot panel for the Course Constructor: course/lecture management
 * (new / rename / delete) plus insert-block helpers that append HTML
 * snippets to the editor draft. Mirrors `ConstructorControlPanel`'s shape.
 */
@Composable
fun CourseConstructorControlPanel(
    courseConstructorViewModel: CourseConstructorViewModel,
    modifier: Modifier = Modifier,
) {
    val selectedLectureId by courseConstructorViewModel.selectedLectureId.collectAsState()
    val lectures by courseConstructorViewModel.lectures.collectAsState()
    val hasLecture = selectedLectureId != null

    var showNewCourse by remember { mutableStateOf(false) }
    var showNewLecture by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (showNewCourse) {
        TwoFieldDialog(
            title = stringResource(R.string.course_constructor_new_course),
            field1Label = stringResource(R.string.course_constructor_id_hint),
            field2Label = stringResource(R.string.course_constructor_title_hint),
            onConfirm = { id, title -> courseConstructorViewModel.createCourse(id, title); showNewCourse = false },
            onDismiss = { showNewCourse = false },
        )
    }
    if (showNewLecture) {
        TwoFieldDialog(
            title = stringResource(R.string.course_constructor_new_lecture),
            field1Label = stringResource(R.string.course_constructor_id_hint),
            field2Label = stringResource(R.string.course_constructor_title_hint),
            onConfirm = { id, title -> courseConstructorViewModel.createLecture(id, title); showNewLecture = false },
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

    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab(text = stringResource(R.string.course_constructor_new_course), onClick = { showNewCourse = true }, modifier = Modifier.weight(1f))
        Tab(text = stringResource(R.string.course_constructor_new_lecture), onClick = { showNewLecture = true }, modifier = Modifier.weight(1f))
        Tab(text = stringResource(R.string.course_constructor_rename), onClick = { if (hasLecture) showRename = true }, modifier = Modifier.weight(1f))
        Tab(text = stringResource(R.string.course_constructor_delete), onClick = { if (hasLecture) showDelete = true }, modifier = Modifier.weight(1f))

        ControlPanelDivider()

        Tab(text = "H2", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_H2) }, modifier = Modifier.weight(0.7f))
        Tab(text = "Σ", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_FORMULA) }, modifier = Modifier.weight(0.7f))
        Tab(text = "IMG", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_IMAGE) }, modifier = Modifier.weight(0.7f))
        Tab(text = "ECG", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_ECG) }, modifier = Modifier.weight(0.7f))
        Tab(text = "TBL", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_TABLE) }, modifier = Modifier.weight(0.7f))
    }
}

@Composable
private fun TwoFieldDialog(
    title: String,
    field1Label: String,
    field2Label: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = f1, onValueChange = { f1 = it }, label = { Text(field1Label) }, singleLine = true)
                TextField(value = f2, onValueChange = { f2 = it }, label = { Text(field2Label) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(f1, f2) }, enabled = f1.isNotBlank()) {
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
