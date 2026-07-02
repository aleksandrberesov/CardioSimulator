package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.domain.TopicEntry
import com.example.cardiosimulator.ui.screens.verticalScrollbar

/**
 * Lecture index for the chosen course. Mirrors [CourseSelector]'s look;
 * selection drives `CourseViewerViewModel.selectLecture`. Localised title
 * follows the active [language] (RU name with English fallback).
 *
 * Lectures are grouped by [topics]. Ungrouped lectures appear first.
 */
@Composable
fun LectureSelector(
    lectures: List<LectureEntry>,
    language: Language,
    modifier: Modifier = Modifier,
    selectedLectureId: String? = null,
    topics: List<TopicEntry> = emptyList(),
    onLectureSelect: (LectureEntry) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.lecture_selector_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 1. Ungrouped / Orphans first
            val topicIds = topics.map { it.id }.toSet()
            val ungrouped = lectures.filter { it.topic == null || it.topic !in topicIds }

            ungrouped.forEachIndexed { index, lecture ->
                LectureItem(lecture, language, selectedLectureId == lecture.id, isIndented = false) {
                    onLectureSelect(lecture)
                }
                if (index < ungrouped.lastIndex || topics.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            // 2. Topics in order
            topics.forEachIndexed { topicIndex, topic ->
                val topicTitle = if (language == Language.RU) topic.nameRu ?: topic.titleEn else topic.titleEn
                Text(
                    text = topicTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                val topicLectures = lectures.filter { it.topic == topic.id }
                topicLectures.forEachIndexed { lectureIndex, lecture ->
                    LectureItem(lecture, language, selectedLectureId == lecture.id, isIndented = true) {
                        onLectureSelect(lecture)
                    }
                    if (lectureIndex < topicLectures.lastIndex || topicIndex < topics.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureItem(
    lecture: LectureEntry,
    language: Language,
    isSelected: Boolean,
    isIndented: Boolean,
    onClick: () -> Unit
) {
    val title = if (language == Language.RU) lecture.nameRu ?: lecture.titleEn else lecture.titleEn
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .padding(vertical = 12.dp, horizontal = if (isIndented) 16.dp else 4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black,
        style = MaterialTheme.typography.bodyLarge,
    )
}
