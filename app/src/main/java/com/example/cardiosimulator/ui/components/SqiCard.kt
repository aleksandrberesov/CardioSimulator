package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.signals.biosppy.SqiInfo

@Composable
fun SqiBadge(
    info: SqiInfo?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = when (info?.quality) {
            "Excellent" -> Color.Green
            "Barely acceptable", "Barely acceptable/Acceptable" -> Color(0xFFC5A000) // Amber/Gold
            null -> Color.Gray.copy(alpha = 0.5f)
            else -> Color.Red
        }

        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            val qualityPrefix = stringResource(R.string.monitor_signal_quality)
            val qualityLabel = info?.quality ?: "—"
            val leadSuffix = info?.lead?.let { " (${it.name})" } ?: ""
            
            Text(
                text = "$qualityPrefix: $qualityLabel$leadSuffix",
                color = LocalContentColor.current,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            
            val details = if (info != null) {
                "sSQI: %.2f | kSQI: %.2f | pSQI: %.1f%%".format(info.sSqi, info.kSqi, info.pSqi * 100)
            } else {
                stringResource(R.string.monitor_signal_quality_unavailable)
            }
            
            Text(
                text = details,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 13.sp
            )
        }
    }
}
