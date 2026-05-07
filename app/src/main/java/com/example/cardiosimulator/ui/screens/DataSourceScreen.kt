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
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.DataState

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
                Text(
                    text = stringResource(
                        R.string.data_source_loaded_format,
                        state.seriesCount,
                        state.partsCount,
                    )
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.confirmData() },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(stringResource(R.string.data_source_continue))
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
}
