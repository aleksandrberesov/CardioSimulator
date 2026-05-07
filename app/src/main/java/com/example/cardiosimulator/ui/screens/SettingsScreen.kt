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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.FtpSendStatus
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
    val ftpPort by appViewModel.ftpPort.collectAsState()
    val ftpUser by appViewModel.ftpUser.collectAsState()
    val ftpPassword by appViewModel.ftpPassword.collectAsState()
    val ftpRemotePath by appViewModel.ftpRemotePath.collectAsState()
    val ftpStatus by appViewModel.ftpStatus.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        SettingsContent(
            gridScheme = monitorMode.gridScheme,
            selectedLanguage = selectedLanguage,
            tcpIp = tcpIp,
            tcpPort = tcpPort,
            ftpPort = ftpPort,
            ftpUser = ftpUser,
            ftpPassword = ftpPassword,
            ftpRemotePath = ftpRemotePath,
            ftpStatus = ftpStatus,
            onGridSchemeChange = { monitorViewModel.setGridScheme(it) },
            onLanguageChange = { appViewModel.updateLanguage(it) },
            onTcpChange = { ip, port -> appViewModel.updateTcpConnection(ip, port) },
            onFtpChange = { port, user, password, remotePath ->
                appViewModel.updateFtpCredentials(port, user, password, remotePath)
            },
            onSendModel = { appViewModel.sendModelViaFtp() },
            onDismiss = onDismiss,
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
    ftpPort: Int,
    ftpUser: String,
    ftpPassword: String,
    ftpRemotePath: String,
    ftpStatus: FtpSendStatus,
    onGridSchemeChange: (GridScheme) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onTcpChange: (String, Int) -> Unit,
    onFtpChange: (Int, String, String, String) -> Unit,
    onSendModel: () -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember(tcpIp) { mutableStateOf(tcpIp) }
    var portText by remember(tcpPort) { mutableStateOf(tcpPort.toString()) }
    var ftpPortText by remember(ftpPort) { mutableStateOf(ftpPort.toString()) }
    var ftpUserText by remember(ftpUser) { mutableStateOf(ftpUser) }
    var ftpPasswordText by remember(ftpPassword) { mutableStateOf(ftpPassword) }
    var ftpRemotePathText by remember(ftpRemotePath) { mutableStateOf(ftpRemotePath) }

    fun pushFtp() {
        onFtpChange(
            ftpPortText.toIntOrNull() ?: 0,
            ftpUserText,
            ftpPasswordText,
            ftpRemotePathText,
        )
    }

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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_ftp_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = ftpUserText,
                    onValueChange = {
                        ftpUserText = it
                        pushFtp()
                    },
                    label = { Text(stringResource(R.string.settings_ftp_user)) },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ftpPortText,
                    onValueChange = {
                        ftpPortText = it
                        pushFtp()
                    },
                    label = { Text(stringResource(R.string.settings_ftp_port)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ftpPasswordText,
                onValueChange = {
                    ftpPasswordText = it
                    pushFtp()
                },
                label = { Text(stringResource(R.string.settings_ftp_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ftpRemotePathText,
                onValueChange = {
                    ftpRemotePathText = it
                    pushFtp()
                },
                label = { Text(stringResource(R.string.settings_ftp_remote_path)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(12.dp))

            FtpStatusLine(status = ftpStatus)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSendModel,
                    enabled = ftpStatus !is FtpSendStatus.Sending,
                ) {
                    if (ftpStatus is FtpSendStatus.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_ftp_send))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.settings_close))
            }
        }
    }
}

@Composable
private fun FtpStatusLine(status: FtpSendStatus) {
    val (text, color) = when (status) {
        FtpSendStatus.Idle -> "" to MaterialTheme.colorScheme.onSurfaceVariant
        FtpSendStatus.Sending -> stringResource(R.string.settings_ftp_status_sending) to
            MaterialTheme.colorScheme.onSurfaceVariant
        is FtpSendStatus.Success -> stringResource(
            R.string.settings_ftp_status_success,
            status.bytes,
            status.remotePath,
        ) to MaterialTheme.colorScheme.primary
        is FtpSendStatus.Error -> {
            val msg = when (status.message) {
                "no_rhythm" -> stringResource(R.string.settings_ftp_error_no_rhythm)
                "invalid_settings" -> stringResource(R.string.settings_ftp_error_invalid_settings)
                else -> stringResource(R.string.settings_ftp_status_error, status.message)
            }
            msg to MaterialTheme.colorScheme.error
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
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
            ftpPort = 21,
            ftpUser = "root",
            ftpPassword = "",
            ftpRemotePath = "/tmp/cardio.csv",
            ftpStatus = FtpSendStatus.Idle,
            onGridSchemeChange = {},
            onLanguageChange = {},
            onTcpChange = { _, _ -> },
            onFtpChange = { _, _, _, _ -> },
            onSendModel = {},
            onDismiss = {}
        )
    }
}
