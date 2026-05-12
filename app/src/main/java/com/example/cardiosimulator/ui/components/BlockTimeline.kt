package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.BlockFlags
import com.example.cardiosimulator.domain.SeriesPartRef
import com.example.cardiosimulator.domain.WaveformPart
import com.example.cardiosimulator.ui.display.ekgGrid

/**
 * One block on the series timeline. Width is proportional to part duration;
 * height is fixed. Thumbnail reuses [ChartCanvas]. Flag icons overlay the
 * top-right corner. Tap to select; long-press opens the flag menu.
 */
@Composable
fun TimelineBlock(
    part: WaveformPart,
    ref: SeriesPartRef,
    isSelected: Boolean,
    onTap: () -> Unit,
    onFlagToggle: (BlockFlags) -> Unit,
    onDragDelta: (Float) -> Unit,
    onResizeDelta: (Float) -> Unit,
    onJumpToPart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current
    // Block width derived from sample count * effective px-per-sample.
    val effPxPerSample = scale.pxPerSampleFor(part.effectiveSampleRateHz)
    val widthPx = (part.samples.size * effPxPerSample).coerceAtLeast(40f)
    val widthDp = with(density) { widthPx.toDp() }
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .padding(2.dp)
            .background(
                if (isSelected) Color(0xFFE3F2FD)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF1976D2) else Color.LightGray,
            )
            .pointerInput(part.identy) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { menuExpanded = true },
                    onDoubleTap = { onJumpToPart() },
                )
            }
    ) {
        // Thumbnail
        Box(modifier = Modifier.fillMaxSize().ekgGrid()) {
            ChartCanvas(
                points = Points(part.samples.map { (it - 1024f) }),
                sampleRateHz = part.effectiveSampleRateHz,
                samplesPerMv = part.samplesPerMv,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Title
        Text(
            text = part.title,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
        )
        // Flag icons (BEAT/SOUND/BROKEN/LOCK/SKIP/FREQ)
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
        ) {
            if (ref.flags.has(BlockFlags.BEAT)) BlockFlagDot(Color(0xFF388E3C), "B")
            if (ref.flags.has(BlockFlags.WITHSOUND)) BlockFlagDot(Color(0xFFFBC02D), "S")
            if (ref.flags.has(BlockFlags.BROKEN)) BlockFlagDot(Color(0xFFD32F2F), "X")
            if (ref.flags.has(BlockFlags.DURATIONFIXED)) BlockFlagDot(Color(0xFF455A64), "L")
            if (ref.flags.has(BlockFlags.SKIPQRS)) BlockFlagDot(Color(0xFF7B1FA2), "K")
            if (ref.flags.has(BlockFlags.FREQUENTLY)) BlockFlagDot(Color(0xFF1976D2), "F")
        }
        // Resize handle (right edge)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(8.dp)
                .fillMaxHeight()
                .background(Color.Gray.copy(alpha = 0.3f))
                .pointerInput(part.identy) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        onResizeDelta(delta.x)
                    }
                }
        )
        // Drag-to-reorder handle (entire body, minus right edge)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(widthDp - 8.dp)
                .pointerInput(part.identy) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        onDragDelta(delta.x)
                    }
                }
        )
        // Flag context menu
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            for ((flag, label) in flagLabels) {
                DropdownMenuItem(
                    text = {
                        Text((if (ref.flags.has(flag)) "✓ " else "  ") + label)
                    },
                    onClick = { onFlagToggle(flag); menuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun BlockFlagDot(color: Color, label: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .background(color)
            .width(14.dp)
            .height(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 9.sp)
    }
}

private val flagLabels = listOf(
    BlockFlags.FREQUENTLY to "Frequently",
    BlockFlags.WITHSOUND to "With sound",
    BlockFlags.DURATIONFIXED to "Duration fixed",
    BlockFlags.BROKEN to "Broken",
    BlockFlags.BEAT to "Beat",
    BlockFlags.SKIPQRS to "Skip QRS",
)

/**
 * Horizontal timeline of blocks. Selected block highlighted; flags shown
 * as overlay dots. Tap selects, long-press opens flag menu, double-tap
 * opens the part in the segment editor.
 */
@Composable
fun BlockTimeline(
    refs: List<SeriesPartRef>,
    parts: List<WaveformPart>,
    selectedIndex: Int?,
    onBlockTap: (Int) -> Unit,
    onFlagToggle: (Int, BlockFlags) -> Unit,
    onBlockReorder: (from: Int, to: Int) -> Unit,
    onBlockResize: (Int, deltaPx: Float) -> Unit,
    onJumpToPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scroll)
            .height(120.dp)
    ) {
        refs.forEachIndexed { idx, ref ->
            val part = parts.firstOrNull { it.identy == ref.partIdenty }
            if (part != null) {
                TimelineBlock(
                    part = part,
                    ref = ref,
                    isSelected = idx == selectedIndex,
                    onTap = { onBlockTap(idx) },
                    onFlagToggle = { f -> onFlagToggle(idx, f) },
                    onDragDelta = { dx ->
                        // Drag-to-reorder: jump indexes when crossing ~40 px
                        if (dx > 40f && idx < refs.lastIndex) onBlockReorder(idx, idx + 1)
                        else if (dx < -40f && idx > 0) onBlockReorder(idx, idx - 1)
                    },
                    onResizeDelta = { dx -> onBlockResize(idx, dx) },
                    onJumpToPart = { onJumpToPart(idx) },
                )
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}
