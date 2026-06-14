package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel

@Composable
fun ConstructorTopPanel(
    appViewModel: AppViewModel,
    constructorViewModel: ConstructorViewModel,
    modifier: Modifier = Modifier,
) {


}

@Preview(showBackground = true, widthDp = 800, heightDp = 60)
@Composable
fun ConstructorTopPanelPreview() {
    val mockRepo = com.example.cardiosimulator.data.PathologyRepository(
        source = object : com.example.cardiosimulator.data.PathologySource {
            override fun readManifest(): com.example.cardiosimulator.domain.PathologyManifest? = null
            override fun readPathology(id: String): com.example.cardiosimulator.domain.PathologyFile? = null
            override fun listPathologies(): List<String> = emptyList()
        }
    )

    val previewAppViewModel: AppViewModel = viewModel(
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

    CardioSimulatorTheme {
        ConstructorTopPanel(
            appViewModel = previewAppViewModel,
            constructorViewModel = previewConstructorViewModel
        )
    }
}
