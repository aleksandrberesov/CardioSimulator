package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.QuestionStimulus
import com.example.cardiosimulator.domain.TestQuestion
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import java.io.File

@Composable
fun TestQuestionPanel(
    question: TestQuestion,
    totalQuestions: Int,
    remainingSeconds: Int,
    revealed: Boolean,
    selectedOptionId: String?,
    onOptionSelect: (String) -> Unit,
    onNext: () -> Unit,
    onAbort: () -> Unit,
    isTimed: Boolean
) {
    var showAbortConfirm by remember { mutableStateOf(false) }

    if (showAbortConfirm) {
        AlertDialog(
            onDismissRequest = { showAbortConfirm = false },
            title = { Text(stringResource(R.string.test_abort)) },
            text = { Text(stringResource(R.string.test_abort_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showAbortConfirm = false
                    onAbort()
                }) {
                    Text(stringResource(R.string.test_abort), color = Negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbortConfirm = false }) {
                    Text(stringResource(R.string.cd_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header: N of M + Time
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.test_counter_format, question.number, totalQuestions),
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (isTimed) {
                Text(
                    text = formatTime(remainingSeconds),
                    color = Negative,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Title
        Text(
            text = stringResource(R.string.test_question_title_format, question.number),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question Text
        Text(
            text = question.text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Options
        question.options.forEachIndexed { index, option ->
            val isSelected = selectedOptionId == option.id
            val isCorrect = option.id == question.correctOptionId
            
            val backgroundColor = when {
                !revealed -> Color.Transparent
                isSelected && isCorrect -> AccentGreenTint
                isSelected && !isCorrect -> Negative.copy(alpha = 0.12f)
                isCorrect -> AccentGreenTint
                else -> Color.Transparent
            }
            
            val borderColor = when {
                !revealed -> if (isSelected) AccentGreen else ControlBorder
                isSelected && isCorrect -> Positive
                isSelected && !isCorrect -> Negative
                isCorrect -> Positive
                else -> ControlBorder
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(backgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable(enabled = !revealed) { onOptionSelect(option.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${option.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        !revealed -> TextPrimary
                        isCorrect -> Positive
                        isSelected && !isCorrect -> Negative
                        else -> TextSecondary
                    }
                )
            }
        }

        if (revealed) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Comment Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ControlFill, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.test_comment_title),
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.test_correct_answer_format, question.correctOptionNumber()),
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.comment,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Footer Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showAbortConfirm = true }) {
                Text(stringResource(R.string.test_abort), color = Negative)
            }

            if (revealed) {
                val isCorrectSelection = selectedOptionId == question.correctOptionId
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCorrectSelection) Positive else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCorrectSelection) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (question.number < totalQuestions) stringResource(R.string.test_next) else stringResource(R.string.test_finish))
                    }
                }
            }
        }
    }
}

@Composable
fun ExamQuestionPanel(
    question: TestQuestion,
    totalQuestions: Int,
    remainingSeconds: Int,
    selectedOptionId: String?,
    onOptionSelect: (String) -> Unit,
    onNext: () -> Unit,
    isTimed: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.test_counter_format, question.number, totalQuestions),
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (isTimed) {
                Text(
                    text = formatTime(remainingSeconds),
                    color = Negative,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Title
        Text(
            text = stringResource(R.string.test_question_title_format, question.number),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question Text
        Text(
            text = question.text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Options
        question.options.forEachIndexed { index, option ->
            val isSelected = selectedOptionId == option.id
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) AccentGreen else ControlBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOptionSelect(option.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${option.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) TextPrimary else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedOptionId != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen
            )
        ) {
            Text(if (question.number < totalQuestions) stringResource(R.string.test_next) else stringResource(R.string.test_finish))
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
