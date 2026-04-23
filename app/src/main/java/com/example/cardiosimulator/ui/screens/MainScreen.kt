package com.example.cardiosimulator.ui.screens
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.viewmodels.MainViewModel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun MainScreen(viewModel: MainViewModel){
    val selectedMode by viewModel.selectedOperatingMode.collectAsState()
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Box(
            modifier = Modifier.weight(2f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            com.example.cardiosimulator.ui.panels.AppControlPanel(viewModel = viewModel)
        }
        Box(
            modifier = Modifier.weight(15f).fillMaxWidth()
        ) {
            when (selectedMode.title) {
                "Teaching" -> TeachingScreen(viewModel = viewModel)
                "Testing" -> TestingScreen(viewModel = viewModel)
                "Examination" -> ExaminationScreen(viewModel = viewModel)
                "OSKE" -> OSKEScreen(viewModel = viewModel)
            }
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    val appModes = context.resources.getStringArray(R.array.app_modes)
    val appBuilder = AppBuilder()
    appModes.forEach { title ->
        appBuilder.addMode(OperatingModeModel(title, ""))
    }

    val samplePoints = Points(List(100) { (it % 10).toFloat() / 10f })

    val previewViewModel: MainViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    appState = appBuilder.build(),
                    repository = samplePoints
                ) as T
            }
        }
    )

    CardioSimulatorTheme {
        MainScreen(viewModel = previewViewModel)
    }
}
