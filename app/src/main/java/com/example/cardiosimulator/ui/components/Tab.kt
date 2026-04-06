package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Tab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    subText: String? = null,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    borderWidth: Dp = 1.dp
) {
    Column(
        modifier = modifier
            .fillMaxHeight(1f)
            .width(IntrinsicSize.Max)
            .defaultMinSize(minWidth = 56.dp)
            .border(borderWidth, Color.Black)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.Black,
                modifier = iconModifier
            )
        } else if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black
            )
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black
                )
            }
        }
    }
}
