package com.example.cardiosimulator.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.HtmlBlock
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.dialogs.ComparisonTargetDialog
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context

@Composable
fun HtmlBlockEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    blocks: List<HtmlBlock>,
    onUpdateBlock: (String, HtmlBlock) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlock: (String, Int) -> Unit,
    onImportImage: (String, ByteArray) -> String? = { _, _ -> null },
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    scrollToBlockId: String? = null,
) {
    LaunchedEffect(scrollToBlockId) {
        if (scrollToBlockId != null) {
            val index = blocks.indexOfFirst { it.id == scrollToBlockId }
            if (index != -1) {
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    LazyColumn(
        state = lazyListState,
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
                    is HtmlBlock.Image -> ImageEditor(block, onImportImage) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.KaTeX -> KaTeXEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Ecg -> EcgEditor(appViewModel, rhythms, block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Table -> TableEditor(block) { onUpdateBlock(block.id, it) }
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
private fun ImageEditor(
    block: HtmlBlock.Image,
    onImportImage: (String, ByteArray) -> String?,
    onUpdate: (HtmlBlock.Image) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                val fileName = getFileName(context, it) ?: "image_${System.currentTimeMillis()}.png"
                val newPath = onImportImage(fileName, bytes)
                if (newPath != null) {
                    onUpdate(block.copy(src = newPath))
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.course_insert_image), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = block.src,
                onValueChange = { onUpdate(block.copy(src = it)) },
                label = { Text(stringResource(R.string.course_constructor_image_src)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = { launcher.launch("image/*") }) {
                Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.course_constructor_image_upload))
            }
        }
        TextField(
            value = block.alt,
            onValueChange = { onUpdate(block.copy(alt = it)) },
            label = { Text(stringResource(R.string.course_constructor_image_alt)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) return cursor.getString(index)
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}

private val KatexSymbols = listOf(
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\theta" to "θ", "\\lambda" to "λ", "\\pi" to "π", "\\sigma" to "σ", "\\omega" to "ω",
    "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω",
    "\\infty" to "∞", "\\approx" to "≈", "\\neq" to "≠", "\\le" to "≤", "\\ge" to "≥", "\\pm" to "±",
    "\\times" to "×", "\\div" to "÷", "\\sqrt{}" to "√", "\\frac{}{}" to "n/m", "^" to "xⁿ", "_" to "xₙ"
)

@Composable
private fun KaTeXEditor(block: HtmlBlock.KaTeX, onUpdate: (HtmlBlock.KaTeX) -> Unit) {
    var textFieldValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.expression, selection = TextRange(block.expression.length)))
    }

    LaunchedEffect(block.expression) {
        if (block.expression != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = block.expression,
                selection = TextRange(block.expression.length)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("KaTeX Math", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("Display Mode", style = MaterialTheme.typography.labelSmall)
            Checkbox(checked = block.displayMode, onCheckedChange = { onUpdate(block.copy(displayMode = it)) })
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(KatexSymbols) { (code, display) ->
                AssistChip(
                    onClick = {
                        val text = textFieldValue.text
                        val selection = textFieldValue.selection
                        val newText = text.substring(0, selection.start) + code + text.substring(selection.end)
                        val newSelection = TextRange(selection.start + code.length)
                        textFieldValue = TextFieldValue(newText, newSelection)
                        onUpdate(block.copy(expression = newText))
                    },
                    label = { Text(display) }
                )
            }
        }

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                if (it.text != block.expression) {
                    onUpdate(block.copy(expression = it.text))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. E = mc^2") },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        )
    }
}

@Composable
private fun EcgEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Ecg,
    onUpdate: (HtmlBlock.Ecg) -> Unit
) {
    var showSelector by remember { mutableStateOf(false) }
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    if (showSelector) {
        ComparisonTargetDialog(
            appViewModel = appViewModel,
            rhythms = rhythms,
            onDismiss = { showSelector = false },
            onTargetSelected = { target ->
                val newLeads = if (block.leads.contains(target.lead.name)) block.leads else block.leads + target.lead.name
                onUpdate(block.copy(pathology = target.pathologyId, leads = newLeads))
                showSelector = false
            },
            initialPathologyId = block.pathology,
            initialLead = block.leads.firstOrNull()?.let { Lead.fromToken(it) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ECG Reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        
        OutlinedCard(
            onClick = { showSelector = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Waves, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = remember(block.pathology, rhythms, selectedLanguage) {
                        if (block.pathology.isBlank()) "Select Rhythm..."
                        else {
                            val entry = rhythms.find { it.id == block.pathology }
                            if (entry != null) {
                                if (selectedLanguage == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn
                            } else {
                                block.pathology
                            }
                        }
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit Selection", modifier = Modifier.size(20.dp))
            }
        }

        // Multi-lead selection
        Text("Leads", style = MaterialTheme.typography.labelSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Lead.entries.forEach { lead ->
                val isSelected = block.leads.contains(lead.name)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newLeads = if (isSelected) block.leads - lead.name else block.leads + lead.name
                        onUpdate(block.copy(leads = newLeads))
                    },
                    label = { Text(lead.name) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Count
            var countExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { countExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Count: ${block.count}", style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = countExpanded, onDismissRequest = { countExpanded = false }) {
                    listOf(1, 2, 3, 4, 6, 12).forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.toString()) },
                            onClick = { onUpdate(block.copy(count = c)); countExpanded = false }
                        )
                    }
                }
            }

            // Grid Scheme
            var gridExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { gridExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Grid: ${block.gridScheme}", style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = gridExpanded, onDismissRequest = { gridExpanded = false }) {
                    GridScheme.entries.forEach { gs ->
                        DropdownMenuItem(
                            text = { Text(gs.name) },
                            onClick = { onUpdate(block.copy(gridScheme = gs.name)); gridExpanded = false }
                        )
                    }
                }
            }

            // Series Scheme
            var seriesExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { seriesExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Layout: ${block.seriesScheme}", style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = seriesExpanded, onDismissRequest = { seriesExpanded = false }) {
                    SeriesScheme.entries.forEach { ss ->
                        DropdownMenuItem(
                            text = { Text(ss.name) },
                            onClick = { onUpdate(block.copy(seriesScheme = ss.name)); seriesExpanded = false }
                        )
                    }
                }
            }
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
private fun TableEditor(block: HtmlBlock.Table, onUpdate: (HtmlBlock.Table) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Table", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

        val rows = block.rows
        val rowCount = rows.size
        val colCount = if (rowCount > 0) rows[0].size else 0

        // Table controls
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val newRows = if (rowCount == 0) {
                    listOf(listOf(""))
                } else {
                    rows.map { it + "" }
                }
                onUpdate(block.copy(rows = newRows))
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Col")
            }
            TextButton(onClick = {
                val newRows = rows + listOf(List(colCount.coerceAtLeast(1)) { "" })
                onUpdate(block.copy(rows = newRows))
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Row")
            }
        }

        // Grid of text fields
        if (rowCount > 0) {
            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                for (rowIndex in 0 until rowCount) {
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (colIndex in 0 until colCount) {
                            OutlinedTextField(
                                value = rows[rowIndex][colIndex],
                                onValueChange = { newValue ->
                                    val newRows = rows.mapIndexed { r, row ->
                                        if (r == rowIndex) {
                                            row.mapIndexed { c, cell ->
                                                if (c == colIndex) newValue else cell
                                            }
                                        } else row
                                    }
                                    onUpdate(block.copy(rows = newRows))
                                },
                                modifier = Modifier.width(180.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                minLines = 1,
                                maxLines = 5,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                val newRows = rows.filterIndexed { r, _ -> r != rowIndex }
                                onUpdate(block.copy(rows = newRows))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Delete Row", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Column delete buttons
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (colIndex in 0 until colCount) {
                        Box(modifier = Modifier.width(180.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = {
                                    val newRows = rows.map { row ->
                                        row.filterIndexed { c, _ -> c != colIndex }
                                    }.filter { it.isNotEmpty() }
                                    onUpdate(block.copy(rows = newRows))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Delete Col", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
