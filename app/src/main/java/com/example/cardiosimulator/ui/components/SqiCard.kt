package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.signals.biosppy.QrsSegmenters
import com.example.cardiosimulator.signals.biosppy.Sqi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SqiInfo(
    val quality: String,
    val sSqi: Double,
    val kSqi: Double,
    val pSqi: Double
)

@Composable
fun SqiCard(
    signal: DoubleArray,
    samplingRate: Double,
    modifier: Modifier = Modifier
) {
    var sqiData by remember { mutableStateOf<SqiInfo?>(null) }

    LaunchedEffect(signal, samplingRate) {
        if (signal.size < 500) {
            sqiData = null
            return@LaunchedEffect
        }
        
        sqiData = withContext(Dispatchers.Default) {
            try {
                val det1 = QrsSegmenters.hamiltonSegmenter(signal, samplingRate)
                val det2 = QrsSegmenters.ssfSegmenter(signal, samplingRate)
                val quality = Sqi.zz2018(signal, det1, det2, samplingRate, mode = "fuzzy")
                val sSqi = Sqi.ssqi(signal)
                val kSqi = Sqi.ksqi(signal)
                val pSqi = Sqi.psqi(signal)
                SqiInfo(quality, sSqi, kSqi, pSqi)
            } catch (e: Exception) {
                null
            }
        }
    }

    sqiData?.let { data ->
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = when (data.quality) {
                    "Excellent" -> Color.Green
                    "Barely acceptable" -> Color.Yellow
                    else -> Color.Red
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = data.quality,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "sSQI: %.2f  kSQI: %.2f  pSQI: %.1f%%".format(data.sSqi, data.kSqi, data.pSqi),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}
