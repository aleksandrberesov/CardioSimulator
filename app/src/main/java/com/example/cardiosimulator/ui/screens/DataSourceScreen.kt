package com.example.cardiosimulator.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.DataState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * First-run screen shown when no data folder has been picked yet, or when
 * the previously picked folder is no longer usable. Launches the system
 * `OPEN_DOCUMENT_TREE` chooser, takes a persistable read permission for
 * the resulting URI, and hands it to the view model to load.
 */
@Composable
fun DataSourceScreen(
    viewModel: AppViewModel,
    state: DataState,
) {
    val context = LocalContext.current
    var showDetails by remember { mutableStateOf(false) }

    val pickZipFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Persist read permission so the URI keeps working after reboot.
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.setDataFolder(context, uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.data_source_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.data_source_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Spacer(Modifier.height(24.dp))

        when (state) {
            is DataState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.data_source_loading))
            }
            is DataState.Error -> {
                val msg = when (state.reason) {
                    DataState.Error.Reason.MissingSubdirs ->
                        stringResource(R.string.data_source_error_no_subdirs)
                    DataState.Error.Reason.Unreadable ->
                        stringResource(R.string.data_source_error_unreadable)
                    DataState.Error.Reason.Empty ->
                        stringResource(R.string.data_source_error_empty)
                }
                Text(text = msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { pickZipFile.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                    Text(stringResource(R.string.data_source_retry))
                }
            }
            is DataState.Ready -> {
                val isUploading by viewModel.isUploading.collectAsState()
                val lastAck by viewModel.lastAck.collectAsState()

                Text(
                    text = stringResource(
                        R.string.data_source_loaded_format,
                        state.seriesCount,
                        state.partsCount,
                    )
                )
                if (isUploading) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.data_source_uploading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else if (lastAck != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.data_source_upload_success),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.confirmData() },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(stringResource(R.string.data_source_continue))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showDetails = true },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(stringResource(R.string.data_source_show_details))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { pickZipFile.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(stringResource(R.string.data_source_change_folder))
                }
            }
            DataState.NotConfigured -> {
                Button(onClick = { pickZipFile.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                    Text(stringResource(R.string.data_source_pick_folder))
                }
            }
        }
    }

    val rhythms by viewModel.rhythms.collectAsState()
    val language by viewModel.selectedLanguage.collectAsState()

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.data_source_pathologies_title, rhythms.size)) },
            text = {
                RhythmChoosingPanel(
                    modifier = Modifier.fillMaxHeight(0.7f),
                    rhythms = rhythms,
                    currentLanguage = language
                )
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.data_source_close))
                }
            }
        )
    }
}

@Composable
private fun previewAppViewModel(): AppViewModel {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    AppBuilder()
                        .addMode(OperatingModeModel(OperatingMode.Teaching))
                        .build()
                ) as T
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenNotConfiguredPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(viewModel = previewAppViewModel(), state = DataState.NotConfigured)
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenLoadingPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(viewModel = previewAppViewModel(), state = DataState.Loading)
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenReadyPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(
            viewModel = previewAppViewModel(),
            state = DataState.Ready(seriesCount = 124, partsCount = 450)
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenErrorPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(
            viewModel = previewAppViewModel(),
            state = DataState.Error(DataState.Error.Reason.Empty)
        )
    }
}
