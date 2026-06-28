package com.example.cardiosimulator.ui.components

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R

private val WindowsBlue = Color(0xFF5B9BD5)

@Composable
fun EosOverlay(onClose: () -> Unit, modifier: Modifier = Modifier) {
    // ... existing EosOverlay implementation
}

@Composable
fun TipsOverlay(
    selectedKind: com.example.cardiosimulator.domain.TipOverlayKind,
    onKindSelected: (com.example.cardiosimulator.domain.TipOverlayKind) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(WindowsBlue.copy(alpha = 0.8f))
            .padding(top = 16.dp, bottom = 16.dp, start = 12.dp, end = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.monitor_tips_window_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clickable { onClose() }
                )
            }

            Text(
                text = stringResource(R.string.monitor_tips_types_header),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TipKindChip(1, com.example.cardiosimulator.domain.TipOverlayKind.Arrow, stringResource(R.string.monitor_tips_type_arrow), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.Arrow) { onKindSelected(it) }
                TipKindChip(2, com.example.cardiosimulator.domain.TipOverlayKind.LeadArea, stringResource(R.string.monitor_tips_type_lead_area), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.LeadArea) { onKindSelected(it) }
                TipKindChip(3, com.example.cardiosimulator.domain.TipOverlayKind.GraphArea, stringResource(R.string.monitor_tips_type_graph_area), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.GraphArea) { onKindSelected(it) }
                TipKindChip(4, com.example.cardiosimulator.domain.TipOverlayKind.EcgPart, stringResource(R.string.monitor_tips_type_ecg_part), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.EcgPart) { onKindSelected(it) }
                TipKindChip(5, com.example.cardiosimulator.domain.TipOverlayKind.VerticalLines, stringResource(R.string.monitor_tips_type_vertical_lines), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.VerticalLines) { onKindSelected(it) }
                TipKindChip(6, com.example.cardiosimulator.domain.TipOverlayKind.HorizontalLines, stringResource(R.string.monitor_tips_type_horizontal_lines), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.HorizontalLines) { onKindSelected(it) }
                TipKindChip(7, com.example.cardiosimulator.domain.TipOverlayKind.Label, stringResource(R.string.monitor_tips_type_label), selectedKind == com.example.cardiosimulator.domain.TipOverlayKind.Label) { onKindSelected(it) }
            }

            // Preview Card
            Surface(
                color = Color.White.copy(alpha = 0.95f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.monitor_tips_preview_header),
                        color = Color(0xFF1E5FA5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    repeat(4) { i ->
                        Text(
                            text = "${i + 1}.  ……",
                            color = Color(0xFF999999),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.monitor_tips_note),
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TipKindChip(
    number: Int,
    kind: com.example.cardiosimulator.domain.TipOverlayKind,
    label: String,
    isSelected: Boolean,
    onClick: (com.example.cardiosimulator.domain.TipOverlayKind) -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = if (isSelected) 0.35f else 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick(kind) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$number.",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            
            TipPictogram(kind = kind, modifier = Modifier.size(26.dp, 20.dp))
            
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TipPictogram(kind: com.example.cardiosimulator.domain.TipOverlayKind, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.6.dp.toPx()
        val color = Color.White
        val w = size.width
        val h = size.height
        
        when (kind) {
            com.example.cardiosimulator.domain.TipOverlayKind.Arrow -> {
                drawLine(color, Offset(2f, h - 2f), Offset(w - 4f, 4f), strokeWidth = strokeWidth)
                // Small arrowhead
                drawLine(color, Offset(w - 4f, 4f), Offset(w - 10f, 4f), strokeWidth = strokeWidth)
                drawLine(color, Offset(w - 4f, 4f), Offset(w - 4f, 10f), strokeWidth = strokeWidth)
            }
            com.example.cardiosimulator.domain.TipOverlayKind.LeadArea -> {
                drawRect(color.copy(alpha = 0.3f), Offset(2f, 2f), size = androidx.compose.ui.geometry.Size(w - 4f, h - 4f))
                drawRect(color, Offset(2f, 2f), size = androidx.compose.ui.geometry.Size(w - 4f, h - 4f), style = Stroke(1f))
            }
            com.example.cardiosimulator.domain.TipOverlayKind.GraphArea -> {
                drawRect(color.copy(alpha = 0.3f), Offset(w/4f, h/4f), size = androidx.compose.ui.geometry.Size(w/2f, h/2f))
                drawRect(color, Offset(w/4f, h/4f), size = androidx.compose.ui.geometry.Size(w/2f, h/2f), style = Stroke(1f))
            }
            com.example.cardiosimulator.domain.TipOverlayKind.EcgPart -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h/2f)
                    lineTo(w/3f, h/2f)
                    lineTo(w/2f, 2f)
                    lineTo(w*2/3f, h - 2f)
                    lineTo(w*3/4f, h/2f)
                    lineTo(w, h/2f)
                }
                drawPath(path, color, style = Stroke(strokeWidth))
            }
            com.example.cardiosimulator.domain.TipOverlayKind.VerticalLines -> {
                drawLine(color, Offset(w/3f, 0f), Offset(w/3f, h), strokeWidth = strokeWidth)
                drawLine(color, Offset(w*2/3f, 0f), Offset(w*2/3f, h), strokeWidth = strokeWidth)
            }
            com.example.cardiosimulator.domain.TipOverlayKind.HorizontalLines -> {
                drawLine(color, Offset(0f, h/3f), Offset(w, h/3f), strokeWidth = strokeWidth)
                drawLine(color, Offset(0f, h*2/3f), Offset(w, h*2/3f), strokeWidth = strokeWidth)
            }
            com.example.cardiosimulator.domain.TipOverlayKind.Label -> {
                drawRect(color.copy(alpha = 0.3f), Offset(2f, 2f), size = androidx.compose.ui.geometry.Size(w - 4f, h - 4f))
                drawLine(color, Offset(6f, h/2f - 2f), Offset(w - 6f, h/2f - 2f), strokeWidth = 1f)
                drawLine(color, Offset(6f, h/2f + 2f), Offset(w*2/3f, h/2f + 2f), strokeWidth = 1f)
            }
        }
    }
}
