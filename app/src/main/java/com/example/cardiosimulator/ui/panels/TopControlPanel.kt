package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseViewerViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun TopControlPanel(
    viewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel? = null,
    constructorViewModel: ConstructorViewModel? = null,
    courseConstructorViewModel: CourseConstructorViewModel? = null,
    courseViewerViewModel: CourseViewerViewModel? = null,
    modifier: Modifier = Modifier,
    onStartStopClick: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val operatingModes = viewModel.operatingModes
    val selectedOperatingMode by viewModel.selectedOperatingMode.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(300.dp).fillMaxHeight()
                ) {
                    Tab(
                        text = stringResource(selectedOperatingMode.id.titleRes),
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        operatingModes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(item.id.titleRes)) },
                                onClick = {
                                    viewModel.updateOperatingMode(item)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().fillMaxHeight()
                ){
                    when (selectedOperatingMode.id) {
                        OperatingMode.Teaching -> {
                            if (courseViewerViewModel != null && rhythmViewModel != null) {
                                TeachingControlPanel(
                                    appViewModel = viewModel,
                                    courseViewerViewModel = courseViewerViewModel,
                                    rhythmViewModel = rhythmViewModel,
                                    monitorViewModel = monitorViewModel
                                )
                            }
                        }
                        OperatingMode.Testing -> {}
                        OperatingMode.Examination -> {}
                        OperatingMode.TestConstructor -> {}
                        OperatingMode.OSKE -> {}
                        OperatingMode.OSKEConstructor -> {}
                        OperatingMode.Constructor -> {
                            if (constructorViewModel != null) {
                                ConstructorTopPanel(
                                    appViewModel = viewModel,
                                    constructorViewModel = constructorViewModel
                                )
                            }
                        }
                        OperatingMode.CourseConstructor -> {
                            if (courseConstructorViewModel != null) {
                                CourseConstructorTopPanel(
                                    appViewModel = viewModel,
                                    courseConstructorViewModel = courseConstructorViewModel
                                )
                            }
                        }
                    }
                }
            }
            Image(
                painter = painterResource(id = R.drawable.main_logo),
                contentDescription = "Company Logo",
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 100)
@Composable
fun TopControlPanelPreview() {
    val mockRepo = com.example.cardiosimulator.data.PathologyRepository(
        source = object : com.example.cardiosimulator.data.PathologySource {
            override fun readManifest(): com.example.cardiosimulator.domain.PathologyManifest? = null
            override fun readPathology(id: String): com.example.cardiosimulator.domain.PathologyFile? = null
            override fun listPathologies(): List<String> = emptyList()
        }
    )

    val previewViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    appState = AppBuilder()
                        .addMode(OperatingModeModel(OperatingMode.Constructor))
                        .build(),
                    repository = mockRepo
                ) as T
            }
        }
    )

    val previewConstructorViewModel: ConstructorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConstructorViewModel(
                    repository = mockRepo,
                    mode = OperatingMode.Constructor
                ) as T
            }
        }
    )

    val previewMonitorViewModel: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(
                    mode = OperatingMode.Constructor
                ) as T
            }
        }
    )

    CardioSimulatorTheme {
        TopControlPanel(
            viewModel = previewViewModel,
            monitorViewModel = previewMonitorViewModel,
            constructorViewModel = previewConstructorViewModel
        )
    }
}
