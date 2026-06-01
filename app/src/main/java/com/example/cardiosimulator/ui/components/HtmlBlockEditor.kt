package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.domain.HtmlBlock

@Composable
fun HtmlBlockEditor(
    blocks: List<HtmlBlock>,
    onUpdateBlock: (String, HtmlBlock) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlock: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(blocks, key = { it.id }) { block ->
            BlockWrapper(
                onDelete = { onDeleteBlock(block.id) },
                onMoveUp = { onMoveBlock(block.id, -1) },
                onMoveDown = { onMoveBlock(block.id, 1) }
            ) {
                when (block) {
                    is HtmlBlock.Header -> HeaderEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Paragraph -> ParagraphEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Image -> ImageEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.KaTeX -> KaTeXEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Ecg -> EcgEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.RawHtml -> RawHtmlEditor(block) { onUpdateBlock(block.id, it) }
                }
            }
        }
    }
}

@Composable
private fun BlockWrapper(
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderEditor(block: HtmlBlock.Header, onUpdate: (HtmlBlock.Header) -> Unit) {
    Column {
        Text("Header L${block.level}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        TextField(
            value = block.text,
            onValueChange = { onUpdate(block.copy(text = it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            placeholder = { Text("Header text...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ParagraphEditor(block: HtmlBlock.Paragraph, onUpdate: (HtmlBlock.Paragraph) -> Unit) {
    Column {
        Text("Paragraph", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = block.html,
            onValueChange = { onUpdate(block.copy(html = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter text or simple HTML...") },
            minLines = 2
        )
    }
}

@Composable
private fun ImageEditor(block: HtmlBlock.Image, onUpdate: (HtmlBlock.Image) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Image", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        TextField(
            value = block.src,
            onValueChange = { onUpdate(block.copy(src = it)) },
            label = { Text("Source (URL or assets/path)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextField(
            value = block.alt,
            onValueChange = { onUpdate(block.copy(alt = it)) },
            label = { Text("Alt Text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun KaTeXEditor(block: HtmlBlock.KaTeX, onUpdate: (HtmlBlock.KaTeX) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("KaTeX Math", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("Display Mode", style = MaterialTheme.typography.labelSmall)
            Checkbox(checked = block.displayMode, onCheckedChange = { onUpdate(block.copy(displayMode = it)) })
        }
        OutlinedTextField(
            value = block.expression,
            onValueChange = { onUpdate(block.copy(expression = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. E = mc^2") },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        )
    }
}

@Composable
private fun EcgEditor(block: HtmlBlock.Ecg, onUpdate: (HtmlBlock.Ecg) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ECG Reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = block.pathology,
                onValueChange = { onUpdate(block.copy(pathology = it)) },
                label = { Text("Pathology ID") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            TextField(
                value = block.lead ?: "",
                onValueChange = { onUpdate(block.copy(lead = it.takeIf { it.isNotBlank() })) },
                label = { Text("Lead (optional)") },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }
        TextField(
            value = block.caption,
            onValueChange = { onUpdate(block.copy(caption = it)) },
            label = { Text("Caption") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun RawHtmlEditor(block: HtmlBlock.RawHtml, onUpdate: (HtmlBlock.RawHtml) -> Unit) {
    Column {
        Text("Raw HTML / Table", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = block.html,
            onValueChange = { onUpdate(block.copy(html = it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp),
            minLines = 3
        )
    }
}
