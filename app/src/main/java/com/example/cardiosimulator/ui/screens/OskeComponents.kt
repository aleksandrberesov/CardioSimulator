package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.OskeAnswerKind
import com.example.cardiosimulator.domain.OskeQuestion

@Composable
fun OskeQuestionBlock(
    question: OskeQuestion,
    selectedIds: List<String>,
    onOptionToggle: (String) -> Unit,
    isReadOnly: Boolean = false,
    correctIds: List<String>? = null
) {
    Column {
        Text(
            text = "${question.number}. ${question.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        question.options.forEach { option ->
            val isSelected = selectedIds.contains(option.id)
            val isCorrect = correctIds?.contains(option.id)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { if (!isReadOnly) onOptionToggle(option.id) },
                        enabled = !isReadOnly
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (question.kind == OskeAnswerKind.Single) {
                    RadioButton(selected = isSelected, onClick = null)
                } else {
                    Checkbox(checked = isSelected, onCheckedChange = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = option.text, style = MaterialTheme.typography.bodyMedium)

                if (isReadOnly && isCorrect != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    if (isCorrect) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green)
                    } else if (isSelected) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                    }
                }
            }
        }
    }
}
