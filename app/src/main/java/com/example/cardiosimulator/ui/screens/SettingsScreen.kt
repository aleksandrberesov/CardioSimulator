package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.network.TcpConnectionState
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
    val isDarkTheme by appViewModel.isDarkTheme.collectAsState()
    val tcpIp by appViewModel.tcpIp.collectAsState()
    val tcpPort by appViewModel.tcpPort.collectAsState()
    val connectionState by appViewModel.tcpConnectionState.collectAsState()
    val isConnected = connectionState == TcpConnectionState.Connected

    if (connectionState is TcpConnectionState.Error) {
        AlertDialog(
            onDismissRequest = { appViewModel.dismissTcpError() },
            title = { Text(stringResource(R.string.tcp_status_error)) },
            text = { Text((connectionState as TcpConnectionState.Error).message) },
            confirmButton = {
                TextButton(onClick = { appViewModel.dismissTcpError() }) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }

    var ipInput by remember(tcpIp) { mutableStateOf(tcpIp) }
    val ipRegex = remember { Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$") }
    val isIpError = ipInput.isNotEmpty() && !ipInput.matches(ipRegex)

    var portInput by remember(tcpPort) { 
        mutableStateOf(if (tcpPort == 0) "" else tcpPort.toString()) 
    }
    val isPortError = portInput.isNotEmpty() && (portInput.toIntOrNull() ?: 70000) > 65535

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .width(500.dp)
            .heightIn(max = 600.dp)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_close)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.settings_color_scheme),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // System theme / Dark / Light
                    FilterChip(
                        selected = !isDarkTheme,
                        onClick = { appViewModel.updateDarkTheme(false) },
                        label = { Text(stringResource(R.string.theme_light)) },
                        leadingIcon = if (!isDarkTheme) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                    FilterChip(
                        selected = isDarkTheme,
                        onClick = { appViewModel.updateDarkTheme(true) },
                        label = { Text(stringResource(R.string.theme_dark)) },
                        leadingIcon = if (isDarkTheme) {
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

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_grid_scheme),
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

                Text(
                    text = stringResource(R.string.settings_tcp_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() || it == '.' }) {
                                ipInput = newValue
                                if (newValue.matches(ipRegex)) {
                                    appViewModel.updateTcpConnection(newValue, tcpPort)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_tcp_ip)) },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isIpError,
                        supportingText = if (isIpError) {
                            { Text(stringResource(R.string.settings_tcp_ip_error)) }
                        } else null
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty()) {
                                portInput = ""
                                appViewModel.updateTcpConnection(ipInput, 0)
                            } else if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                                portInput = newValue
                                val newPort = newValue.toIntOrNull() ?: 0
                                if (newPort <= 65535) {
                                    appViewModel.updateTcpConnection(ipInput, newPort)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_tcp_port)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isPortError,
                        supportingText = if (isPortError) {
                            { Text(stringResource(R.string.settings_tcp_port_error)) }
                        } else null
                    )
                    IconButton(
                        onClick = { appViewModel.toggleTcpConnection() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                            contentDescription = stringResource(
                                if (isConnected) R.string.tcp_disconnect else R.string.tcp_connect
                            )
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(60.dp)
                    ) {
                        if (connectionState == TcpConnectionState.Connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color.Gray
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connectionState) {
                                            TcpConnectionState.Connected -> Color.Green
                                            is TcpConnectionState.Error -> Color.Magenta
                                            else -> Color.Red
                                        }
                                    )
                                    .semantics { contentDescription = "TCP Status Indicator" }
                            )
                        }
                        Text(
                            text = when (connectionState) {
                                TcpConnectionState.Connected -> stringResource(R.string.tcp_status_connected)
                                TcpConnectionState.Connecting -> stringResource(R.string.tcp_status_waiting)
                                is TcpConnectionState.Error -> stringResource(R.string.tcp_status_error)
                                else -> stringResource(R.string.tcp_status_disconnected)
                            },
                            fontSize = 8.sp,
                            color = Color.Gray,
                            lineHeight = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ECG data ZIP archive — lets the user re-pick the source file.
                // The picker takes a persistable read permission so the chosen
                // file keeps working across reboots.
                val context = LocalContext.current
                val pickZipFile = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
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
                OutlinedButton(onClick = { pickZipFile.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                    Text(stringResource(R.string.data_source_change_folder))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Export ZIP: re-packs the in-memory dataset (with edits) to
                // a user-picked destination. SAF CreateDocument lets the user
                // choose the file name and folder; no auto-upload.
                val exportZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/zip")
                ) { uri ->
                    if (uri != null) appViewModel.exportZip(context, uri)
                }
                OutlinedButton(onClick = { exportZipLauncher.launch("ecg_export.zip") }) {
                    Text(stringResource(R.string.data_source_export_zip))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

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
