package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun SettingsDialog(
    monitorViewModel: MonitorViewModel,
    appViewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        SettingsContent(
            monitorViewModel = monitorViewModel,
            appViewModel = appViewModel,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    monitorViewModel: MonitorViewModel,
    appViewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .width(500.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = stringResource(R.string.settings_color_scheme),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GridScheme.entries.forEach { scheme ->
                    val isSelected = monitorMode.gridScheme == scheme
                    FilterChip(
                        selected = isSelected,
                        onClick = { monitorViewModel.setGridScheme(scheme) },
                        label = { Text(stringResource(scheme.labelRes)) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Language.entries.forEach { language ->
                    val isSelected = selectedLanguage == language
                    FilterChip(
                        selected = isSelected,
                        onClick = { appViewModel.updateLanguage(language) },
                        label = { Text(language.displayName) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ECG data folder — lets the user re-pick the source folder.
            // The picker takes a persistable read permission so the chosen
            // folder keeps working across reboots.
            val context = LocalContext.current
            val pickFolder = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    appViewModel.setDataFolder(context, uri)
                    onDismiss()
                }
            }
            Text(
                text = stringResource(R.string.data_source_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(onClick = { pickFolder.launch(null) }) {
                Text(stringResource(R.string.data_source_change_folder))
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.settings_close))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    CardioSimulatorTheme {
    }
}
