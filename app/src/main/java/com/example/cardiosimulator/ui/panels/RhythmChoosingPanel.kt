package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.PathologyGroup
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.screens.verticalScrollbar
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun RhythmChoosingPanel(
    modifier: Modifier = Modifier,
    rhythms: List<PathologyGroup> = emptyList(),
    selectedPathology: String? = null,
    currentLanguage: Language = Language.EN,
    onRhythmSelect: (PathologyGroup) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val rhythmsListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            //horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
                placeholder = { Text(stringResource(R.string.rhythm_search_placeholder), color = Color.Black) },
                leadingIcon = {
                    Icon(
                        tint = Color.Black,
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.rhythm_search_content_description)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyColumn(
                state = rhythmsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScrollbar(rhythmsListState)
            ) {
                val filtered = rhythms.filter {
                    val title = if (currentLanguage == Language.RU) it.fileName else it.displayTitle
                    title.contains(searchQuery, ignoreCase = true)
                }
                items(filtered, key = { it.pathology }) { rhythm ->
                    val isSelected = rhythm.pathology == selectedPathology
                    val title = if (currentLanguage == Language.RU) rhythm.fileName else rhythm.displayTitle
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRhythmSelect(rhythm) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        text = title,
                        color = if (isSelected) Color.Red else Color.Black,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RhythmChoosingPanelPreview() {
    CardioSimulatorTheme {
        Surface {
            RhythmChoosingPanel()
        }
    }
}
