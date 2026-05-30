package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.ui.screens.verticalScrollbar
import com.example.cardiosimulator.ui.viewmodels.AppViewModel

@Composable
fun LectureSelector(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    lectures: List<LectureEntry> = emptyList(),
    selectedLectureId: String? = null,
    onLectureSelect: (LectureEntry) -> Unit = {},
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScrollbar(listState),
        ) {
            itemsIndexed(lectures, key = { _, l -> l.id }) { index, lecture ->
                val title = if (currentLanguage == Language.RU)
                    lecture.nameRu ?: lecture.titleEn
                else
                    lecture.titleEn
                
                val isSelected = lecture.id == selectedLectureId
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLectureSelect(lecture) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                
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
