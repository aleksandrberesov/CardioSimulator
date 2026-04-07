package com.example.cardiosimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.screens.MainScreen
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardioSimulatorTheme {
                MainScreen(
                    viewModel = MainViewModel(
                        appState = AppStateModel(initialOperatingMode = "Test"),
                        repository = Points.fromResources(this)
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    val context = LocalContext.current
    CardioSimulatorTheme {
        MainScreen(
            viewModel = MainViewModel(
                appState = AppStateModel(initialOperatingMode = "Test"),
                repository = Points.fromResources(context)
            )
        )
    }
}