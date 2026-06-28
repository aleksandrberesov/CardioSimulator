package com.example.cardiosimulator.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal val CreamBackground = Color(0xFFF2EFE6)
internal val WindowsBlue = Color(0xFF5B9BD5)

@Composable
internal fun DialogBlueButton(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = WindowsBlue,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
