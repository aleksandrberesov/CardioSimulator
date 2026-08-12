package com.example.cardiosimulator.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.domain.*
import com.example.cardiosimulator.ui.dialogs.ComparisonTargetDialog
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HtmlBlockEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    blocks: List<HtmlBlock>,
    onUpdateBlock: (String, HtmlBlock) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlock: (String, Int) -> Unit,
    onAddBlock: (HtmlBlock) -> Unit,
    onImportImage: (String, ByteArray) -> String? = { _, _ -> null },
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    scrollToBlockId: String? = null,
    onExecuteJs: ((String) -> Unit)? = null,
    editElementId: String? = null,
    onEditHandled: () -> Unit = {},
) {
    var autoOpenBlockId by remember { mutableStateOf<String?>(null) }
    var autoEditNodeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scrollToBlockId) {
        if (scrollToBlockId != null) {
            val index = blocks.indexOfFirst { it.id == scrollToBlockId }
            if (index != -1) {
                lazyListState.animateScrollToItem(index + 1) // +1 for AddBar
            }
        }
    }

    LaunchedEffect(editElementId) {
        if (editElementId != null) {
            // 1. Top-level block
            val directBlock = blocks.firstOrNull { it.id == editElementId }
            if (directBlock != null) {
                if (directBlock is HtmlBlock.Ecg || directBlock is HtmlBlock.EcgSegment) {
                    autoOpenBlockId = editElementId
                } else {
                    val index = blocks.indexOfFirst { it.id == editElementId }
                    if (index != -1) lazyListState.animateScrollToItem(index + 1)
                }
                onEditHandled()
                return@LaunchedEffect
            }

            // 2. Nested element
            val owner = blocks.firstOrNull { block ->
                val body = bodyHtmlOf(block) ?: ""
                HtmlStructure.nodeById(body, editElementId) != null
            }
            if (owner != null) {
                val index = blocks.indexOfFirst { it.id == owner.id }
                if (index != -1) lazyListState.animateScrollToItem(index + 1)
                autoOpenBlockId = owner.id
                autoEditNodeId = editElementId
                onEditHandled()
            } else {
                onEditHandled()
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AddBar(onAddBlock)
        }
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
                    is HtmlBlock.Ecg -> EcgEditor(
                        appViewModel, rhythms, block,
                        autoOpen = autoOpenBlockId == block.id,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onOpened = { if (autoOpenBlockId == block.id) autoOpenBlockId = null }
                    )
                    is HtmlBlock.EcgSegment -> EcgSegmentEditor(
                        appViewModel, rhythms, block,
                        autoOpen = autoOpenBlockId == block.id,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onOpened = { if (autoOpenBlockId == block.id) autoOpenBlockId = null }
                    )
                    is HtmlBlock.Table -> TableEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.HtmlList -> HtmlListEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Quote -> QuoteEditor(block) { onUpdateBlock(block.id, it) }
                    is HtmlBlock.Note -> NoteEditor(
                        appViewModel = appViewModel,
                        rhythms = rhythms,
                        block = block,
                        blocks = blocks,
                        autoEditNodeId = if (autoOpenBlockId == block.id) autoEditNodeId else null,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onImportImage = onImportImage,
                        onExecuteJs = onExecuteJs,
                        onAutoEditHandled = { autoOpenBlockId = null; autoEditNodeId = null }
                    )
                    is HtmlBlock.Card -> CardEditor(
                        appViewModel = appViewModel,
                        rhythms = rhythms,
                        block = block,
                        blocks = blocks,
                        autoEditNodeId = if (autoOpenBlockId == block.id) autoEditNodeId else null,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onImportImage = onImportImage,
                        onExecuteJs = onExecuteJs,
                        onAutoEditHandled = { autoOpenBlockId = null; autoEditNodeId = null }
                    )
                    is HtmlBlock.Section -> SectionEditor(
                        appViewModel = appViewModel,
                        rhythms = rhythms,
                        block = block,
                        blocks = blocks,
                        autoEditNodeId = if (autoOpenBlockId == block.id) autoEditNodeId else null,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onImportImage = onImportImage,
                        onExecuteJs = onExecuteJs,
                        onAutoEditHandled = { autoOpenBlockId = null; autoEditNodeId = null }
                    )
                    is HtmlBlock.Figure -> FigureEditor(
                        appViewModel = appViewModel,
                        rhythms = rhythms,
                        block = block,
                        blocks = blocks,
                        autoEditNodeId = if (autoOpenBlockId == block.id) autoEditNodeId else null,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onImportImage = onImportImage,
                        onExecuteJs = onExecuteJs,
                        onAutoEditHandled = { autoOpenBlockId = null; autoEditNodeId = null }
                    )
                    is HtmlBlock.Container -> ContainerEditor(
                        appViewModel = appViewModel,
                        rhythms = rhythms,
                        block = block,
                        blocks = blocks,
                        autoEditNodeId = if (autoOpenBlockId == block.id) autoEditNodeId else null,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onImportImage = onImportImage,
                        onExecuteJs = onExecuteJs,
                        onAutoEditHandled = { autoOpenBlockId = null; autoEditNodeId = null }
                    )
                    is HtmlBlock.Divider -> DividerEditor()
                    is HtmlBlock.Raw -> RawEditor(
                        appViewModel = appViewModel,
                        rhythms = rhythms,
                        block = block,
                        blocks = blocks,
                        autoEditNodeId = if (autoOpenBlockId == block.id) autoEditNodeId else null,
                        onUpdate = { onUpdateBlock(block.id, it) },
                        onImportImage = onImportImage,
                        onExecuteJs = onExecuteJs,
                        onAutoEditHandled = { autoOpenBlockId = null; autoEditNodeId = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddBar(onAddBlock: (HtmlBlock) -> Unit) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AddButton("Header", Icons.Default.Title) { onAddBlock(HtmlBlock.Header(level = 1, text = "")) }
        AddButton("Text", Icons.Default.Notes) { onAddBlock(HtmlBlock.Paragraph(html = "")) }
        AddButton("Math", Icons.Default.Functions) { onAddBlock(HtmlBlock.KaTeX(expression = "", displayMode = true)) }
        AddButton("Image", Icons.Default.Image) { onAddBlock(HtmlBlock.Image(src = "", alt = "")) }
        AddButton("ECG", Icons.Default.Waves) { onAddBlock(HtmlBlock.Ecg(pathology = "", lead = null, caption = "")) }
        AddButton("ECG segment", Icons.Default.Timeline) { onAddBlock(HtmlBlock.EcgSegment(pathology = "", lead = "II", caption = "")) }
        AddButton("Table", Icons.Default.TableChart) { onAddBlock(HtmlBlock.Table(rows = listOf(listOf("", ""), listOf("", "")))) }
        AddButton("List", Icons.Default.List) { onAddBlock(HtmlBlock.HtmlList(items = "", numbered = false)) }
        AddButton("Quote", Icons.Default.FormatQuote) { onAddBlock(HtmlBlock.Quote(html = "")) }
        AddButton("Note", Icons.Default.Info) { onAddBlock(HtmlBlock.Note(variant = "info", html = "")) }
        AddButton("Card", Icons.Default.SmartButton) { onAddBlock(HtmlBlock.Card(title = "", html = "")) }
        AddButton("Section", Icons.Default.ViewHeadline) { onAddBlock(HtmlBlock.Section(title = "", html = "")) }
        AddButton("Figure", Icons.Default.Portrait) { onAddBlock(HtmlBlock.Figure(html = "", caption = "")) }
        AddButton("Container", Icons.Default.TableRows) { onAddBlock(HtmlBlock.Container(html = "")) }
        AddButton("Divider", Icons.Default.HorizontalRule) { onAddBlock(HtmlBlock.Divider()) }
        AddButton("Raw", Icons.Default.Code) { onAddBlock(HtmlBlock.Raw(html = "<div></div>")) }
    }
}

@Composable
private fun AddButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
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

private fun bodyHtmlOf(block: HtmlBlock): String? = when (block) {
    is HtmlBlock.Note -> block.html
    is HtmlBlock.Card -> block.html
    is HtmlBlock.Section -> block.html
    is HtmlBlock.Figure -> block.html
    is HtmlBlock.Container -> block.html
    is HtmlBlock.Raw -> block.html
    else -> null
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
            textStyle = TextStyle(fontFamily = FontFamily.Monospace)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EcgEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Ecg,
    autoOpen: Boolean = false,
    onUpdate: (HtmlBlock.Ecg) -> Unit,
    onOpened: () -> Unit = {}
) {
    var showSelector by remember { mutableStateOf(false) }
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()

    LaunchedEffect(autoOpen) {
        if (autoOpen) {
            showSelector = true
            onOpened()
        }
    }

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
private fun EcgSegmentEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.EcgSegment,
    autoOpen: Boolean = false,
    onUpdate: (HtmlBlock.EcgSegment) -> Unit,
    onOpened: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(autoOpen) {
        if (autoOpen) {
            showDialog = true
            onOpened()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("ECG Segment", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit segment")
                }
            }

            val rhythm = rhythms.find { it.id == block.pathology }
            val label = rhythm?.let { "${it.nameRu ?: it.titleEn} (${it.id})" }
                ?: block.pathology.takeIf { it.isNotBlank() } ?: "(none)"

            Text("Rhythm: $label", style = MaterialTheme.typography.bodySmall)
            Text(
                "Lead: ${block.lead} | Start: ${block.startSec}s | Duration: ${block.durationSec}s",
                style = MaterialTheme.typography.bodySmall
            )
            if (!block.caption.isNullOrBlank()) {
                Text("Caption: ${block.caption}", style = MaterialTheme.typography.bodySmall)
            }
            if (block.tips.isNotEmpty()) {
                Text("Tips: ${block.tips.size}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showDialog) {
        EcgSegmentDialog(
            appViewModel = appViewModel,
            rhythms = rhythms,
            initialPathologyId = block.pathology,
            initialLead = Lead.fromToken(block.lead) ?: Lead.II,
            initialStart = block.startSec,
            initialDuration = block.durationSec,
            initialCaption = block.caption ?: "",
            initialTips = block.tips,
            onConfirm = { updated ->
                onUpdate(
                    block.copy(
                        pathology = updated.pathology,
                        lead = updated.lead,
                        startSec = updated.startSec,
                        durationSec = updated.durationSec,
                        caption = updated.caption,
                        tips = updated.tips
                    )
                )
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun DividerEditor() {
    Column {
        Text("Divider", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun HtmlListEditor(block: HtmlBlock.HtmlList, onUpdate: (HtmlBlock.HtmlList) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("List", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("Numbered", style = MaterialTheme.typography.labelSmall)
            Checkbox(checked = block.numbered, onCheckedChange = { onUpdate(block.copy(numbered = it)) })
        }
        OutlinedTextField(
            value = block.items,
            onValueChange = { onUpdate(block.copy(items = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter items (one per line)...") },
            minLines = 3
        )
    }
}

@Composable
private fun QuoteEditor(block: HtmlBlock.Quote, onUpdate: (HtmlBlock.Quote) -> Unit) {
    Column {
        Text("Quote", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = block.html,
            onValueChange = { onUpdate(block.copy(html = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter quote text or HTML...") },
            minLines = 2
        )
    }
}

@Composable
private fun NoteEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Note,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onUpdate: (HtmlBlock.Note) -> Unit,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)?,
    onAutoEditHandled: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(block.variant.uppercase())
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("info", "tip", "warning", "important").forEach { v ->
                        DropdownMenuItem(text = { Text(v.uppercase()) }, onClick = { onUpdate(block.copy(variant = v)); expanded = false })
                    }
                }
            }
        }
        StructureEditor(
            appViewModel = appViewModel,
            rhythms = rhythms,
            blockId = block.id,
            html = block.html,
            onUpdateHtml = { onUpdate(block.copy(html = it)) },
            blocks = blocks,
            autoEditNodeId = autoEditNodeId,
            onImportImage = onImportImage,
            onExecuteJs = onExecuteJs,
            onAutoEditHandled = onAutoEditHandled
        )
    }
}

@Composable
private fun CardEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Card,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onUpdate: (HtmlBlock.Card) -> Unit,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)?,
    onAutoEditHandled: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Card", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        TextField(
            value = block.title,
            onValueChange = { onUpdate(block.copy(title = it)) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        StructureEditor(
            appViewModel = appViewModel,
            rhythms = rhythms,
            blockId = block.id,
            html = block.html,
            onUpdateHtml = { onUpdate(block.copy(html = it)) },
            blocks = blocks,
            autoEditNodeId = autoEditNodeId,
            onImportImage = onImportImage,
            onExecuteJs = onExecuteJs,
            onAutoEditHandled = onAutoEditHandled
        )
    }
}

@Composable
private fun SectionEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Section,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onUpdate: (HtmlBlock.Section) -> Unit,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)?,
    onAutoEditHandled: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Section", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        TextField(
            value = block.title,
            onValueChange = { onUpdate(block.copy(title = it)) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        StructureEditor(
            appViewModel = appViewModel,
            rhythms = rhythms,
            blockId = block.id,
            html = block.html,
            onUpdateHtml = { onUpdate(block.copy(html = it)) },
            blocks = blocks,
            autoEditNodeId = autoEditNodeId,
            onImportImage = onImportImage,
            onExecuteJs = onExecuteJs,
            onAutoEditHandled = onAutoEditHandled
        )
    }
}

@Composable
private fun FigureEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Figure,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onUpdate: (HtmlBlock.Figure) -> Unit,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)?,
    onAutoEditHandled: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Figure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        StructureEditor(
            appViewModel = appViewModel,
            rhythms = rhythms,
            blockId = block.id,
            html = block.html,
            onUpdateHtml = { onUpdate(block.copy(html = it)) },
            blocks = blocks,
            autoEditNodeId = autoEditNodeId,
            onImportImage = onImportImage,
            onExecuteJs = onExecuteJs,
            onAutoEditHandled = onAutoEditHandled
        )
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
private fun ContainerEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Container,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onUpdate: (HtmlBlock.Container) -> Unit,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)?,
    onAutoEditHandled: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Container", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        StructureEditor(
            appViewModel = appViewModel,
            rhythms = rhythms,
            blockId = block.id,
            html = block.html,
            onUpdateHtml = { onUpdate(block.copy(html = it)) },
            blocks = blocks,
            autoEditNodeId = autoEditNodeId,
            onImportImage = onImportImage,
            onExecuteJs = onExecuteJs,
            onAutoEditHandled = onAutoEditHandled
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

@Composable
private fun RawEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    block: HtmlBlock.Raw,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onUpdate: (HtmlBlock.Raw) -> Unit,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)? = null,
    onAutoEditHandled: () -> Unit = {}
) {
    StructureEditor(
        appViewModel = appViewModel,
        rhythms = rhythms,
        blockId = block.id,
        html = block.html,
        onUpdateHtml = { onUpdate(block.copy(html = it)) },
        blocks = blocks,
        autoEditNodeId = autoEditNodeId,
        onImportImage = onImportImage,
        onExecuteJs = onExecuteJs,
        onAutoEditHandled = onAutoEditHandled
    )
}

@Composable
private fun StructureEditor(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    blockId: String,
    html: String,
    onUpdateHtml: (String) -> Unit,
    blocks: List<HtmlBlock>,
    autoEditNodeId: String? = null,
    onImportImage: (String, ByteArray) -> String?,
    onExecuteJs: ((String) -> Unit)? = null,
    onAutoEditHandled: () -> Unit = {}
) {
    val outline = remember(html) { HtmlStructure.outline(html) }
    var selectedPath by remember { mutableStateOf<List<Int>?>(null) }
    var expandedPaths by remember { mutableStateOf(setOf<List<Int>>()) }
    var showRawHtml by remember { mutableStateOf(false) }

    var menuTarget by remember { mutableStateOf<HtmlStructure.Node?>(null) }
    var showReplaceConfirm by remember { mutableStateOf<HtmlStructure.Node?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<HtmlStructure.Node?>(null) }
    var pendingAction by remember { mutableStateOf<Triple<HtmlStructure.Node, InsertionPlacement, ComponentKind>?>(null) }
    var editTarget by remember { mutableStateOf<HtmlStructure.Node?>(null) }

    LaunchedEffect(autoEditNodeId) {
        if (autoEditNodeId != null) {
            val node = HtmlStructure.nodeById(html, autoEditNodeId)
            if (node != null) {
                editTarget = node
                selectedPath = node.path
                // Ensure parents are expanded
                val newExpanded = expandedPaths.toMutableSet()
                for (i in 1..node.path.size) {
                    newExpanded.add(node.path.take(i))
                }
                expandedPaths = newExpanded
            }
            onAutoEditHandled()
        }
    }

    var showRootInsertMenu by remember { mutableStateOf(false) }

    fun scrollToNode(node: HtmlStructure.Node) {
        val blockIndex = blocks.indexOfFirst { it.id == blockId }
        if (blockIndex == -1) return

        val isFull = HtmlCompiler.isFullDocument(html)
        val path = if (isFull) {
            node.path
        } else {
            val block = blocks[blockIndex]
            if (block is HtmlBlock.Raw) {
                listOf(blockIndex + node.path[0]) + node.path.drop(1)
            } else {
                listOf(blockIndex) + node.path
            }
        }
        val indicesJson = path.joinToString(",", prefix = "[", postfix = "]")
        val js = """
            (function(){
                var el = document.body;
                if (!el) return;
                var idx = $indicesJson;
                for (var k = 0; k < idx.length; k++) {
                    if (!el.children || idx[k] >= el.children.length) {
                        el = null;
                        break;
                    }
                    el = el.children[idx[k]];
                }
                if (el) el.scrollIntoView({behavior: 'smooth', block: 'center'});
            })();
        """.trimIndent()
        onExecuteJs?.invoke(js)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nested Structure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            
            Box {
                TextButton(
                    onClick = { showRootInsertMenu = true },
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Insert", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = showRootInsertMenu, onDismissRequest = { showRootInsertMenu = false }) {
                    ComponentKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.name) },
                            onClick = {
                                pendingAction = Triple(HtmlStructure.Node("", null, null, HtmlStructure.Kind.Other, "", null, emptyList(), emptyList()), InsertionPlacement.Inside, kind)
                                showRootInsertMenu = false
                            }
                        )
                    }
                }
            }

            TextButton(
                onClick = { showRawHtml = !showRawHtml },
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(if (showRawHtml) Icons.Default.CodeOff else Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showRawHtml) "Hide HTML" else "Edit HTML", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (showRawHtml) {
            OutlinedTextField(
                value = html,
                onValueChange = onUpdateHtml,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                if (outline.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No elements. Use '＋ Insert' to add structure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    NodeTree(
                        nodes = outline,
                        selectedPath = selectedPath,
                        expandedPaths = expandedPaths,
                        onSelect = { node ->
                            selectedPath = node.path
                            scrollToNode(node)
                        },
                        onToggleExpand = { path ->
                            expandedPaths = if (expandedPaths.contains(path)) expandedPaths - setOf(path) else expandedPaths + setOf(path)
                        },
                        onLongClick = { node -> menuTarget = node }
                    )
                }
            }
        }
    }

    if (menuTarget != null) {
        val node = menuTarget!!
        DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
            DropdownMenuItem(
                text = { Text("Edit...") },
                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                onClick = { editTarget = node; menuTarget = null }
            )
            HorizontalDivider()
            if (node.kind == HtmlStructure.Kind.Container) {
                ComponentPickerMenu("Insert inside", node, InsertionPlacement.Inside) { placement, kind ->
                    pendingAction = Triple(node, placement, kind)
                    menuTarget = null
                }
            }
            ComponentPickerMenu("Insert before", node, InsertionPlacement.Before) { placement, kind ->
                pendingAction = Triple(node, placement, kind)
                menuTarget = null
            }
            ComponentPickerMenu("Insert after", node, InsertionPlacement.After) { placement, kind ->
                pendingAction = Triple(node, placement, kind)
                menuTarget = null
            }
            HorizontalDivider()
            ComponentPickerMenu("Replace with", node, InsertionPlacement.Replace) { placement, kind ->
                if (node.kind == HtmlStructure.Kind.Container) {
                    showReplaceConfirm = node
                    pendingAction = Triple(node, placement, kind)
                } else {
                    pendingAction = Triple(node, placement, kind)
                }
                menuTarget = null
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                onClick = {
                    if (node.kind == HtmlStructure.Kind.Container) showDeleteConfirm = node
                    else onUpdateHtml(HtmlStructure.removeElement(html, node.path))
                    menuTarget = null
                }
            )
        }
    }

    if (showReplaceConfirm != null) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = null; pendingAction = null },
            title = { Text("Replace Container?") },
            text = { Text("Replacing a container will discard all nested elements inside it. Continue?") },
            confirmButton = {
                Button(onClick = { showReplaceConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { showReplaceConfirm = null; pendingAction = null }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm != null) {
        val node = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Container?") },
            text = { Text("Deleting this container will also delete all elements inside it. Continue?") },
            confirmButton = {
                Button(onClick = { onUpdateHtml(HtmlStructure.removeElement(html, node.path)); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } }
        )
    }

    if (pendingAction != null && showReplaceConfirm == null) {
        val (node, placement, kind) = pendingAction!!
        val onConfirm = { newBlock: HtmlBlock ->
            val markup = HtmlCompiler.compile(listOf(newBlock))
            val newHtml = when {
                node.path.isEmpty() -> HtmlStructure.appendToRoot(html, markup)
                placement == InsertionPlacement.Inside -> HtmlStructure.appendChild(html, node.path, markup)
                placement == InsertionPlacement.Before -> HtmlStructure.insertAdjacent(html, node.path, markup, false)
                placement == InsertionPlacement.After -> HtmlStructure.insertAdjacent(html, node.path, markup, true)
                placement == InsertionPlacement.Replace -> HtmlStructure.replaceElement(html, node.path, markup)
                else -> html
            }
            if (newHtml != html) onUpdateHtml(newHtml)
            pendingAction = null
        }
        val onDismiss = { pendingAction = null }
        ComponentKindDialog(kind, appViewModel, rhythms, onImportImage, onConfirm, onDismiss)
    }

    if (editTarget != null) {
        val node = editTarget!!
        val outerHtml = HtmlStructure.getOuterHtml(html, node.path)
        val initialBlock = HtmlCompiler.parse(outerHtml).firstOrNull()

        if (initialBlock is HtmlBlock.Ecg) {
             EcgDialog(
                 appViewModel = appViewModel,
                 rhythms = rhythms,
                 initialPathologyId = initialBlock.pathology,
                 initialLead = initialBlock.leads.firstOrNull()?.let { Lead.fromToken(it) },
                 onConfirm = { newEcg ->
                     val markup = HtmlCompiler.buildEcgTag(newEcg)
                     onUpdateHtml(HtmlStructure.replaceElement(html, node.path, markup))
                     editTarget = null
                 },
                 onDismiss = { editTarget = null }
             )
        } else {
            var currentHtml by remember { mutableStateOf(outerHtml) }
            AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { Text("Edit Element HTML") },
                text = { OutlinedTextField(value = currentHtml, onValueChange = { currentHtml = it }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), minLines = 5) },
                confirmButton = { Button(onClick = { onUpdateHtml(HtmlStructure.replaceElement(html, node.path, currentHtml)); editTarget = null }) { Text("Apply") } },
                dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ComponentKindDialog(
    kind: ComponentKind,
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    onImportImage: (String, ByteArray) -> String?,
    onConfirm: (HtmlBlock) -> Unit,
    onDismiss: () -> Unit
) {
    when (kind) {
        ComponentKind.Header -> HeaderDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Text -> TextDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Math -> MathDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Image -> ImageDialog(onImportImage = onImportImage, onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Table -> TableDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Ecg -> EcgDialog(appViewModel = appViewModel, rhythms = rhythms, onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.EcgSegment -> EcgSegmentDialog(
            appViewModel = appViewModel,
            rhythms = rhythms,
            initialPathologyId = "",
            initialLead = Lead.II,
            initialStart = 0f,
            initialDuration = 2.5f,
            initialCaption = "",
            initialTips = emptyList(),
            onConfirm = { onConfirm(it) },
            onDismiss = onDismiss
        )
        ComponentKind.List -> ListDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Quote -> QuoteDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Note -> NoteDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Card -> CardDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Section -> SectionDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Figure -> FigureDialog(onConfirm = { onConfirm(it) }, onDismiss = onDismiss)
        ComponentKind.Divider -> SideEffect { onConfirm(HtmlBlock.Divider()) }
    }
}

@Composable
private fun NodeTree(
    nodes: List<HtmlStructure.Node>,
    selectedPath: List<Int>?,
    expandedPaths: Set<List<Int>>,
    onSelect: (HtmlStructure.Node) -> Unit,
    onToggleExpand: (List<Int>) -> Unit,
    onLongClick: (HtmlStructure.Node) -> Unit,
    depth: Int = 0
) {
    for (node in nodes) {
        val isSelected = node.path == selectedPath
        val isExpanded = expandedPaths.contains(node.path) || depth < 2
        
        NodeRow(
            node = node,
            isSelected = isSelected,
            isExpanded = isExpanded,
            onSelect = { onSelect(node) },
            onToggleExpand = { onToggleExpand(node.path) },
            onLongClick = { onLongClick(node) },
            depth = depth
        )
        
        if (isExpanded && node.children.isNotEmpty()) {
            NodeTree(
                nodes = node.children,
                selectedPath = selectedPath,
                expandedPaths = expandedPaths,
                onSelect = onSelect,
                onToggleExpand = onToggleExpand,
                onLongClick = onLongClick,
                depth = depth + 1
            )
        }
    }
}

@Composable
private fun NodeRow(
    node: HtmlStructure.Node,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onLongClick: () -> Unit,
    depth: Int
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isHovered -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
                onLongClick = onLongClick
            )
            .padding(start = (depth * 16 + 4).dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty()) {
            Icon(
                if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                null,
                modifier = Modifier
                    .size(20.dp)
                    .combinedClickable(onClick = onToggleExpand)
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
        
        Spacer(Modifier.width(4.dp))
        
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = classifyColor(node.kind)
        ) {}
        
        Spacer(Modifier.width(8.dp))
        
        Text(
            text = node.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        
        if (node.preview != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "· ${node.preview}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun classifyColor(kind: HtmlStructure.Kind): Color = when (kind) {
    HtmlStructure.Kind.Heading -> Color(0xFF2196F3)
    HtmlStructure.Kind.Text, HtmlStructure.Kind.Math -> Color(0xFF4CAF50)
    HtmlStructure.Kind.Image -> Color(0xFF9C27B0)
    HtmlStructure.Kind.Ecg -> Color(0xFFF44336)
    HtmlStructure.Kind.Table -> Color(0xFF795548)
    HtmlStructure.Kind.Diagram -> Color(0xFFFF9800)
    HtmlStructure.Kind.Container -> Color.Gray
    else -> Color.LightGray
}

@Composable
private fun ComponentPickerMenu(
    label: String,
    node: HtmlStructure.Node,
    placement: InsertionPlacement,
    onSelect: (InsertionPlacement, ComponentKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
            }
        },
        onClick = { expanded = true }
    )
    
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ComponentKind.entries.forEach { kind ->
            DropdownMenuItem(
                text = { Text(kind.name) },
                onClick = { onSelect(placement, kind); expanded = false }
            )
        }
    }
}

private enum class InsertionPlacement { Inside, Before, After, Replace }
private enum class ComponentKind { Header, Text, Math, Image, Ecg, EcgSegment, Table, List, Quote, Note, Card, Section, Figure, Divider }

@Composable
private fun ListDialog(onConfirm: (HtmlBlock.HtmlList) -> Unit, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf("") }
    var numbered by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert List") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Numbered")
                    Checkbox(checked = numbered, onCheckedChange = { numbered = it })
                }
                OutlinedTextField(value = items, onValueChange = { items = it }, label = { Text("Items (one per line)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.HtmlList(items = items, numbered = numbered)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun QuoteDialog(onConfirm: (HtmlBlock.Quote) -> Unit, onDismiss: () -> Unit) {
    var html by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Quote") },
        text = { OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("Quote Content (HTML)") }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Quote(html = html)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NoteDialog(onConfirm: (HtmlBlock.Note) -> Unit, onDismiss: () -> Unit) {
    var html by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("info") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Variant: ${variant.uppercase()}")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("info", "tip", "warning", "important").forEach { v ->
                            DropdownMenuItem(text = { Text(v.uppercase()) }, onClick = { variant = v; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("Note Content (HTML)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Note(variant = variant, html = html)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CardDialog(onConfirm: (HtmlBlock.Card) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var html by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("Body (HTML)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Card(title = title, html = html)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SectionDialog(onConfirm: (HtmlBlock.Section) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var html by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("Content (HTML)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Section(title = title, html = html)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FigureDialog(onConfirm: (HtmlBlock.Figure) -> Unit, onDismiss: () -> Unit) {
    var html by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Figure") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("Content (HTML)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Figure(html = html, caption = caption)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HeaderDialog(onConfirm: (HtmlBlock.Header) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Header") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Level: ")
                    (1..6).forEach { l ->
                        FilterChip(selected = level == l, onClick = { level = l }, label = { Text("H$l") }, modifier = Modifier.padding(horizontal = 2.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Header(level = level, text = text)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TextDialog(onConfirm: (HtmlBlock.Paragraph) -> Unit, onDismiss: () -> Unit) {
    var html by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Text") },
        text = { OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("HTML Content") }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Paragraph(html = html)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MathDialog(onConfirm: (HtmlBlock.KaTeX) -> Unit, onDismiss: () -> Unit) {
    var expression by remember { mutableStateOf("") }
    var displayMode by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Math") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = expression, onValueChange = { expression = it }, label = { Text("KaTeX Expression") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Display Mode")
                    Checkbox(checked = displayMode, onCheckedChange = { displayMode = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.KaTeX(expression = expression, displayMode = displayMode)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ImageDialog(onImportImage: (String, ByteArray) -> String?, onConfirm: (HtmlBlock.Image) -> Unit, onDismiss: () -> Unit) {
    var src by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                val fileName = getFileName(context, it) ?: "image_${System.currentTimeMillis()}.png"
                val newPath = onImportImage(fileName, bytes)
                if (newPath != null) src = newPath
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = src, onValueChange = { src = it }, label = { Text("Src") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { launcher.launch("image/*") }) { Icon(Icons.Default.FileUpload, null) }
                }
                OutlinedTextField(value = alt, onValueChange = { alt = it }, label = { Text("Alt Text") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Image(src = src, alt = alt)) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TableDialog(onConfirm: (HtmlBlock.Table) -> Unit, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf(2) }
    var cols by remember { mutableStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Table") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = rows.toString(), onValueChange = { rows = it.toIntOrNull() ?: 1 }, label = { Text("Rows") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = cols.toString(), onValueChange = { cols = it.toIntOrNull() ?: 1 }, label = { Text("Cols") }, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(HtmlBlock.Table(rows = List(rows) { List(cols) { "" } })) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EcgDialog(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    initialPathologyId: String = "",
    initialLead: Lead? = null,
    onConfirm: (HtmlBlock.Ecg) -> Unit,
    onDismiss: () -> Unit
) {
    ComparisonTargetDialog(
        appViewModel = appViewModel,
        rhythms = rhythms,
        onDismiss = onDismiss,
        onTargetSelected = { target ->
            onConfirm(HtmlBlock.Ecg(pathology = target.pathologyId, leads = listOf(target.lead.name), caption = ""))
        },
        initialPathologyId = initialPathologyId,
        initialLead = initialLead
    )
}

@Composable
private fun EcgSegmentDialog(
    appViewModel: AppViewModel,
    rhythms: List<PathologyEntry>,
    initialPathologyId: String,
    initialLead: Lead,
    initialStart: Float,
    initialDuration: Float,
    initialCaption: String,
    initialTips: List<TipOverlay>,
    onConfirm: (HtmlBlock.EcgSegment) -> Unit,
    onDismiss: () -> Unit
) {
    var pathologyId by remember { mutableStateOf(initialPathologyId) }
    var lead by remember { mutableStateOf(initialLead) }
    var start by remember { mutableStateOf(initialStart) }
    var duration by remember { mutableStateOf(initialDuration) }
    var caption by remember { mutableStateOf(initialCaption) }
    var tips by remember { mutableStateOf(initialTips) }
    var tool by remember { mutableStateOf(SegmentTool.Range) }
    var showRhythmPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ECG Segment") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = { showRhythmPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val rhythm = rhythms.find { it.id == pathologyId }
                    val label = rhythm?.let { "${it.nameRu ?: it.titleEn} (${it.id})" } ?: "Pick Rhythm"
                    Text("$label | Lead: ${lead.name}")
                }

                Spacer(Modifier.height(8.dp))

                SegmentToolRow(tool, onToolSelected = { tool = it })

                Spacer(Modifier.height(8.dp))

                SegmentRangeCanvas(
                    appViewModel = appViewModel,
                    pathologyId = pathologyId,
                    lead = lead,
                    startSec = start,
                    durationSec = duration,
                    tips = tips,
                    tool = tool,
                    onRangeChange = { s, d -> start = s; duration = d },
                    onTipsChange = { tips = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    HtmlBlock.EcgSegment(
                        pathology = pathologyId,
                        lead = lead.name,
                        startSec = start,
                        durationSec = duration,
                        caption = caption.takeIf { it.isNotBlank() },
                        tips = tips
                    )
                )
            }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showRhythmPicker) {
        ComparisonTargetDialog(
            appViewModel = appViewModel,
            rhythms = rhythms,
            onDismiss = { showRhythmPicker = false },
            onTargetSelected = { target ->
                pathologyId = target.pathologyId
                lead = target.lead
                showRhythmPicker = false
            },
            initialPathologyId = pathologyId,
            initialLead = lead
        )
    }
}

private enum class SegmentTool { Range, VerticalLine, HorizontalLine, Text, Point, Delete }

@Composable
private fun SegmentToolRow(selected: SegmentTool, onToolSelected: (SegmentTool) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SegmentToolButton("Range", Icons.Default.OpenInFull, selected == SegmentTool.Range) { onToolSelected(SegmentTool.Range) }
        SegmentToolButton("V-Line", Icons.Default.VerticalDistribute, selected == SegmentTool.VerticalLine) { onToolSelected(SegmentTool.VerticalLine) }
        SegmentToolButton("H-Line", Icons.Default.HorizontalDistribute, selected == SegmentTool.HorizontalLine) { onToolSelected(SegmentTool.HorizontalLine) }
        SegmentToolButton("Point", Icons.Default.FiberManualRecord, selected == SegmentTool.Point) { onToolSelected(SegmentTool.Point) }
        SegmentToolButton("Del", Icons.Default.Delete, selected == SegmentTool.Delete) { onToolSelected(SegmentTool.Delete) }
    }
}

@Composable
private fun SegmentToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(
            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            RoundedCornerShape(4.dp)
        )
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SegmentRangeCanvas(
    appViewModel: AppViewModel,
    pathologyId: String,
    lead: Lead,
    startSec: Float,
    durationSec: Float,
    tips: List<TipOverlay>,
    tool: SegmentTool,
    onRangeChange: (Float, Float) -> Unit,
    onTipsChange: (List<TipOverlay>) -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshTrigger by appViewModel.refreshTrigger.collectAsState()
    val points = remember(pathologyId, lead, refreshTrigger) {
        appViewModel.repository?.leadWaveform(pathologyId, lead)
    }

    if (points == null || points.values.isEmpty()) {
        Box(modifier.background(Color.Black.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text("Waveform unavailable")
        }
        return
    }

    val cal = remember { EcgCalibration() }
    val samples = points.values
    val totalDuration = samples.size / cal.sampleRateHz

    Canvas(
        modifier = modifier
            .pointerInput(pathologyId, lead, tool, tips) {
                detectTapGestures { offset ->
                    val sample = (offset.x / size.width) * samples.size
                    val adc = (size.height / 2f - offset.y) / (size.height / 4000f)

                    when (tool) {
                        SegmentTool.VerticalLine -> {
                            val newTip = TipOverlay(TipOverlayKind.VerticalLines, listOf(TipPoint(sample, 0f)), lead = lead)
                            onTipsChange(tips + newTip)
                        }
                        SegmentTool.HorizontalLine -> {
                            val newTip = TipOverlay(TipOverlayKind.HorizontalLines, listOf(TipPoint(0f, (size.height / 2f - offset.y) / (size.height / 4000f))), lead = lead)
                            onTipsChange(tips + newTip)
                        }
                        SegmentTool.Point -> {
                            val newTip = TipOverlay(TipOverlayKind.Points, listOf(TipPoint(sample, (size.height / 2f - offset.y) / (size.height / 4000f))), lead = lead)
                            onTipsChange(tips + newTip)
                        }
                        SegmentTool.Delete -> {
                            val thresholdSq = 400f
                            val toDelete = tips.minByOrNull { t ->
                                t.points.minOfOrNull { p ->
                                    val tx = (p.sample / samples.size) * size.width
                                    val ty = size.height / 2f - p.adc * (size.height / 4000f)
                                    (tx - offset.x) * (tx - offset.x) + (ty - offset.y) * (ty - offset.y)
                                } ?: Float.MAX_VALUE
                            }
                            if (toDelete != null) {
                                val minDistSq = toDelete.points.minOfOrNull { p ->
                                    val tx = (p.sample / samples.size) * size.width
                                    val ty = size.height / 2f - p.adc * (size.height / 4000f)
                                    (tx - offset.x) * (tx - offset.x) + (ty - offset.y) * (ty - offset.y)
                                } ?: Float.MAX_VALUE
                                if (minDistSq < thresholdSq) onTipsChange(tips - toDelete)
                            }
                        }
                        else -> {}
                    }
                }
            }
            .pointerInput(pathologyId, lead, tool, startSec) {
                detectDragGestures { change, dragAmount ->
                    if (tool == SegmentTool.Range) {
                        val dx = (dragAmount.x / size.width) * totalDuration
                        val newStart = (startSec + dx).coerceIn(0f, max(0f, totalDuration - durationSec))
                        onRangeChange(newStart, durationSec)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val baselineY = height / 2f
        val scaleX = width / samples.size
        val scaleY = height / 4000f

        // Draw grid
        val stepX = 0.2f * cal.sampleRateHz * scaleX
        for (i in 0..(samples.size / (0.2f * cal.sampleRateHz)).toInt()) {
            val x = i * stepX
            drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
        }

        // Draw trace
        val path = Path()
        if (samples.isNotEmpty()) {
            path.moveTo(0f, baselineY - samples[0] * scaleY)
            for (i in 1 until samples.size) {
                path.lineTo(i * scaleX, baselineY - samples[i] * scaleY)
            }
        }
        drawPath(path, Color.Black, style = Stroke(width = 1.dp.toPx()))

        // Draw selection range
        val rangeStartX = (startSec / totalDuration) * width
        val rangeWidthX = (durationSec / totalDuration) * width
        drawRect(
            color = Color.Blue.copy(alpha = 0.1f),
            topLeft = Offset(rangeStartX, 0f),
            size = Size(rangeWidthX, height)
        )
        drawRect(
            color = Color.Blue,
            topLeft = Offset(rangeStartX, 0f),
            size = Size(rangeWidthX, height),
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw tips
        for (tip in tips) {
            val color = Color.Red
            for (pt in tip.points) {
                val x = (pt.sample / samples.size) * width
                val y = baselineY - pt.adc * scaleY

                when (tip.kind) {
                    TipOverlayKind.VerticalLines -> drawLine(color, Offset(x, 0f), Offset(x, height), strokeWidth = 2f)
                    TipOverlayKind.HorizontalLines -> drawLine(color, Offset(0f, y), Offset(width, y), strokeWidth = 2f)
                    TipOverlayKind.Points -> drawCircle(color, 6f, Offset(x, y))
                    else -> {}
                }
            }
        }
    }
}
