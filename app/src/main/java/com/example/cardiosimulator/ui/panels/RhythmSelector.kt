package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
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
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
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
    val collapsedGroups by appViewModel.collapsedRhythmGroups.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val groups = appViewModel.repository?.groups

    val filtered = remember(rhythms, searchQuery, currentLanguage) {
        rhythms.filter { entry ->
            val title = if (currentLanguage == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn
            title.contains(searchQuery, ignoreCase = true)
        }
    }

    // Grouping logic
    val groupedItems = remember(filtered, isGrouped, currentLanguage, groups) {
        if (!isGrouped || groups == null) {
            mapOf("" to filtered.sortedBy { 
                if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn 
            })
        } else {
            val map = filtered.groupBy { it.group ?: PathologyGroups.OTHER_KEY }
            val orderedKeys = groups.getOrderedKeys() + PathologyGroups.OTHER_KEY
            orderedKeys.associateWith { key ->
                map[key]?.sortedBy { if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn }
            }.filterValues { it != null }.mapValues { it.value!! }
        }
    }

    LaunchedEffect(selectedId) {
        if (selectedId != null) {
            // Optional: scroll to selected
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.rhythm_selector_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = { appViewModel.setRhythmListGrouped(!isGrouped) }) {
                Icon(
                    imageVector = if (isGrouped) Icons.AutoMirrored.Filled.Sort else Icons.Default.ViewList,
                    contentDescription = "Toggle Grouping",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(onClick = { appViewModel.setDrawerFixed(!isDrawerFixed) }) {
                Icon(
                    imageVector = if (isDrawerFixed) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Toggle Pin",
                    tint = if (isDrawerFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearchQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            placeholder = {
                Text(stringResource(R.string.rhythm_search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.rhythm_search_content_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState
        ) {
            groupedItems.forEach { (groupKey, items) ->
                if (isGrouped && groupKey.isNotEmpty()) {
                    val isCollapsed = collapsedGroups.contains(groupKey)

                    stickyHeader(key = groupKey) {
                        val groupName = if (groups != null) {
                            val nameFromTxt = groups.displayName(groupKey, currentLanguage.tag) { null }
                            if (nameFromTxt != groupKey) {
                                nameFromTxt
                            } else {
                                val resId = when (groupKey) {
                                    "sinus" -> R.string.group_sinus
                                    "arrhythmia" -> R.string.group_arrhythmia
                                    "conduction" -> R.string.group_conduction
                                    "hypertrophy" -> R.string.group_hypertrophy
                                    "ischemia" -> R.string.group_ischemia
                                    "infarction" -> R.string.group_infarction
                                    "electrolyte" -> R.string.group_electrolyte
                                    "syndromes" -> R.string.group_syndromes
                                    "pacemaker" -> R.string.group_pacemaker
                                    "special" -> R.string.group_special
                                    "pediatric" -> R.string.group_pediatric
                                    "newborn" -> R.string.group_newborn
                                    "pregnant" -> R.string.group_pregnant
                                    "clinical" -> R.string.group_clinical
                                    PathologyGroups.OTHER_KEY -> R.string.group_other
                                    else -> null
                                }
                                if (resId != null) stringResource(resId) else groupKey
                            }
                        } else groupKey

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
                            onClick = { onRhythmSelect(rhythm) }
                        )
                    }
                }
            }
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
        color = Color(0xFFD3DEEF) // Reference: Windows band #D3DEEF
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
                tint = Color(0xFF1C3D6B) // Reference: Windows chevron #1C3D6B
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF12294B) // Reference: Windows text #12294B
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF5A6B82) // Reference: Windows count #5A6B82
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
    onClick: () -> Unit
) {
    val title = if (currentLanguage == Language.RU)
        rhythm.nameRu ?: rhythm.titleEn
    else
        rhythm.titleEn
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp) // Tighter rows
    ) {
        Text(
            text = title,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), // Smaller font
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Preview(showBackground = true, widthDp = 500, heightDp = 600)
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
