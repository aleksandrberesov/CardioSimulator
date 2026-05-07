package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val tcpIp by appViewModel.tcpIp.collectAsState()
    val tcpPort by appViewModel.tcpPort.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        SettingsContent(
            gridScheme = monitorMode.gridScheme,
            selectedLanguage = selectedLanguage,
            tcpIp = tcpIp,
            tcpPort = tcpPort,
            onGridSchemeChange = { monitorViewModel.setGridScheme(it) },
            onLanguageChange = { appViewModel.updateLanguage(it) },
            onTcpChange = { ip, port -> appViewModel.updateTcpConnection(ip, port) },
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    gridScheme: GridScheme,
    selectedLanguage: Language,
    tcpIp: String,
    tcpPort: Int,
    onGridSchemeChange: (GridScheme) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onTcpChange: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember(tcpIp) { mutableStateOf(tcpIp) }
    var portText by remember(tcpPort) { mutableStateOf(tcpPort.toString()) }

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
                    val isSelected = gridScheme == scheme
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGridSchemeChange(scheme) },
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
                        onClick = { onLanguageChange(language) },
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

            Text(
                text = stringResource(R.string.settings_tcp_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = ipText,
                    onValueChange = {
                        ipText = it
                        onTcpChange(it, portText.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.settings_tcp_ip)) },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        onTcpChange(ipText, it.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.settings_tcp_port)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.settings_close))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 600)
@Composable
fun SettingsContentPreview() {
    CardioSimulatorTheme {
        SettingsContent(
            gridScheme = GridScheme.Pink,
            selectedLanguage = Language.EN,
            tcpIp = "192.168.1.100",
            tcpPort = 8080,
            onGridSchemeChange = {},
            onLanguageChange = {},
            onTcpChange = { _, _ -> },
            onDismiss = {}
        )
    }
}
