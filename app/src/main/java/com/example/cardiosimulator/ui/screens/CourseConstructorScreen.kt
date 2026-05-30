package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.panels.CourseSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun CourseConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    courseConstructorViewModel: CourseConstructorViewModel,
) {
    val courses by appViewModel.courses.collectAsState()
    val selectedCourseId by courseConstructorViewModel.selectedCourseId.collectAsState()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Course Constructor Screen (Stub)\nSelected Course: ${selectedCourseId ?: "None"}")
        }

        SideDrawer(
            isExpanded = isDrawerExpanded,
            onExpandedChange = { isDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                CourseSelector(
                    appViewModel = appViewModel,
                    courses = courses,
                    selectedCourseId = selectedCourseId,
                    onCourseSelect = {
                        courseConstructorViewModel.selectCourse(it)
                    }
                )
            },
            handlerContent = {
                Text(
                    text = stringResource(R.string.course_drawer_title),
                    modifier = Modifier.rotate(-90f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}
