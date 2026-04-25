package com.example.cardiosimulator

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.EcgRepository
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.screens.MainScreen
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

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
                        return AppViewModel(
                            appState = appBuilder.build(initialMode = OperatingMode.Teaching),
                            ecgRepository = EcgRepository(this@MainActivity.assets)
                        ) as T
                    }
                }
            )
            CardioSimulatorTheme {
                MainScreen(
                    viewModel = viewModel
                )
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
                return AppViewModel(
                    appState = appBuilder.build(),
                ) as T
            }
        }
    )
    CardioSimulatorTheme {
        MainScreen(
            viewModel = previewViewModel
        )
    }
}
