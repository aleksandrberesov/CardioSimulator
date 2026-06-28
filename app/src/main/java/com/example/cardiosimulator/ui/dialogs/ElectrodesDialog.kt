package com.example.cardiosimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R

@Composable
fun ElectrodesDialog(onDismiss: () -> Unit) {
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
                    Surface(
                        color = WindowsBlue,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.electrodes_system_standard),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
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
                    // Left Column: Chest placement
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageCard(
                            painter = painterResource(R.drawable.electrodes_chest),
                            caption = stringResource(R.string.electrodes_caption_chest)
                        )
                        ImageCard(
                            painter = painterResource(R.drawable.electrodes_cross),
                            caption = stringResource(R.string.electrodes_caption_cross)
                        )
                    }

                    // Middle Column: Legend and States
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LegendRow(Color(0xFFE53935), stringResource(R.string.electrodes_ra))
                            LegendRow(Color(0xFFFDD835), stringResource(R.string.electrodes_la))
                            LegendRow(Color(0xFF43A047), stringResource(R.string.electrodes_rl))
                            LegendRow(Color(0xFF101010), stringResource(R.string.electrodes_ll))
                            Spacer(modifier = Modifier.height(8.dp))
                            LegendRow(Color(0xFFE53935), stringResource(R.string.electrodes_v1))
                            LegendRow(Color(0xFFFDD835), stringResource(R.string.electrodes_v2))
                            LegendRow(Color(0xFF43A047), stringResource(R.string.electrodes_v3))
                            LegendRow(Color(0xFF8D6E63), stringResource(R.string.electrodes_v4))
                            LegendRow(Color(0xFF101010), stringResource(R.string.electrodes_v5))
                            LegendRow(Color(0xFF8E24AA), stringResource(R.string.electrodes_v6))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DialogBlueButton(stringResource(R.string.electrodes_state_ok), Modifier.weight(1f))
                            DialogBlueButton(stringResource(R.string.electrodes_state_swapped), Modifier.weight(1f))
                            DialogBlueButton(stringResource(R.string.electrodes_state_displacement), Modifier.weight(1f))
                        }
                    }

                    // Right Column: Full body
                    Column(modifier = Modifier.weight(0.8f)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.electrodes_body),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCard(painter: androidx.compose.ui.graphics.painter.Painter, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentScale = ContentScale.Fit
            )
        }
        Text(text = caption, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .border(0.5.dp, Color.Gray, CircleShape)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp),
            lineHeight = 14.sp
        )
    }
}
