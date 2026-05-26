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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.AssetPathologySource
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.screens.MainScreen
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appBuilder = AppBuilder()
        OperatingMode.entries.forEach { mode ->
            appBuilder.addMode(OperatingModeModel(mode))
        }

        val repository = PathologyRepository(AssetPathologySource(this.assets))
        val prefs = DataSourcePrefs(this.applicationContext)

        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AppViewModel(
                            appState = appBuilder.build(initialMode = OperatingMode.Teaching),
                            repository = repository,
                            appContext = applicationContext,
                            prefs = prefs,
                        ) as T
                    }
                },
            )

            val monitorViewModel: MonitorViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MonitorViewModel(prefs) as T
                    }
                }
            )

            val rhythmViewModel: RhythmViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return RhythmViewModel(repository, prefs) as T
                    }
                }
            )

            val editorViewModel: EditorViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return EditorViewModel(repository, prefs) as T
                    }
                }
            )

            val isDarkTheme by appViewModel.isDarkTheme.collectAsState()
            CardioSimulatorTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    editorViewModel = editorViewModel
                )
            }
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
fun MainPreview() {
    val context = LocalContext.current
    val appBuilder = AppBuilder()
    OperatingMode.entries.forEach { mode ->
        appBuilder.addMode(OperatingModeModel(mode))
    }
    
    val repository = PathologyRepository(AssetPathologySource(context.assets))
    val prefs = DataSourcePrefs(context)

    val appViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(appState = appBuilder.build()) as T
            }
        },
    )
    val monitorViewModel: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(prefs) as T
            }
        }
    )
    val rhythmViewModel: RhythmViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RhythmViewModel(repository, prefs) as T
            }
        }
    )

    val editorViewModel: EditorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(repository, prefs) as T
            }
        }
    )

    CardioSimulatorTheme {
        MainScreen(
            appViewModel = appViewModel,
            monitorViewModel = monitorViewModel,
            rhythmViewModel = rhythmViewModel,
            editorViewModel = editorViewModel
        )
    }
}
