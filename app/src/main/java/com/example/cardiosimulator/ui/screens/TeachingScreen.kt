package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.MockCourses
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.Lead as DomainLead
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.MonitorControlPanel
import com.example.cardiosimulator.ui.panels.RhythmChoosingDrawer
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val significantPoints by rhythmViewModel.significantPoints.collectAsState()

    var selectedCourse by remember { mutableStateOf<Course?>(MockCourses.courses.first()) }
    var selectedLecture by remember { mutableStateOf<Lecture?>(null) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxSize()) {
            
            // Left Panel: Course/Lecture Selection
            Surface(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Courses",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCourse?.title ?: "Select Course",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            MockCourses.courses.forEach { course ->
                                DropdownMenuItem(
                                    text = { Text(course.title) },
                                    onClick = {
                                        selectedCourse = course
                                        selectedLecture = course.lectures.firstOrNull()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedCourse?.id != "general") {
                        Text(
                            text = "Lectures",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedCourse?.lectures?.forEach { lecture ->
                                Card(
                                    onClick = {
                                        selectedLecture = lecture
                                        // Select the first attached pathology
                                        lecture.attachedPathologyIds.firstOrNull()?.let { id ->
                                            rhythmViewModel.selectRhythm(id)
                                        }
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedLecture == lecture) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Text(
                                        text = lecture.title,
                                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        // General course just shows the regular rhythms list
                        Box(modifier = Modifier.weight(1f)) {
                            // We can use the existing rhythm panel here, or rely on the drawer
                            Text("General course selected. Use the drawer or rhythm panel to select any pathology.")
                        }
                    }
                }
            }

            // Main Content Area
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                
                // Lecture Text Area (if applicable)
                if (selectedCourse?.id != "general" && selectedLecture != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(150.dp).padding(8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                            Text(text = selectedLecture!!.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = selectedLecture!!.textContent, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Monitor
                Box(modifier = Modifier.weight(1f)) {
                    val mode by monitorViewModel.monitorMode.collectAsState()
                    Monitor(
                        modifier = Modifier.fillMaxSize().padding(start = 24.dp),
                        monitorViewModel = monitorViewModel,
                    ) { rows, columns, scrollOffsetPx ->
                        LeadsGrid(
                            rows = rows,
                            columns = columns,
                            itemCount = mode.count,
                            scrollOffsetPx = scrollOffsetPx
                        ) { _, lead, offset ->
                            val leadPoints = lead?.let { waveforms[it] }
                                ?.takeIf { it.values.size >= 2 }
                                ?: Points(emptyList<Float>())
                            LeadView(
                                points = leadPoints,
                                title = lead?.name ?: "",
                                isRunning = mode.isRunning,
                                scrollOffsetPx = offset,
                                significantPoints = significantPoints
                            )
                        }
                    }
                }
                
                // Monitor Control Panel
                MonitorControlPanel(
                    viewModel = monitorViewModel,
                    onCompareClick = {
                        appViewModel.operatingModes.find { it.id == com.example.cardiosimulator.domain.OperatingMode.Comparison }?.let {
                            appViewModel.updateOperatingMode(it)
                        }
                    },
                    onStartStopClick = { isRunning ->
                        if (isRunning) {
                            appViewModel.sendStartCommand(selectedRhythm?.id, selectedRhythm?.titleEn)
                        } else {
                            appViewModel.sendStopCommand()
                        }
                    }
                )
            }
        }

        if (selectedCourse?.id == "general") {
            RhythmChoosingDrawer(
                appViewModel = appViewModel,
                rhythms = rhythms,
                selectedId = selectedRhythm?.id,
                onRhythmSelect = { rhythmViewModel.selectRhythm(it.id) },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
}
