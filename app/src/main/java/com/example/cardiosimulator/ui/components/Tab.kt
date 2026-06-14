package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    enabled: Boolean = true,
    isRepeatable: Boolean = false,
    text: String? = null,
    subText: String? = null,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    iconContentDescription: String? = null,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 4.dp,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = Color.Black
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    
    val baseModifier = modifier
        .fillMaxHeight(1f)
        .defaultMinSize(minWidth = 35.dp)
        .border(borderWidth, if (enabled) Color.Black else Color.Gray, shape)
        .background(if (enabled) backgroundColor else Color.LightGray.copy(alpha = 0.3f), shape)
        .clip(shape)

    val clickModifier = if (isRepeatable) {
        Modifier
            .indication(interactionSource, ripple())
            .repeatingClickable(onClick = onClick, enabled = enabled, interactionSource = interactionSource)
    } else {
        Modifier.clickable(interactionSource = interactionSource, indication = ripple(), enabled = enabled, onClick = onClick)
    }

    val displayContentColor = if (enabled) contentColor else Color.Gray

    Column(
        modifier = baseModifier.then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription ?: text,
                tint = displayContentColor,
                modifier = iconModifier.padding(4.dp)
            )
        }
        
        when {
            text != null && subText != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    AutoResizeText(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = displayContentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        minFontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                    )
                    AutoResizeText(
                        text = subText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = displayContentColor,
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
                    color = displayContentColor,
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
