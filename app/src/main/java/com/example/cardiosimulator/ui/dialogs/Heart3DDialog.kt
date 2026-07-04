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

import com.example.cardiosimulator.ui.components.Heart3DController

@Composable
fun Heart3DDialog(
    initialBpm: Int = 75,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    val controller = remember { Heart3DController() }
    
    var isPlaying by remember { mutableStateOf(false) }
    var bpm by remember { mutableFloatStateOf(initialBpm.toFloat()) }
    var isXray by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isCutaway by remember { mutableStateOf(false) }
    var cutPosition by remember { mutableFloatStateOf(0.5f) }

    LaunchedEffect(Unit) {
        delay(15_000) // 15 second backstop
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
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
                    // Left Column: Controls
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Conduction Group
                        Text(
                            text = stringResource(R.string.monitor_3d_conduction),
                            style = MaterialTheme.typography.titleMedium,
                            color = WindowsBlue,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { 
                                    isPlaying = !isPlaying
                                    controller.setConductionPlaying(isPlaying)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = WindowsBlue),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(stringResource(if (isPlaying) R.string.monitor_3d_pause else R.string.monitor_3d_play))
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = "${stringResource(R.string.monitor_3d_rate)}: ${bpm.toInt()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = bpm,
                                    onValueChange = { 
                                        bpm = it
                                        controller.setBpm(it.toInt())
                                    },
                                    valueRange = 40f..180f,
                                    colors = SliderDefaults.colors(thumbColor = WindowsBlue, activeTrackColor = WindowsBlue)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.monitor_3d_xray), modifier = Modifier.weight(1f))
                            Switch(
                                checked = isXray,
                                onCheckedChange = { 
                                    isXray = it
                                    controller.setXray(it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WindowsBlue, checkedTrackColor = WindowsBlue.copy(alpha = 0.5f))
                            )
                        }

                        Button(
                            onClick = { 
                                isEditing = !isEditing
                                controller.setEditing(isEditing)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEditing) Color.Gray else WindowsBlue
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(stringResource(if (isEditing) R.string.monitor_3d_done_editing else R.string.monitor_3d_edit_pathway))
                        }

                        Divider(color = Color.LightGray, thickness = 1.dp)

                        // Cutaway Group
                        Text(
                            text = stringResource(R.string.monitor_3d_cutaway),
                            style = MaterialTheme.typography.titleMedium,
                            color = WindowsBlue,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.monitor_3d_cut_in_half), modifier = Modifier.weight(1f))
                            Switch(
                                checked = isCutaway,
                                onCheckedChange = { 
                                    isCutaway = it
                                    controller.setCutaway(it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WindowsBlue, checkedTrackColor = WindowsBlue.copy(alpha = 0.5f))
                            )
                        }

                        if (isCutaway) {
                            Column {
                                Text(
                                    text = stringResource(R.string.monitor_3d_cut_position),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = cutPosition,
                                    onValueChange = { 
                                        cutPosition = it
                                        controller.setCutPosition(it)
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(thumbColor = WindowsBlue, activeTrackColor = WindowsBlue)
                                )
                            }
                        }
                    }

                    // Middle Column: Description (Legacy placeholder)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WindowsBlue.copy(alpha = 0.1f))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.monitor_3d_description),
                            color = WindowsBlue,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Right Column: Heart Viewer
                    Column(
                        modifier = Modifier.weight(2f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Heart3DViewer(
                                    modifier = Modifier.fillMaxSize(),
                                    controller = controller,
                                    modelPath = "heart3d/heart.glb",
                                    onLoaded = { 
                                        isLoading = false 
                                        controller.setBpm(bpm.toInt())
                                    },
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
                    }
                }
            }
        }
    }
}
