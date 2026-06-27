package com.example.cardiosimulator.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.DataState
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun DataSourceScreen(
    appViewModel: AppViewModel,
    rhythmViewModel: RhythmViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = appViewModel.repository ?: com.example.cardiosimulator.data.PathologyRepository(
                    source = object : com.example.cardiosimulator.data.PathologySource {
                        override fun readManifest(): com.example.cardiosimulator.domain.PathologyManifest? = null
                        override fun readPathology(id: String): com.example.cardiosimulator.domain.PathologyFile? = null
                        override fun listPathologies(): List<String> = emptyList()
                    }
                )
                return RhythmViewModel(
                    repository = repo,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(32.dp))

            DataSourceSection(
                title = stringResource(R.string.data_source_title),
                description = stringResource(R.string.data_source_description),
                state = state,
                onPickFile = { pickZipFile.launch(ZIP_MIME) },
                readyText = { count -> stringResource(R.string.data_source_loaded_format, count) },
                onShowDetails = { showDetails = true }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.8f), color = Hairline)
            Spacer(Modifier.height(24.dp))

            DataSourceSection(
                title = stringResource(R.string.course_data_source_title),
                description = stringResource(R.string.course_data_source_description),
                state = courseState,
                onPickFile = { pickCourseZipFile.launch(ZIP_MIME) },
                readyText = { count -> stringResource(R.string.course_data_source_loaded_format, count) },
                onShowDetails = { showCourseDetails = true }
            )

            Spacer(Modifier.height(80.dp))
        }

        Button(
            onClick = { appViewModel.confirmData() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.data_source_continue))
        }
    }

    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val allCourses by appViewModel.courses.collectAsState()
    val courses = remember(allCourses) {
        allCourses.filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
    }

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
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f),
            color = TextSecondary
        )
        Spacer(Modifier.height(24.dp))

        when (state) {
            is DataState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.data_source_loading), color = TextPrimary)
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
                Text(text = readyText(state.pathologyCount), color = TextPrimary)
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
    val mockRepo = com.example.cardiosimulator.data.PathologyRepository(
        source = object : com.example.cardiosimulator.data.PathologySource {
            override fun readManifest(): com.example.cardiosimulator.domain.PathologyManifest? = null
            override fun readPathology(id: String): com.example.cardiosimulator.domain.PathologyFile? = null
            override fun listPathologies(): List<String> = emptyList()
        }
    )
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    AppBuilder().addMode(OperatingModeModel(OperatingMode.Teaching)).build(),
                    repository = mockRepo
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
