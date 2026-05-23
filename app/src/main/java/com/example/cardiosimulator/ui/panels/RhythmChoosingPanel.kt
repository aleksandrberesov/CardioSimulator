package com.example.cardiosimulator.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.ui.screens.verticalScrollbar
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun RhythmChoosingDrawer(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    rhythms: List<PathologyEntry> = emptyList(),
    selectedId: String? = null,
    onRhythmSelect: (PathologyEntry) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp
            ) {
                RhythmChoosingPanel(
                    appViewModel = appViewModel,
                    rhythms = rhythms,
                    selectedId = selectedId,
                    onRhythmSelect = onRhythmSelect,
                    onSearchQueryChange = onSearchQueryChange
                )
            }
        }

        // Handler
        Surface(
            modifier = Modifier
                .width(24.dp)
                .height(64.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Hide rhythms" else "Show rhythms",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun RhythmChoosingPanel(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    rhythms: List<PathologyEntry> = emptyList(),
    selectedId: String? = null,
    onRhythmSelect: (PathologyEntry) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
                placeholder = {
                    Text(stringResource(R.string.rhythm_search_placeholder), color = Color.Black)
                },
                leadingIcon = {
                    Icon(
                        tint = Color.Black,
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.rhythm_search_content_description),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScrollbar(listState),
            ) {
                val filtered = rhythms.filter {
                    val title = if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
                    title.contains(searchQuery, ignoreCase = true)
                }
                itemsIndexed(filtered, key = { _, r -> r.id }) { index, rhythm ->
                    val isSelected = rhythm.id == selectedId
                    val title = if (currentLanguage == Language.RU)
                        rhythm.nameRu ?: rhythm.titleEn
                    else
                        rhythm.titleEn
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRhythmSelect(rhythm) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        text = title,
                        color = if (isSelected) Color.Red else Color.Black,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (index < filtered.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RhythmChoosingPanelPreview() {
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
        Surface { RhythmChoosingPanel(appViewModel = previewAppViewModel) }
    }
}

@Preview(showBackground = true, widthDp = 500, heightDp = 600)
@Composable
fun RhythmChoosingDrawerPreview() {
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
            RhythmChoosingDrawer(appViewModel = previewAppViewModel)
        }
    }
}
