package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel

private const val SNIPPET_H2 = "<h2>Heading</h2>"
private const val SNIPPET_FORMULA = "\$\$ \\frac{a}{b} \$\$"
private const val SNIPPET_IMAGE = "<img src=\"assets/image.svg\" alt=\"\">"
private const val SNIPPET_ECG = "<ecg pathology=\"lad\" lead=\"II\"></ecg>"
private val SNIPPET_TABLE = """
<table data-quiz-id="quiz1" data-editable="true">
  <tr><th>Question</th><th>Answer</th></tr>
  <tr><td>Example</td><td><input></td></tr>
</table>
""".trimIndent()

/**
 * Bottom-slot panel for the Course Constructor: insert-block helpers 
 * that append HTML snippets to the editor draft.
 */
@Composable
fun CourseConstructorControlPanel(
    courseConstructorViewModel: CourseConstructorViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab(text = "H2", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_H2) }, modifier = Modifier.weight(1f))
        Tab(text = "Σ", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_FORMULA) }, modifier = Modifier.weight(1f))
        Tab(text = "IMG", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_IMAGE) }, modifier = Modifier.weight(1f))
        Tab(text = "ECG", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_ECG) }, modifier = Modifier.weight(1f))
        Tab(text = "TBL", onClick = { courseConstructorViewModel.insertSnippet(SNIPPET_TABLE) }, modifier = Modifier.weight(1f))
    }
}
