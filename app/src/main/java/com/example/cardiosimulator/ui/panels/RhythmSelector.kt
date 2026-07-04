package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RhythmSelector(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    rhythms: List<PathologyEntry> = emptyList(),
    selectedId: String? = null,
    onRhythmSelect: (PathologyEntry) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    val isDrawerFixed by appViewModel.isDrawerFixed.collectAsState()
    val isGrouped by appViewModel.isRhythmListGrouped.collectAsState()
    val isClinicalMode by appViewModel.isClinicalMode.collectAsState()
    val collapsedGroups by appViewModel.collapsedRhythmGroups.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
                val title = if (isClinicalMode) {
                    entry.getClinicalTitle() ?: (if (currentLanguage == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn)
                } else {
                    if (currentLanguage == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn
                }
                title.contains(searchQuery, ignoreCase = true)
            }
    }

    // Grouping logic
    val groupedItems = remember(filtered, isGrouped, currentLanguage, groups, isClinicalMode) {
        if (!isGrouped || groups == null) {
            mapOf("" to filtered.sortedBy { 
                if (isClinicalMode) {
                    it.getClinicalTitle() ?: (if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn)
                } else {
                    if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
                }
            })
        } else {
            val map = filtered.groupBy { it.group ?: PathologyGroups.OTHER_KEY }
            val orderedKeys = groups.getOrderedKeys() + PathologyGroups.OTHER_KEY
            orderedKeys.associateWith { key ->
                map[key]?.sortedBy { 
                    if (isClinicalMode) {
                        it.getClinicalTitle() ?: (if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn)
                    } else {
                        if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn 
                    }
                }
            }.filterValues { it != null }.mapValues { it.value!! }
        }
    }

    LaunchedEffect(selectedId) {
        if (selectedId != null) {
            // Optional: scroll to selected
        }
    }

    LaunchedEffect(isClinicalMode, filtered) {
        if (isClinicalMode && filtered.isNotEmpty() && (selectedId == null || filtered.none { it.id == selectedId })) {
            onRhythmSelect(filtered.first())
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val resourceResolver: (String) -> String? = remember(context) {
        { name ->
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            if (id != 0) context.getString(id) else null
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
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            // Grouping / Sorting toggle
            IconButton(
                onClick = { appViewModel.setRhythmListGrouped(!isGrouped) },
                enabled = !isClinicalMode
            ) {
                Icon(
                    imageVector = if (isGrouped) Icons.Default.ViewList else Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = if (isClinicalMode) TextSecondary else AccentGreen
                )
            }

            // Clinical mode toggle
            IconButton(
                onClick = { appViewModel.setClinicalMode(!isClinicalMode) }
            ) {
                Icon(
                    imageVector = if (isClinicalMode) Icons.Default.Healing else Icons.Outlined.Healing,
                    contentDescription = stringResource(R.string.clinical_mode_tooltip),
                    tint = if (isClinicalMode) Color.Red else TextSecondary
                )
            }

            // Fix drawer toggle
            IconButton(
                onClick = { appViewModel.setDrawerFixed(!isDrawerFixed) }
            ) {
                Icon(
                    imageVector = if (isDrawerFixed) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(R.string.fix_drawer),
                    tint = if (isDrawerFixed) AccentGreen else TextSecondary
                )
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

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState
        ) {
            groupedItems.forEach { (groupKey, items) ->
                if (groupKey.isNotEmpty() && isGrouped) {
                    val isCollapsed = collapsedGroups.contains(groupKey)

                    stickyHeader {
                        val groupName = if (groupKey == PathologyGroups.OTHER_KEY) {
                            stringResource(R.string.group_other)
                        } else if (isClinicalMode && groupKey == "clinical") {
                            stringResource(R.string.group_clinical)
                        } else {
                            groups?.displayName(groupKey, currentLanguage.tag, resourceResolver) ?: groupKey
                        }
                        RhythmGroupHeader(
                            name = groupName,
                            count = items.size,
                            isCollapsed = isCollapsed,
                            onClick = { appViewModel.toggleRhythmGroupCollapsed(groupKey) }
                        )
                    }

                    if (!isCollapsed) {
                        items(items, key = { it.id }) { rhythm ->
                            RhythmItem(
                                rhythm = rhythm,
                                isSelected = rhythm.id == selectedId,
                                currentLanguage = currentLanguage,
                                isClinicalMode = isClinicalMode,
                                onClick = { onRhythmSelect(rhythm) }
                            )
                        }
                    }
                } else {
                    items(items, key = { it.id }) { rhythm ->
                        RhythmItem(
                            rhythm = rhythm,
                            isSelected = rhythm.id == selectedId,
                            currentLanguage = currentLanguage,
                            isClinicalMode = isClinicalMode,
                            onClick = { onRhythmSelect(rhythm) }
                        )
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
        color = AccentGreenTint
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AccentGreen
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextSecondary
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
    onClick: () -> Unit
) {
    val title = if (isClinicalMode) {
        rhythm.getClinicalTitle() ?: (if (currentLanguage == Language.RU) rhythm.nameRu ?: rhythm.titleEn else rhythm.titleEn)
    } else {
        if (currentLanguage == Language.RU)
            rhythm.nameRu ?: rhythm.titleEn
        else
            rhythm.titleEn
    }

    val display = if (rhythm.number != null) "${rhythm.number} $title" else title
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp) // Tighter rows
    ) {
        Text(
            text = display,
            color = if (isSelected) Color.Red else TextPrimary,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), // Smaller font
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        thickness = 0.5.dp,
        color = Hairline,
    )
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
