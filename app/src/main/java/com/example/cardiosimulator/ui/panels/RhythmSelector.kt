package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.PathologyGroups
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RhythmSelector(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    rhythms: List<PathologyEntry> = emptyList(),
    selectedId: String? = null,
    showPinButton: Boolean = true,
    onRhythmSelect: (PathologyEntry) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    showScrollButtons: Boolean = false,
) {
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()
    val isGrouped by appViewModel.isRhythmListGrouped.collectAsState()
    val isClinicalMode by appViewModel.isClinicalMode.collectAsState()
    val collapsedGroups by appViewModel.collapsedRhythmGroups.collectAsState()
    val collapsedSubgroups by appViewModel.collapsedSubgroups.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    val groups = appViewModel.repository?.groups

    val filtered = remember(rhythms, searchQuery, currentLanguage, isClinicalMode) {
        rhythms
            .filter { entry ->
                if (isClinicalMode) {
                    !entry.clinicalCase.isNullOrBlank()
                } else {
                    entry.clinicalCase.isNullOrBlank()
                }
            }
            .filter { entry ->
                val title = entry.getDisplayName(currentLanguage, isClinicalMode)
                title.contains(searchQuery, ignoreCase = true)
            }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val resourceResolver: (String) -> String? = remember(context, config) {
        { name ->
            @Suppress("DiscouragedApi", "LocalContextGetResourceValueCall", "LocalContextResourcesRead")
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            @Suppress("LocalContextGetResourceValueCall", "LocalContextResourcesRead")
            if (id != 0) context.resources.getString(id) else null
        }
    }
    
    val otherLabel = stringResource(R.string.group_other)
    val clinicalLabel = stringResource(R.string.group_clinical)

    // Grouping logic
    val listItems = remember(filtered, isGrouped, currentLanguage, groups, isClinicalMode, collapsedGroups, collapsedSubgroups, otherLabel, clinicalLabel) {
        val result = mutableListOf<RhythmListLine>()
        if (!isGrouped || groups == null) {
            filtered.sortedWith(pathologyComparator(currentLanguage, isClinicalMode))
                .forEach { result.add(RhythmListLine.RhythmItem(it, false)) }
        } else {
            val map = filtered.groupBy { it.group ?: PathologyGroups.OTHER_KEY }
            val orderedKeys = groups.getOrderedKeys() + PathologyGroups.OTHER_KEY
            orderedKeys.forEach { groupKey ->
                val items = map[groupKey] ?: return@forEach
                if (items.isEmpty()) return@forEach
                
                val isCollapsed = collapsedGroups.contains(groupKey)
                
                val groupName = if (groupKey == PathologyGroups.OTHER_KEY) {
                    otherLabel
                } else if (isClinicalMode && groupKey == "clinical") {
                    clinicalLabel
                } else {
                    groups.displayName(groupKey, currentLanguage.tag, resourceResolver) ?: groupKey
                }
                
                result.add(RhythmListLine.GroupHeader(groupKey, groupName, items.size, isCollapsed))
                
                if (!isCollapsed) {
                    // Complexity-based sorting within group
                    val sortedItems = items.sortedWith(pathologyComparator(currentLanguage, isClinicalMode))
                    
                    // Subgrouping by identical titles
                    val subGroups = sortedItems.groupBy { it.getDisplayName(currentLanguage, isClinicalMode) }
                    
                    subGroups.forEach { (title, subgroupItems) ->
                        if (subgroupItems.size > 1) {
                            val subgroupKey = "${groupKey}_$title"
                            val isSubCollapsed = collapsedSubgroups.contains(subgroupKey)
                            result.add(RhythmListLine.SubgroupHeader(subgroupKey, title, subgroupItems.size, isSubCollapsed))
                            if (!isSubCollapsed) {
                                subgroupItems.forEach { 
                                    result.add(RhythmListLine.RhythmItem(it, true)) 
                                }
                            }
                        } else {
                            subgroupItems.forEach { 
                                result.add(RhythmListLine.RhythmItem(it, false)) 
                            }
                        }
                    }
                }
            }
        }
        result
    }

    LaunchedEffect(selectedId) {
        if (selectedId != null) {
            val entry = rhythms.find { it.id == selectedId }
            if (entry != null) {
                val groupKey = entry.group ?: PathologyGroups.OTHER_KEY
                val title = entry.getDisplayName(currentLanguage, isClinicalMode)
                
                // Check if it's in a subgroup within its category
                val groupItems = rhythms.filter { (it.group ?: PathologyGroups.OTHER_KEY) == groupKey }
                val sameTitleCount = groupItems.count { 
                    it.getDisplayName(currentLanguage, isClinicalMode) == title 
                }
                val subgroupKey = if (sameTitleCount > 1) "${groupKey}_$title" else null
                
                appViewModel.expandGroupAndSubgroup(groupKey, subgroupKey)
            }
        }
    }

    LaunchedEffect(isClinicalMode, filtered) {
        if (isClinicalMode && filtered.isNotEmpty() && (selectedId == null || filtered.none { it.id == selectedId })) {
            onRhythmSelect(filtered.first())
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.rhythm_selector_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (isGrouped && !isClinicalMode) {
                IconButton(onClick = {
                    appViewModel.expandAllRhythms()
                }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowDown,
                        contentDescription = "Expand All",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = {
                    val groupKeys = listItems.filterIsInstance<RhythmListLine.GroupHeader>().map { it.key }.toSet()
                    val subgroupKeys = listItems.filterIsInstance<RhythmListLine.SubgroupHeader>().map { it.key }.toSet()
                    appViewModel.collapseAllRhythms(groupKeys, subgroupKeys)
                }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowUp,
                        contentDescription = "Collapse All",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Grouping / Sorting toggle
            IconButton(
                onClick = { appViewModel.setRhythmListGrouped(!isGrouped) },
                enabled = !isClinicalMode
            ) {
                Icon(
                    imageVector = if (isGrouped) Icons.Default.ViewList else Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = if (isClinicalMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }

            // Clinical mode toggle
            IconButton(
                onClick = { appViewModel.setClinicalMode(!isClinicalMode) }
            ) {
                Icon(
                    // Person = "patient clinical case"; matches the Windows U+E77B Contact glyph.
                    // Not a heart/pulse — that reads as the ECG/rhythm (the other mode).
                    imageVector = if (isClinicalMode) Icons.Default.Person else Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.clinical_mode_tooltip),
                    tint = if (isClinicalMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Fix drawer toggle
            if (showPinButton) {
                IconButton(
                    onClick = { appViewModel.setDrawerFixed(!isDrawerFixed) }
                ) {
                    Icon(
                        imageVector = if (isDrawerFixed) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.fix_drawer),
                        tint = if (isDrawerFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearchQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.rhythm_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp)
        )
        
        // Find selected rhythm
        val selectedRhythm = remember(rhythms, selectedId) { rhythms.find { it.id == selectedId } }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                listItems.forEach { line ->
                    when (line) {
                        is RhythmListLine.GroupHeader -> {
                            stickyHeader(key = "group_${line.key}") {
                                RhythmGroupHeader(
                                    name = line.name,
                                    count = line.count,
                                    isCollapsed = line.isCollapsed,
                                    onClick = { appViewModel.toggleRhythmGroupCollapsed(line.key) }
                                )
                            }
                        }
                        is RhythmListLine.SubgroupHeader -> {
                            item(key = "subgroup_${line.key}") {
                                RhythmSubgroupHeader(
                                    name = line.name,
                                    count = line.count,
                                    isCollapsed = line.isCollapsed,
                                    onClick = { appViewModel.toggleSubgroupCollapsed(line.key) }
                                )
                            }
                        }
                        is RhythmListLine.RhythmItem -> {
                            item(key = "item_${line.entry.id}") {
                                RhythmItem(
                                    rhythm = line.entry,
                                    isSelected = line.entry.id == selectedId,
                                    currentLanguage = currentLanguage,
                                    isClinicalMode = isClinicalMode,
                                    isIndented = line.isIndented,
                                    onClick = { onRhythmSelect(line.entry) }
                                )
                            }
                        }
                    }
                }
            }

            if (showScrollButtons) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier.padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledIconButton(
                            onClick = {
                                scrollScope.launch {
                                    val page = listState.layoutInfo.viewportSize.height * 0.9f
                                    listState.animateScrollBy(-page)
                                }
                            },
                            modifier = Modifier.size(width = 40.dp, height = 34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Scroll up",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                scrollScope.launch {
                                    val page = listState.layoutInfo.viewportSize.height * 0.9f
                                    listState.animateScrollBy(page)
                                }
                            },
                            modifier = Modifier.size(width = 40.dp, height = 34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll down",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isClinicalMode && selectedRhythm != null) {
            ClinicalDashboard(
                clinicalCase = selectedRhythm.clinicalCase,
                number = selectedRhythm.number,
                language = currentLanguage
            )
        }
    }
}

@Composable
fun RhythmGroupHeader(
    name: String,
    count: Int,
    isCollapsed: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun RhythmSubgroupHeader(
    name: String,
    count: Int,
    isCollapsed: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun RhythmItem(
    rhythm: PathologyEntry,
    isSelected: Boolean,
    currentLanguage: Language,
    isClinicalMode: Boolean,
    isIndented: Boolean,
    onClick: () -> Unit
) {
    val title = rhythm.getDisplayName(currentLanguage, isClinicalMode)

    val display = if (isIndented) {
        if (rhythm.number != null) "${rhythm.number} $title" else "$title (${rhythm.id})"
    } else {
        if (rhythm.number != null) "${rhythm.number} $title" else title
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = if (isIndented) 32.dp else 12.dp)
    ) {
        Text(
            text = display,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

sealed class RhythmListLine {
    data class GroupHeader(val key: String, val name: String, val count: Int, val isCollapsed: Boolean) : RhythmListLine()
    data class SubgroupHeader(val key: String, val name: String, val count: Int, val isCollapsed: Boolean) : RhythmListLine()
    data class RhythmItem(val entry: PathologyEntry, val isIndented: Boolean) : RhythmListLine()
}

private fun PathologyEntry.getDisplayName(language: Language, isClinicalMode: Boolean): String {
    return if (isClinicalMode) {
        getClinicalTitle() ?: (if (language == Language.RU) nameRu ?: titleEn else titleEn)
    } else {
        if (language == Language.RU) nameRu ?: titleEn else titleEn
    }
}

private fun pathologyComparator(language: Language, isClinicalMode: Boolean) = Comparator<PathologyEntry> { a, b ->
    val aFactors = a.titleEn.split('+').size
    val bFactors = b.titleEn.split('+').size
    if (aFactors != bFactors) return@Comparator aFactors.compareTo(bFactors)
    
    val aName = a.getDisplayName(language, isClinicalMode)
    val bName = b.getDisplayName(language, isClinicalMode)
    aName.compareTo(bName, ignoreCase = true)
}

@Composable
fun ClinicalDashboard(
    clinicalCase: String?,
    number: Int?,
    language: Language,
    modifier: Modifier = Modifier
) {
    if (clinicalCase.isNullOrBlank()) return

    val params = remember(clinicalCase) {
        clinicalCase.split(',').associate {
            val parts = it.split('=')
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
        }.filterKeys { it.isNotEmpty() }
    }

    val canonicalKeys = listOf("title", "description", "name", "age", "gender", "hr", "bp")
    val otherKeys = params.keys.filter { it !in canonicalKeys }.sorted()
    val allOrderedKeys = canonicalKeys.filter { it in params } + otherKeys

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.clinical_dashboard_title) + (if (number != null) " №$number" else ""),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        allOrderedKeys.forEach { key ->
            val labelRes = when (key) {
                "title" -> R.string.clinical_label_title
                "description" -> R.string.clinical_label_description
                "name" -> R.string.clinical_label_patient_name
                "age" -> R.string.clinical_label_age
                "gender" -> R.string.clinical_label_gender
                "hr" -> R.string.clinical_label_hr
                "bp" -> R.string.clinical_label_bp
                else -> null
            }
            val label = if (labelRes != null) stringResource(labelRes) else key
            var value = params[key] ?: ""

            if (key == "gender") {
                value = when (value.lowercase()) {
                    "male", "мужской", "masculino", "男", "पुरुष" -> stringResource(R.string.gender_male)
                    "female", "женский", "femenino", "女", "महिला" -> stringResource(R.string.gender_female)
                    else -> value
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$label:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun PathologyEntry.getClinicalTitle(): String? {
    if (clinicalCase.isNullOrBlank()) return null
    return clinicalCase!!.split(',').firstOrNull { it.trim().startsWith("title=") }?.substringAfter("title=")
}

@Composable
fun RhythmSelectorPreview() {
    val previewAppViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    AppBuilder().addMode(OperatingModeModel(OperatingMode.Teaching)).build(),
                ) as T
            }
        },
    )
    CardioSimulatorTheme {
        Surface {
            RhythmSelector(appViewModel = previewAppViewModel)
        }
    }
}
