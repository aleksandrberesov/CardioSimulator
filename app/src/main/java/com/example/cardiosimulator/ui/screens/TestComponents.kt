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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.example.cardiosimulator.domain.AssemblyAttempt
import com.example.cardiosimulator.domain.AssemblyPaletteItem
import kotlin.math.abs
import kotlin.math.max
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
    isTimed: Boolean,
    assemblyAttempt: AssemblyAttempt? = null,
    onSubmitAssembly: () -> Unit = {}
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
        if (!question.isAssembly) {
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
        } else {
            // In assembly mode, the workspace is in the left pane, 
            // but we might want a "Check" button here if it fits the flow.
            // Or just a message.
            if (!revealed) {
                Text(
                    text = stringResource(R.string.assemble_hint),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
                if (!question.isAssembly) {
                    Text(
                        text = stringResource(R.string.test_correct_answer_format, question.correctOptionNumber()),
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    val allCorrect = assemblyAttempt?.allCorrect == true
                    Text(
                        text = if (allCorrect) stringResource(R.string.assemble_correct) else stringResource(R.string.assemble_wrong),
                        color = if (allCorrect) Positive else Negative,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
                val isCorrect = if (question.isAssembly) assemblyAttempt?.allCorrect == true else selectedOptionId == question.correctOptionId
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCorrect) Positive else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCorrect) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (question.number < totalQuestions) stringResource(R.string.test_next) else stringResource(R.string.test_finish))
                    }
                }
            } else if (question.isAssembly) {
                Button(
                    onClick = onSubmitAssembly,
                    enabled = assemblyAttempt?.isComplete == true,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text(stringResource(R.string.assemble_check))
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

@Composable
fun EcgAssemblyWorkspace(
    attempt: AssemblyAttempt,
    revealed: Boolean,
    onPlace: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPaletteKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tape
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = ControlFill)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val slotCount = attempt.spec.partCount
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasSize = this.size
                    val midY = canvasSize.height / 2
                    // Isoline
                    drawLine(
                        color = Color.Gray,
                        start = androidx.compose.ui.geometry.Offset(0f, midY),
                        end = androidx.compose.ui.geometry.Offset(canvasSize.width, midY),
                        strokeWidth = 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    // Dividers
                    for (i in 1 until slotCount) {
                        val x = i * canvasSize.width / slotCount
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, canvasSize.height),
                            strokeWidth = 1f
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    attempt.slots.forEachIndexed { index, slotState ->
                        val item = attempt.placedAt(index)
                        val isCorrect = item?.correctIndex == index
                        
                        val tint = when {
                            !revealed -> Color.Transparent
                            isCorrect -> Positive.copy(alpha = 0.1f)
                            else -> Negative.copy(alpha = 0.1f)
                        }

                        val sharedScale = calculateSharedScale(attempt.palette)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(tint)
                                .clickable(!revealed) {
                                    if (selectedPaletteKey != null) {
                                        onPlace(index, selectedPaletteKey)
                                        selectedPaletteKey = null
                                    } else if (slotState.placedKey != null) {
                                        onPlace(index, null)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (item != null) {
                                EcgPieceView(
                                    samples = item.samples,
                                    color = when {
                                        !revealed -> TextPrimary
                                        isCorrect -> Positive
                                        else -> Negative
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    sharedMaxAmp = sharedScale.first,
                                    sharedMaxLen = sharedScale.second
                                )
                            }
                            
                            if (revealed && !isCorrect) {
                                // Faintly show correct piece
                                val correctPiece = attempt.palette.find { it.correctIndex == index }
                                if (correctPiece != null) {
                                    EcgPieceView(
                                        samples = correctPiece.samples,
                                        color = Positive.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxSize(),
                                        sharedMaxAmp = sharedScale.first,
                                        sharedMaxLen = sharedScale.second
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.assemble_pieces_label),
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Palette
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center
        ) {
            val sharedScale = calculateSharedScale(attempt.palette)
            attempt.palette.forEach { paletteItem ->
                val isPlaced = attempt.slotOf(paletteItem.key) != -1
                val isSelected = selectedPaletteKey == paletteItem.key

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .width(100.dp)
                        .height(60.dp)
                        .alpha(if (isPlaced) 0.3f else 1.0f)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AccentGreen else ControlBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(ControlFill, RoundedCornerShape(4.dp))
                        .clickable(!revealed && !isPlaced) {
                            selectedPaletteKey = paletteItem.key
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EcgPieceView(
                        samples = paletteItem.samples,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxSize(),
                        sharedMaxAmp = sharedScale.first,
                        sharedMaxLen = sharedScale.second
                    )
                }
            }
        }
    }
}

@Composable
fun EcgPieceView(
    samples: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    sharedMaxAmp: Int,
    sharedMaxLen: Int
) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas

        val canvasSize = this.size
        val midY = canvasSize.height / 2
        val ampScale = if (sharedMaxAmp == 0) 1f else (canvasSize.height * 0.4f) / sharedMaxAmp
        val stepX = canvasSize.width / sharedMaxLen
        
        val path = Path()
        
        samples.forEachIndexed { index, value ->
            val x = index * stepX
            val y = midY - value * ampScale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(path, color, style = Stroke(width = 2f))
    }
}

private fun calculateSharedScale(items: List<AssemblyPaletteItem>): Pair<Int, Int> {
    var maxAmp = 0
    var maxLen = 0
    items.forEach { item ->
        item.samples.forEach { maxAmp = max(maxAmp, abs(it)) }
        maxLen = max(maxLen, item.samples.size)
    }
    return maxAmp to maxLen
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
