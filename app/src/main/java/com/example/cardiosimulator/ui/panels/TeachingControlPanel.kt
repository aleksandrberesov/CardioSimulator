package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun TeachingControlPanel(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel = viewModel(),
    onStartStopClick: (Boolean) -> Unit = {},
) {
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val courses by appViewModel.courses.collectAsState()
    val selectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()

    Row(
        //modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var expanded by remember { mutableStateOf(false) }
        val selectedCourse = courses.find { it.id == selectedCourseId }
        val selectedLabel = selectedCourse?.let { courseDisplayName(it, currentLanguage) } ?: ""

        Tab(
            text = selectedLabel,
            onClick = { if (courses.isNotEmpty()) expanded = true },
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (courses.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                courses.forEach { course ->
                    DropdownMenuItem(
                        text = { Text(courseDisplayName(course, currentLanguage)) },
                        onClick = {
                            appViewModel.selectCourse(course.id)
                            expanded = false
                        }
                    )
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
    CardioSimulatorTheme {
        TeachingControlPanel(
            appViewModel = previewAppViewModel
        )
    }
}
