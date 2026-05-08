package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun AppControlPanel(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val operatingModes = viewModel.operatingModes
    val selectedOperatingMode by viewModel.selectedOperatingMode.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1.5f).fillMaxWidth()
                ) {
                    Tab(
                        text = stringResource(selectedOperatingMode.id.titleRes),
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        operatingModes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(item.id.titleRes)) },
                                onClick = {
                                    viewModel.updateOperatingMode(item)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(5f).fillMaxWidth()
                ){
                    when (selectedOperatingMode.id) {
                        OperatingMode.Teaching -> TeachingControlPanel()
                        OperatingMode.Testing -> TestingControlPanel(viewModel = viewModel)
                        OperatingMode.Examination -> {}
                        OperatingMode.OSKE -> {}
                        OperatingMode.Editor -> {}
                    }
                }
                val isConnected by viewModel.isTcpConnected.collectAsState()
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tab(
                        icon = if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                        iconContentDescription = stringResource(
                            if (isConnected) R.string.tcp_disconnect else R.string.tcp_connect
                        ),
                        onClick = { viewModel.toggleTcpConnection() }
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(60.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color.Green else Color.Red)
                                .semantics { contentDescription = "TCP Status Indicator" }
                        )
                        Text(
                            text = stringResource(if (isConnected) R.string.tcp_status_connected else R.string.tcp_status_disconnected),
                            fontSize = 8.sp,
                            color = Color.Gray,
                            lineHeight = 10.sp
                        )
                    }
                }
                Tab(
                    icon = Icons.Default.Settings,
                    iconContentDescription = stringResource(R.string.cd_settings),
                    onClick = onSettingsClick,
                    borderWidth = 0.dp
                )
            }
            Image(
                painter = painterResource(id = R.drawable.main_logo),
                contentDescription = "Company Logo"
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 100)
@Composable
fun AppControlPanelPreview() {
    val previewViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    appState = AppBuilder()
                        .addMode(OperatingModeModel(OperatingMode.Teaching))
                        .build(),
                ) as T
            }
        }
    )
    CardioSimulatorTheme {
        AppControlPanel(
            viewModel = previewViewModel
        )
    }
}
