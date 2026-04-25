package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
            .width(400.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Color Scheme Setting
            Text(
                text = "Monitor Color Scheme",
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
                        label = { Text(scheme.name) },
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

            // Language Setting
            Text(
                text = "Language",
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
                        label = { Text(language.name) },
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

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("CLOSE")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    CardioSimulatorTheme {
        // Mock view models for preview if possible, or just pass nulls if they are not used in a way that crashes
        // Since they are used in collectAsState, we might need a better way or just accept it might not render perfectly in simple preview
        // For now, I'll just use the viewModel() but it needs to be the right type.
    }
}
