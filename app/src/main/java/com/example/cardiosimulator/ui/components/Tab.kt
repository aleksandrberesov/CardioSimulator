package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.ui.theme.*

@Composable
fun Tab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    showChevron: Boolean = false,
    isRepeatable: Boolean = false,
    text: String? = null,
    subText: String? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconModifier: Modifier = Modifier,
    iconContentDescription: String? = null,
    cornerRadius: Dp = 8.dp,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = TextPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }

    val resolvedBgColor = when {
        !enabled -> Color.LightGray.copy(alpha = 0.3f)
        showChevron -> PanelBackground
        isActive -> AccentGreen
        else -> backgroundColor
    }

    val resolvedContentColor = when {
        !enabled -> Color.Gray
        isActive -> OnAccent
        showChevron -> TextPrimary
        else -> contentColor
    }

    val shape = RoundedCornerShape(cornerRadius)
    
    val baseModifier = modifier
        .fillMaxHeight(1f)
        .defaultMinSize(minWidth = 35.dp)
        .then(
            if (showChevron) {
                Modifier.border(1.dp, ControlBorder, shape)
            } else {
                Modifier
            }
        )
        .background(resolvedBgColor, shape)
        .clip(shape)

    val clickModifier = if (isRepeatable) {
        Modifier
            .indication(interactionSource, ripple(color = if (isActive) Color.White else HoverFill))
            .repeatingClickable(onClick = onClick, enabled = enabled, interactionSource = interactionSource)
    } else {
        Modifier.clickable(
            interactionSource = interactionSource, 
            indication = ripple(color = if (isActive) Color.White else HoverFill), 
            enabled = enabled, 
            onClick = onClick
        )
    }

    Row(
        modifier = baseModifier
            .then(clickModifier)
            .padding(horizontal = if (showChevron) 9.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription ?: text,
                    tint = resolvedContentColor,
                    modifier = iconModifier.padding(4.dp)
                )
            } else if (painter != null) {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = iconContentDescription ?: text,
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
                            color = resolvedContentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            minFontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                        AutoResizeText(
                            text = subText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = resolvedContentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            minFontSize = 7.sp,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                    }
                }
                text != null -> {
                    AutoResizeText(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = resolvedContentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        minFontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                    )
                }
            }
        }
        
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp).padding(start = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 50)
@Composable
fun TabPreview() {
    CardioSimulatorTheme {
        Row(
            modifier = Modifier.padding(16.dp).background(PageBackground),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Tab(
                text = "4x",
                onClick = {}
            )
            Tab(
                text = "25",
                subText = "mm/s",
                showChevron = true,
                onClick = {}
            )
            Tab(
                isActive = true,
                icon = Icons.Default.Pause,
                onClick = {}
            )
        }
    }
}
