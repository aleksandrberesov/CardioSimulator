package com.example.cardiosimulator

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.screens.MainScreen
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appModes = resources.getStringArray(R.array.app_modes)
        val appBuilder = AppBuilder()
        appModes.forEach { title ->
            appBuilder.addMode(OperatingModeModel(title, ""))
        }
        setContent {
            CardioSimulatorTheme {
                MainScreen(
                    viewModel = MainViewModel(
                        appState = appBuilder.build(initialModeTitle = "Teaching"),
                        repository = Points.fromResources(this)
                    )
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
    val appModes = context.resources.getStringArray(R.array.app_modes)
    val appBuilder = AppBuilder()
    appModes.forEach { title ->
        appBuilder.addMode(OperatingModeModel(title, ""))
    }
    CardioSimulatorTheme {
        MainScreen(
            viewModel = MainViewModel(
                appState = appBuilder.build(),
                repository = Points.fromResources(context)
            )
        )
    }
}
