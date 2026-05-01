package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun Tab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    subText: String? = null,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    iconContentDescription: String? = null,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 4.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxHeight(1f)
            .defaultMinSize(minWidth = 35.dp)
            .border(borderWidth, Color.Black, shape)
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = Color.Black,
                    modifier = iconModifier.padding(4.dp)
                )
            }
            text != null && subText != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    AutoResizeText(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        minFontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                    )
                    AutoResizeText(
                        text = subText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        minFontSize = 7.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                    )
                }
            }
            text != null -> {
                AutoResizeText(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    minFontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 50)
@Composable
fun TabPreview() {
    CardioSimulatorTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Tab(
                text = "4x",
                onClick = {}
            )
            Tab(
                text = "25",
                subText = "mm/s",
                onClick = {}
            )
            Tab(
                icon = Icons.Default.Pause,
                onClick = {}
            )
        }
    }
}
