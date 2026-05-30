package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.CourseBlock
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.ui.display.Lead as LeadView

/**
 * Renders a parsed [Lecture] body. Interleaves Markdown text with
 * native Compose overlays for ECG embeds and (eventually) editable tables.
 *
 * Current implementation is a skeleton:
 * - Markdown is rendered as plain [Text].
 * - ECG embeds reuse [LeadView] to show a static trace.
 * - Tables are placeholders.
 */
@Composable
fun MarkdownRenderer(
    lecture: Lecture,
    pathologyRepository: PathologyRepository,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(lecture.blocks) { block ->
            when (block) {
                is CourseBlock.Markdown -> {
                    // TODO: Use a real Markdown library for Compose here.
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is CourseBlock.EcgEmbed -> {
                    EcgEmbedRenderer(block, pathologyRepository)
                }
                is CourseBlock.EditableTable -> {
                    EditableTable(
                        block = block,
                        onValueChange = { row, col, value ->
                            // TODO: Implement answer persistence (Phase 3b/4)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EcgEmbedRenderer(
    block: CourseBlock.EcgEmbed,
    repository: PathologyRepository,
) {
    val lead = block.lead ?: com.example.cardiosimulator.domain.Lead.II
    val points = remember(block.pathologyId, lead) {
        repository.leadWaveform(block.pathologyId, lead)
            ?: com.example.cardiosimulator.data.Points(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        block.caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        LeadView(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            points = points,
            title = lead.name,
            isRunning = false,
            xOffsetPx = 0f,
        )
    }
}
