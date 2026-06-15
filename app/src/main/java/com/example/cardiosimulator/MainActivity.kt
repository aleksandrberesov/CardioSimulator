package com.example.cardiosimulator

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.AssetPathologySource
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.FileCourseSource
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.FileOskeSource
import com.example.cardiosimulator.data.OskeRepository
import com.example.cardiosimulator.data.OskeResultStore
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.screens.MainScreen
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appBuilder = AppBuilder()
        OperatingMode.entries.forEach { mode ->
            appBuilder.addMode(OperatingModeModel(mode))
        }
        setContent {
            val viewModel: AppViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val oskeDir = File(this@MainActivity.filesDir, AppViewModel.OSKE_DIR)
                        return AppViewModel(
                            appState = appBuilder.build(initialMode = OperatingMode.Teaching),
                            // Boot from assets; swapped to a FilePathologySource once
                            // the user picks a Pathologies.zip via SAF.
                            repository = PathologyRepository(
                                AssetPathologySource(this@MainActivity.assets),
                            ),
                            // Course bundle starts empty — no courses are bundled
                            // in assets. The repository points at the (initially
                            // absent) filesDir/courses dir; it stays empty until the
                            // user picks a Courses.zip via SAF, at which point
                            // AppViewModel swaps in a populated FileCourseSource.
                            // While empty, RhythmSelector simply shows all pathologies.
                            courseRepository = CourseRepository(
                                FileCourseSource(File(this@MainActivity.filesDir, AppViewModel.COURSES_DIR)),
                            ),
                            oskeRepository = OskeRepository(
                                FileOskeSource(oskeDir)
                            ),
                            oskeResultStore = OskeResultStore(
                                File(oskeDir, "results")
                            ),
                            appContext = this@MainActivity.applicationContext,
                            prefs = DataSourcePrefs(this@MainActivity.applicationContext),
                        ) as T
                    }
                },
            )
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            CardioSimulatorTheme(darkTheme = isDarkTheme) {
                MainScreen(appViewModel = viewModel)
            }
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
fun MainPreview() {
    val appBuilder = AppBuilder()
    OperatingMode.entries.forEach { mode ->
        appBuilder.addMode(OperatingModeModel(mode))
    }
    val previewViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(appState = appBuilder.build()) as T
            }
        },
    )
    CardioSimulatorTheme {
        MainScreen(appViewModel = previewViewModel)
    }
}
