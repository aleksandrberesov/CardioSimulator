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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    isTimed: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header: N из M + Time
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${question.number} из $totalQuestions",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (isTimed) {
                Text(
                    text = formatTime(remainingSeconds),
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Title
        Text(
            text = "${question.number} вопрос",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color(0xFF1976D2), // Blue accent
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question Text
        Text(
            text = question.text,
            color = Color(0xFF1976D2),
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
                isSelected && isCorrect -> Color(0xFFE8F5E9) // Light Green
                isSelected && !isCorrect -> Color(0xFFFFEBEE) // Light Red
                isCorrect -> Color(0xFFE8F5E9) // Light Green
                else -> Color.Transparent
            }
            
            val borderColor = when {
                !revealed -> if (isSelected) Color.Blue else Color.Gray
                isSelected && isCorrect -> Color.Green
                isSelected && !isCorrect -> Color.Red
                isCorrect -> Color.Green
                else -> Color.Gray
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
                    color = if (revealed && isCorrect) Color(0xFF2E7D32) else if (revealed && isSelected && !isCorrect) Color(0xFFC62828) else Color.Black
                )
            }
        }

        if (revealed) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Comment Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.test_comment_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.test_correct_answer_format, question.correctOptionNumber()),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = question.comment)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Next Button
            val isCorrectSelection = selectedOptionId == question.correctOptionId
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCorrectSelection) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isCorrectSelection) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (question.number < totalQuestions) stringResource(R.string.test_next) else stringResource(R.string.test_finish))
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
                text = "${question.number} из $totalQuestions",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (isTimed) {
                Text(
                    text = formatTime(remainingSeconds),
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Title
        Text(
            text = "${question.number} вопрос",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color(0xFF1976D2),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question Text
        Text(
            text = question.text,
            color = Color(0xFF1976D2),
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
                    .border(1.dp, if (isSelected) Color.Blue else Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { onOptionSelect(option.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${option.text}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedOptionId != null
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
