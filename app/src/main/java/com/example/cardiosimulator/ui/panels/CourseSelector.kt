package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.screens.verticalScrollbar
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun CourseSelector(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    courses: List<CourseEntry> = emptyList(),
    selectedCourseId: String? = null,
    onCourseSelect: (CourseEntry) -> Unit = {},
) {
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScrollbar(listState),
            ) {
                itemsIndexed(courses, key = { _, c -> c.id }) { index, course ->
                    val isSelected = course.id == selectedCourseId
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCourseSelect(course) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        val title = if (course.id == AppViewModel.ALL_RHYTHMS_ID) {
                            stringResource(R.string.rhythm_course_filter_all)
                        } else {
                            if (currentLanguage == Language.RU)
                                course.nameRu ?: course.titleEn
                            else
                                course.titleEn
                        }
                        
                        Text(
                            text = title,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        
                        if (course.lecturesCount > 0 || course.id != AppViewModel.ALL_RHYTHMS_ID) {
                            Text(
                                text = stringResource(R.string.course_details_lectures_format, course.lecturesCount),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    
                    if (index < courses.lastIndex) {
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
