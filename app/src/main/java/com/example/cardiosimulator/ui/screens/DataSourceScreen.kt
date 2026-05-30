package com.example.cardiosimulator.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.panels.CourseSelector
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.DataState
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

/**
 * First-run screen shown when no Pathologies.zip has been picked yet, or
 * when the previously picked file is no longer usable. Launches the
 * system OPEN_DOCUMENT chooser, takes a persistable read permission for
 * the resulting URI, and hands it to the view model to extract and load.
 */
@Composable
fun DataSourceScreen(
    appViewModel: AppViewModel,
    rhythmViewModel: RhythmViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RhythmViewModel(
                    repository = appViewModel.repository!!,
                    mode = appViewModel.selectedOperatingMode.value.id,
                    prefs = appViewModel.prefs
                ) as T
            }
        }
    ),
    state: DataState,
    courseState: DataState,
) {
    val context = LocalContext.current
    var showDetails by remember { mutableStateOf(false) }
    var showCourseDetails by remember { mutableStateOf(false) }

    val pickZipFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            appViewModel.setDataFolder(context, uri)
        }
    }

    val pickCourseZipFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            appViewModel.setCourseDataFolder(context, uri)
            showCourseDetails = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(32.dp))

        // --- ECG DATA SECTION ---
        DataSourceSection(
            title = stringResource(R.string.data_source_title),
            description = stringResource(R.string.data_source_description),
            state = state,
            onPickFile = { pickZipFile.launch(ZIP_MIME) },
            readyText = { count -> stringResource(R.string.data_source_loaded_format, count) },
            onShowDetails = { showDetails = true }
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.8f))
        Spacer(Modifier.height(24.dp))

        // --- COURSE DATA SECTION ---
        DataSourceSection(
            title = stringResource(R.string.course_data_source_title),
            description = stringResource(R.string.course_data_source_description),
            state = courseState,
            onPickFile = { pickCourseZipFile.launch(ZIP_MIME) },
            readyText = { count -> stringResource(R.string.course_data_source_loaded_format, count) },
            onShowDetails = { showCourseDetails = true }
        )

        Spacer(Modifier.height(48.dp))

        val canContinue = state is DataState.Ready && courseState is DataState.Ready
        Button(
            onClick = { appViewModel.confirmData() },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth(0.6f),
        ) { Text(stringResource(R.string.data_source_continue)) }
    }

    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val courses by appViewModel.courses.collectAsState()

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.data_source_pathologies_title, rhythms.size)) },
            text = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    modifier = Modifier.fillMaxHeight(0.7f),
                    rhythms = rhythms,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.data_source_close))
                }
            },
        )
    }

    if (showCourseDetails) {
        AlertDialog(
            onDismissRequest = { showCourseDetails = false },
            title = { Text(stringResource(R.string.data_source_courses_title, courses.size)) },
            text = {
                CourseSelector(
                    appViewModel = appViewModel,
                    modifier = Modifier.fillMaxHeight(0.7f),
                    courses = courses,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCourseDetails = false }) {
                    Text(stringResource(R.string.data_source_close))
                }
            },
        )
    }
}

@Composable
private fun DataSourceSection(
    title: String,
    description: String,
    state: DataState,
    onPickFile: () -> Unit,
    readyText: @Composable (Int) -> String,
    onShowDetails: (() -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
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
                    DataState.Error.Reason.Unreadable ->
                        stringResource(R.string.data_source_error_unreadable)
                    DataState.Error.Reason.Empty ->
                        stringResource(R.string.data_source_error_empty)
                    DataState.Error.Reason.BadManifest ->
                        stringResource(R.string.data_source_error_bad_manifest)
                }
                Text(text = msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onPickFile) {
                    Text(stringResource(R.string.data_source_retry))
                }
            }
            is DataState.Ready -> {
                Text(text = readyText(state.pathologyCount))
                Spacer(Modifier.height(16.dp))
                if (onShowDetails != null) {
                    Button(
                        onClick = onShowDetails,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    ) { Text(stringResource(R.string.data_source_show_details)) }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(0.6f),
                ) { Text(stringResource(R.string.data_source_change_folder)) }
            }
            DataState.NotConfigured -> {
                Button(onClick = onPickFile) {
                    Text(stringResource(R.string.data_source_pick_folder))
                }
            }
        }
    }
}

private val ZIP_MIME = arrayOf("application/zip", "application/x-zip-compressed")

@Composable
private fun previewAppViewModel(): AppViewModel {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    AppBuilder().addMode(OperatingModeModel(OperatingMode.Teaching)).build(),
                ) as T
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenNotConfiguredPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(
            appViewModel = previewAppViewModel(),
            state = DataState.NotConfigured,
            courseState = DataState.NotConfigured
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenLoadingPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(
            appViewModel = previewAppViewModel(),
            state = DataState.Loading,
            courseState = DataState.Loading
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenReadyPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(
            appViewModel = previewAppViewModel(),
            state = DataState.Ready(pathologyCount = 56),
            courseState = DataState.Ready(pathologyCount = 12)
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DataSourceScreenErrorPreview() {
    CardioSimulatorTheme {
        DataSourceScreen(
            appViewModel = previewAppViewModel(),
            state = DataState.Error(DataState.Error.Reason.Empty),
            courseState = DataState.Error(DataState.Error.Reason.Empty)
        )
    }
}
