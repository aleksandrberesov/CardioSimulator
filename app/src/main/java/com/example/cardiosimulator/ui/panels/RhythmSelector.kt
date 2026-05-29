package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.ui.screens.verticalScrollbar
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun RhythmSelector(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    rhythms: List<PathologyEntry> = emptyList(),
    /**
     * Course index (each [CourseEntry] carries its
     * [CourseEntry.pathologies]). When non-empty, a course filter is shown
     * that limits the list to the selected course's pathologies. Empty =
     * no filter affordance, full list — preserves prior behavior.
     */
    courses: List<CourseEntry> = emptyList(),
    selectedId: String? = null,
    onRhythmSelect: (PathologyEntry) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Course scoping: when a course is picked, the list is limited to that
    // course's pathology ids (the course → pathologies map carried on each
    // CourseEntry). null = no course selected = show every rhythm.
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(courses) {
        if (selectedCourseId != null && courses.none { it.id == selectedCourseId }) {
            selectedCourseId = null
        }
    }
    val coursePathologyIds: Set<String>? = remember(courses, selectedCourseId) {
        selectedCourseId?.let { id ->
            courses.firstOrNull { it.id == id }?.pathologies?.toSet() ?: emptySet()
        }
    }

    val filtered = remember(rhythms, searchQuery, currentLanguage, coursePathologyIds) {
        rhythms.filter { entry ->
            if (coursePathologyIds != null && entry.id !in coursePathologyIds) return@filter false
            val title = if (currentLanguage == Language.RU) entry.nameRu ?: entry.titleEn else entry.titleEn
            title.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(selectedId, rhythms) {
        if (selectedId != null) {
            val index = filtered.indexOfFirst { it.id == selectedId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.rhythm_selector_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        if (courses.isNotEmpty()) {
            CourseFilter(
                courses = courses,
                selectedCourseId = selectedCourseId,
                language = currentLanguage,
                onSelect = { selectedCourseId = it },
            )
        }
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

/**
 * Dropdown that scopes the rhythm list to a single course (or "All").
 * Driven by the course → pathologies map carried on each [CourseEntry].
 */
@Composable
private fun CourseFilter(
    courses: List<CourseEntry>,
    selectedCourseId: String?,
    language: Language,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filterCd = stringResource(R.string.rhythm_course_filter_cd)
    val selectedLabel = selectedCourseId
        ?.let { id -> courses.firstOrNull { it.id == id } }
        ?.let { courseDisplayName(it, language) }
        ?: stringResource(R.string.rhythm_course_filter_all)

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = filterCd },
        ) {
            Text(
                text = selectedLabel,
                color = Color.Black,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rhythm_course_filter_all)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            courses.forEach { course ->
                DropdownMenuItem(
                    text = { Text(courseDisplayName(course, language)) },
                    onClick = {
                        onSelect(course.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun courseDisplayName(course: CourseEntry, language: Language): String =
    if (language == Language.RU) course.nameRu ?: course.titleEn else course.titleEn

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
