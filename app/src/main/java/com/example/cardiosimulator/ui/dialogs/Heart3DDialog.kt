package com.example.cardiosimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.Heart3DViewer
import kotlinx.coroutines.delay

@Composable
fun Heart3DDialog(onDismiss: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(15_000) // 15 second backstop
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = CreamBackground
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.monitor_3d_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Buttons
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DialogBlueButton(stringResource(R.string.monitor_3d_lead_scheme))
                        DialogBlueButton(stringResource(R.string.monitor_3d_function_format, 2))
                        DialogBlueButton(stringResource(R.string.monitor_3d_function_format, 3))
                        DialogBlueButton(stringResource(R.string.monitor_3d_mi))
                        DialogBlueButton(stringResource(R.string.monitor_3d_function_format, 5))
                        DialogBlueButton(stringResource(R.string.monitor_3d_function_format, 6))
                    }

                    // Middle Column: Description
                    Column(
                        modifier = Modifier
                            .weight(1.5f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WindowsBlue)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.monitor_3d_description),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.monitor_3d_or_ecg),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Right Column: Heart Image
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Heart3DViewer(
                                    modifier = Modifier.fillMaxSize(),
                                    modelPath = "heart3d/heart.glb",
                                    onLoaded = { isLoading = false },
                                    onError = { isLoading = false }
                                )

                                if (isLoading) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = WindowsBlue,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.monitor_3d_loading),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                        DialogBlueButton(stringResource(R.string.monitor_3d_ecg_lead))
                    }
                }
            }
        }
    }
}
