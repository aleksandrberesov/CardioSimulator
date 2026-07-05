package com.example.cardiosimulator.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.EcgSpan
import com.example.cardiosimulator.domain.EosAxisClass
import com.example.cardiosimulator.domain.EosResult
import kotlin.math.*

private val WindowsBlue = Color(0xFF5B9BD5)

@Composable
fun EosOverlay(
    result: EosResult?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .width(360.dp)
            .heightIn(max = 600.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WindowsBlue.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.monitor_eos_window_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.monitor_eos_intro),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Steps 1-7
                for (i in 1..7) {
                    val stepText = when (i) {
                        1 -> stringResource(R.string.monitor_eos_step_1)
                        2 -> stringResource(R.string.monitor_eos_step_2)
                        3 -> stringResource(R.string.monitor_eos_step_3)
                        4 -> stringResource(R.string.monitor_eos_step_4)
                        5 -> stringResource(R.string.monitor_eos_step_5)
                        6 -> stringResource(R.string.monitor_eos_step_6)
                        7 -> stringResource(R.string.monitor_eos_step_7)
                        else -> ""
                    }
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "$i.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(text = stepText, color = Color.White, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Diagram
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EosDiagram(result)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Measured readout
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (result == null) {
                            Text(
                                text = stringResource(R.string.monitor_eos_no_data),
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.monitor_eos_measured_header),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            val netI = result.leadI.netMm
                            val netAvf = result.leadAvf.netMm
                            
                            Text(
                                text = stringResource(
                                    R.string.monitor_eos_lead_format,
                                    "I",
                                    "%.1f".format(result.leadI.qMm),
                                    "%.1f".format(result.leadI.rMm),
                                    "%.1f".format(result.leadI.sMm),
                                    "a",
                                    "%.1f".format(netI)
                                ),
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(
                                    R.string.monitor_eos_lead_format,
                                    "aVF",
                                    "%.1f".format(result.leadAvf.qMm),
                                    "%.1f".format(result.leadAvf.rMm),
                                    "%.1f".format(result.leadAvf.sMm),
                                    "b",
                                    "%.1f".format(netAvf)
                                ),
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            
                            val variantName = getVariantName(result.axisClass)
                            Text(
                                text = stringResource(
                                    R.string.monitor_eos_angle_format,
                                    "%.0f".format(result.angleDeg),
                                    variantName
                                ),
                                color = Color.Yellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Variants list
                Text(
                    text = stringResource(R.string.monitor_eos_variants_header),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                EosAxisClass.entries.forEach { cls ->
                    val resId = when (cls) {
                        EosAxisClass.Normal -> R.string.monitor_eos_variant_normal
                        EosAxisClass.Horizontal -> R.string.monitor_eos_variant_horizontal
                        EosAxisClass.Vertical -> R.string.monitor_eos_variant_vertical
                        EosAxisClass.LeftDeviation -> R.string.monitor_eos_variant_left
                        EosAxisClass.RightDeviation -> R.string.monitor_eos_variant_right
                        EosAxisClass.ExtremeDeviation -> R.string.monitor_eos_variant_extreme
                    }
                    val fullText = stringResource(resId)
                    val isActive = result?.axisClass == cls
                    
                    VariantRow(fullText, isActive)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VariantRow(fullText: String, isActive: Boolean) {
    val parts = fullText.split(":", "：", limit = 2)
    val name = parts.getOrNull(0) ?: fullText
    val range = parts.getOrNull(1) ?: ""
    val separator = if (fullText.contains("：")) "：" else ":"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isActive) Color.Yellow else Color.White,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
        if (range.isNotEmpty()) {
            Text(
                text = "$separator$range",
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun getVariantName(cls: EosAxisClass): String {
    val resId = when (cls) {
        EosAxisClass.Normal -> R.string.monitor_eos_variant_normal
        EosAxisClass.Horizontal -> R.string.monitor_eos_variant_horizontal
        EosAxisClass.Vertical -> R.string.monitor_eos_variant_vertical
        EosAxisClass.LeftDeviation -> R.string.monitor_eos_variant_left
        EosAxisClass.RightDeviation -> R.string.monitor_eos_variant_right
        EosAxisClass.ExtremeDeviation -> R.string.monitor_eos_variant_extreme
    }
    val fullText = stringResource(resId)
    return fullText.split(":", "：", limit = 2)[0]
}

@Composable
private fun EosDiagram(result: EosResult?) {
    Canvas(modifier = Modifier.size(190.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.width / 2
        
        // 1. Faint hexaxial background
        val spokeColor = Color.White.copy(alpha = 0.2f)
        for (i in 0 until 6) {
            val angle = i * 30.0 * PI / 180.0
            val dx = (r * cos(angle)).toFloat()
            val dy = (r * sin(angle)).toFloat()
            drawLine(spokeColor, center - Offset(dx, dy), center + Offset(dx, dy), strokeWidth = 1.dp.toPx())
        }
        drawCircle(spokeColor, radius = r, center = center, style = Stroke(width = 1.dp.toPx()))

        // 2. Main axes I and aVF
        val axisColor = Color.White.copy(alpha = 0.6f)
        drawLine(axisColor, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 2.dp.toPx())
        drawLine(axisColor, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 2.dp.toPx())

        // 3. Vectors
        val a = result?.leadI?.netMm ?: 2.0
        val b = result?.leadAvf?.netMm ?: 6.0
        val maxVal = maxOf(abs(a), abs(b), 1e-3)
        val unit = (r * 0.85f) / maxVal.toFloat()

        val vecA = Offset((a * unit).toFloat(), 0f)
        val vecB = Offset(0f, (b * unit).toFloat())
        
        // Red vector a along I
        drawLine(Color.Red, center, center + vecA, strokeWidth = 3.dp.toPx())
        // Green vector b along aVF
        drawLine(Color(0xFF4CAF50), center, center + vecB, strokeWidth = 3.dp.toPx())

        // Dashed rectangle
        if (result != null) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(Color.White.copy(alpha = 0.5f), center + vecA, center + vecA + vecB, strokeWidth = 1.dp.toPx(), pathEffect = dash)
            drawLine(Color.White.copy(alpha = 0.5f), center + vecB, center + vecA + vecB, strokeWidth = 1.dp.toPx(), pathEffect = dash)
            
            // Blue resultant
            drawLine(Color(0xFF2196F3), center, center + vecA + vecB, strokeWidth = 4.dp.toPx())
        }

        // Labels
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 12.dp.toPx()
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.nativeCanvas.drawText("I", size.width - 15.dp.toPx(), center.y - 5.dp.toPx(), paint)
            canvas.nativeCanvas.drawText("aVF", center.x + 5.dp.toPx(), size.height - 5.dp.toPx(), paint)
            
            paint.color = android.graphics.Color.RED
            canvas.nativeCanvas.drawText("a", center.x + (a * unit).toFloat() / 2, center.y - 5.dp.toPx(), paint)
            
            paint.color = 0xFF4CAF50.toInt()
            canvas.nativeCanvas.drawText("b", center.x + 5.dp.toPx(), center.y + (b * unit).toFloat() / 2, paint)

            if (result != null) {
                paint.color = 0xFF2196F3.toInt()
                canvas.nativeCanvas.drawText("α", center.x + (a * unit).toFloat() + 5.dp.toPx(), center.y + (b * unit).toFloat() + 5.dp.toPx(), paint)
            }
        }
    }
}

@Composable
fun EosHighlightOverlay(
    points: Points,
    spans: List<EcgSpan>,
    modifier: Modifier = Modifier
) {
    val scale = LocalPixelScale.current
    val stepX = scale.pxPerSample
    
    val fillColor = Color(0x331E88E5)
    val edgeColor = Color(0x991E88E5)

    Canvas(modifier = modifier.fillMaxSize()) {
        spans.forEach { span ->
            val start = span.startSample.coerceIn(points.values.indices)
            val end = span.endSample.coerceIn(points.values.indices)
            if (end > start) {
                val x1 = start * stepX
                val x2 = end * stepX
                
                drawRect(
                    color = fillColor,
                    topLeft = Offset(x1, 0f),
                    size = androidx.compose.ui.geometry.Size(x2 - x1, size.height)
                )
                drawLine(
                    color = edgeColor,
                    start = Offset(x1, 0f),
                    end = Offset(x1, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = edgeColor,
                    start = Offset(x2, 0f),
                    end = Offset(x2, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
