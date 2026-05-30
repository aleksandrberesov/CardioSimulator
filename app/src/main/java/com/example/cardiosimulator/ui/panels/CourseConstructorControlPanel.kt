package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel

@Composable
fun CourseConstructorControlPanel(
    courseConstructorViewModel: CourseConstructorViewModel,
    modifier: Modifier = Modifier,
) {
    val targetLecture by courseConstructorViewModel.targetLecture
    val dirtyLectures by courseConstructorViewModel.dirtyLectures.collectAsState()
    val isSaving by courseConstructorViewModel.isSaving.collectAsState()

    val currentKey = targetLecture?.let { "${it.courseId}/${it.id}.${it.language}" }
    val isDirty = currentKey != null && dirtyLectures.contains(currentKey)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lecture Info
        Box(
            modifier = Modifier.weight(2f).fillMaxHeight().padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            targetLecture?.let { lecture ->
                Column {
                    Text(
                        text = lecture.id,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lecture.frontMatter.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }

        ControlPanelDivider()

        // Editor Actions
        Row(
            modifier = Modifier.weight(3f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                text = stringResource(R.string.constructor_save),
                icon = Icons.Default.Save,
                onClick = { courseConstructorViewModel.save() },
                enabled = isDirty && !isSaving,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = stringResource(R.string.constructor_undo),
                icon = Icons.Default.Undo,
                onClick = { courseConstructorViewModel.revertLecture() },
                enabled = isDirty && !isSaving,
                modifier = Modifier.weight(1f)
            )
        }

        ControlPanelDivider()

        // Insert Block Helpers (Phase 3b)
        Row(
            modifier = Modifier.weight(3f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                icon = Icons.Default.Functions,
                onClick = { /* TODO: Insert Formula */ },
                enabled = targetLecture != null,
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = Icons.Default.Image,
                onClick = { /* TODO: Insert Image */ },
                enabled = targetLecture != null,
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = Icons.Default.MonitorHeart,
                onClick = { courseConstructorViewModel.insertBlock("ecg") },
                enabled = targetLecture != null,
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = Icons.Default.TableChart,
                onClick = { courseConstructorViewModel.insertBlock("table") },
                enabled = targetLecture != null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
