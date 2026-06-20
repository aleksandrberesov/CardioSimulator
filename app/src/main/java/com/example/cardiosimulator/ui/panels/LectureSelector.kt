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
import com.example.cardiosimulator.ui.screens.verticalScrollbar

/**
 * Lecture index for the chosen course. Mirrors [CourseSelector]'s look;
 * selection drives `CourseViewerViewModel.selectLecture`. Localised title
 * follows the active [language] (RU name with English fallback).
 */
@Composable
fun LectureSelector(
    lectures: List<LectureEntry>,
    language: Language,
    modifier: Modifier = Modifier,
    selectedLectureId: String? = null,
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
            lectures.forEachIndexed { index, lecture ->
                val title = if (language == Language.RU) lecture.nameRu ?: lecture.titleEn else lecture.titleEn
                val isSelected = lecture.id == selectedLectureId
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLectureSelect(lecture) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (index < lectures.lastIndex) {
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
