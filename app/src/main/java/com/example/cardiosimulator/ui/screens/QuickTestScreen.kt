package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.QuestionBankRepository
import com.example.cardiosimulator.data.TestRepository
import com.example.cardiosimulator.domain.QuestionDifficulty
import com.example.cardiosimulator.domain.QuestionStimulus
import com.example.cardiosimulator.domain.Test
import com.example.cardiosimulator.domain.TestQuestion
import com.example.cardiosimulator.domain.generators.TestGenType
import java.util.Random

data class QuickTestContext(
    val section: String,
    val subtopic: String,
    val theme: String? = null
)

@Composable
fun QuickTestScreen(
    context: QuickTestContext,
    testRepository: TestRepository,
    bankRepository: QuestionBankRepository,
    onBack: () -> Unit,
    onStart: (Test) -> Unit
) {
    var selectedMode by remember { mutableStateOf(QuickTestMode.Ready) }
    
    // Generator state
    var selectedTypes by remember { mutableStateOf(TestGenType.values().toSet()) }
    var questionCount by remember { mutableStateOf(10) }
    var testMinutes by remember { mutableStateOf(10) }
    var targetDifficulty by remember { mutableStateOf<QuestionDifficulty?>(null) } // null = mixed

    // Ready test filter
    var readyFilterByTheme by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                QuickTestHeader(context, onBack)

                Row(modifier = Modifier.weight(1f)) {
                    // Left sidebar or Main content depending on layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        // Action choice cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ActionCard(
                                title = stringResource(R.string.quick_action_ready),
                                description = stringResource(R.string.quick_action_ready_desc),
                                isSelected = selectedMode == QuickTestMode.Ready,
                                onClick = { selectedMode = QuickTestMode.Ready },
                                modifier = Modifier.weight(1f)
                            )
                            ActionCard(
                                title = stringResource(R.string.quick_action_generate),
                                description = stringResource(R.string.quick_action_generate_desc),
                                isSelected = selectedMode == QuickTestMode.Generate,
                                onClick = { selectedMode = QuickTestMode.Generate },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (selectedMode == QuickTestMode.Ready) {
                                ReadyTestsSection(
                                    testRepository = testRepository,
                                    context = context,
                                    filterByTheme = readyFilterByTheme,
                                    onFilterChange = { readyFilterByTheme = it },
                                    onStart = onStart
                                )
                            } else {
                                GeneratorSection(
                                    selectedTypes = selectedTypes,
                                    onTypesChange = { selectedTypes = it },
                                    questionCount = questionCount,
                                    onCountChange = { questionCount = it },
                                    testMinutes = testMinutes,
                                    onMinutesChange = { testMinutes = it },
                                    targetDifficulty = targetDifficulty,
                                    onDifficultyChange = { targetDifficulty = it }
                                )
                            }
                        }

                        // Footer
                        val quickTestTitle = stringResource(R.string.quick_title)
                        QuickTestFooter(
                            selectedMode = selectedMode,
                            context = context,
                            onStartGenerated = {
                                val test = generateQuickTest(
                                    bank = bankRepository.questions(),
                                    context = context,
                                    types = selectedTypes,
                                    count = questionCount,
                                    minutes = testMinutes,
                                    difficulty = targetDifficulty,
                                    defaultTitle = quickTestTitle
                                )
                                onStart(test)
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class QuickTestMode { Ready, Generate }

@Composable
private fun QuickTestHeader(context: QuickTestContext, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quick_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.quick_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.quick_back_to_lecture))
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_progress_label).uppercase(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = context.section,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = context.subtopic,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadyTestsSection(
    testRepository: TestRepository,
    context: QuickTestContext,
    filterByTheme: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onStart: (Test) -> Unit
) {
    val allTests = remember { testRepository.tests() }
    val filteredTests = remember(allTests, filterByTheme, context.theme) {
        if (filterByTheme && context.theme != null) {
            allTests.filter { test ->
                test.questions.any { it.theme.equals(context.theme, ignoreCase = true) }
            }
        } else {
            allTests
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.quick_ready_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (context.theme != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !filterByTheme,
                        onClick = { onFilterChange(false) },
                        label = { Text(stringResource(R.string.quick_filter_all)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = filterByTheme,
                        onClick = { onFilterChange(true) },
                        label = { Text(stringResource(R.string.quick_filter_bytheme)) },
                        leadingIcon = if (filterByTheme) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredTests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.quick_ready_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.quick_ready_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTests) { test ->
                    ReadyTestItem(
                        test = test,
                        isByTheme = context.theme != null && test.questions.any { it.theme.equals(context.theme, ignoreCase = true) },
                        onClick = { onStart(test) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyTestItem(test: Test, isByTheme: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = test.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val duration = if (test.questionTimeSeconds > 0) {
                    (test.questions.size * test.questionTimeSeconds / 60).coerceAtLeast(1)
                } else null
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (duration != null) 
                            stringResource(R.string.test_gen_ready_meta_format, test.questions.size, duration)
                        else 
                            stringResource(R.string.test_gen_ready_untimed_format, test.questions.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isByTheme) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.quick_badge_bytheme),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GeneratorSection(
    selectedTypes: Set<TestGenType>,
    onTypesChange: (Set<TestGenType>) -> Unit,
    questionCount: Int,
    onCountChange: (Int) -> Unit,
    testMinutes: Int,
    onMinutesChange: (Int) -> Unit,
    targetDifficulty: QuestionDifficulty?,
    onDifficultyChange: (QuestionDifficulty?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.quick_gen_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Types
        Text(stringResource(R.string.quick_gen_pick_types), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        val allTypes = TestGenType.values()
        FlowRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
            // Mixed
            FilterChip(
                selected = selectedTypes.size == allTypes.size,
                onClick = { 
                    if (selectedTypes.size == allTypes.size) onTypesChange(emptySet())
                    else onTypesChange(allTypes.toSet())
                },
                label = { Text(stringResource(R.string.quick_type_mixed)) }
            )
            
            allTypes.forEach { type ->
                FilterChip(
                    selected = selectedTypes.contains(type) && selectedTypes.size != allTypes.size,
                    onClick = {
                        val new = if (selectedTypes.contains(type)) selectedTypes - type else selectedTypes + type
                        onTypesChange(new)
                    },
                    label = { Text(stringResource(getTypeStringRes(type))) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Count
        ParameterRow(
            label = stringResource(R.string.quick_count),
            value = "$questionCount ${stringResource(R.string.quick_count_suffix)}",
            hint = stringResource(R.string.quick_count_hint),
            onDecrease = { onCountChange((questionCount - 5).coerceAtLeast(5)) },
            onIncrease = { onCountChange((questionCount + 5).coerceAtMost(30)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Time
        ParameterRow(
            label = stringResource(R.string.quick_time),
            value = "$testMinutes ${stringResource(R.string.quick_time_suffix)}",
            hint = stringResource(R.string.quick_time_hint),
            onDecrease = { onMinutesChange((testMinutes - 5).coerceAtLeast(5)) },
            onIncrease = { onMinutesChange((testMinutes + 5).coerceAtMost(45)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty
        Text(stringResource(R.string.quick_difficulty), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.quick_difficulty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = targetDifficulty == null,
                onClick = { onDifficultyChange(null) },
                label = { Text(stringResource(R.string.quick_diff_mixed)) }
            )
            QuestionDifficulty.values().forEach { diff ->
                FilterChip(
                    selected = targetDifficulty == diff,
                    onClick = { onDifficultyChange(diff) },
                    label = { Text(stringResource(getDiffStringRes(diff))) }
                )
            }
        }
    }
}

@Composable
private fun ParameterRow(
    label: String,
    value: String,
    hint: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDecrease, modifier = Modifier.size(40.dp), shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("-")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 80.dp), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = onIncrease, modifier = Modifier.size(40.dp), shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("+")
            }
        }
    }
}

@Composable
private fun QuickTestFooter(
    selectedMode: QuickTestMode,
    context: QuickTestContext,
    onStartGenerated: () -> Unit
) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.quick_footer_format, context.subtopic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            if (selectedMode == QuickTestMode.Generate) {
                Button(
                    onClick = onStartGenerated,
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.quick_start))
                }
            }
        }
    }
}

private fun getTypeStringRes(type: TestGenType): Int = when (type) {
    TestGenType.Questions -> R.string.test_gen_type_questions
    TestGenType.Image -> R.string.test_gen_type_image
    TestGenType.Detect -> R.string.test_gen_type_detect
    TestGenType.Assemble -> R.string.test_gen_type_assemble
    TestGenType.Clinical -> R.string.test_gen_type_clinical
}

private fun getDiffStringRes(diff: QuestionDifficulty): Int = when (diff) {
    QuestionDifficulty.Easy -> R.string.diff_easy
    QuestionDifficulty.Medium -> R.string.diff_medium
    QuestionDifficulty.Hard -> R.string.diff_hard
}

@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + spacingPx
        }
        rows.add(currentRow)
        
        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * spacingPx
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + spacingPx
                }
                y += rowHeight + spacingPx
            }
        }
    }
}

private fun generateQuickTest(
    bank: List<TestQuestion>,
    context: QuickTestContext,
    types: Set<TestGenType>,
    count: Int,
    minutes: Int,
    difficulty: QuestionDifficulty?,
    defaultTitle: String
): Test {
    val rng = Random()
    
    // 1. Filter by types
    val typeFiltered = bank.filter { q ->
        types.any { type ->
            when (type) {
                TestGenType.Questions -> !q.isAssembly && (q.stimulus == QuestionStimulus.Text || q.stimulus == QuestionStimulus.Ecg)
                TestGenType.Image -> q.stimulus == QuestionStimulus.Image
                TestGenType.Detect -> q.stimulus == QuestionStimulus.Ecg && !q.isAssembly
                TestGenType.Assemble -> q.isAssembly
                TestGenType.Clinical -> q.stimulus == QuestionStimulus.Text && !q.isAssembly
            }
        }
    }

    // 2. Filter by context theme (soft fallback)
    var topicFiltered = if (context.theme != null) {
        typeFiltered.filter { it.theme.equals(context.theme, ignoreCase = true) }
    } else emptyList()

    if (topicFiltered.isEmpty()) {
        topicFiltered = typeFiltered
    }

    // 3. Soft difficulty preference
    val matchingDiff = if (difficulty != null) topicFiltered.filter { it.difficulty == difficulty }.shuffled(rng) else emptyList()
    val others = if (difficulty != null) topicFiltered.filter { it.difficulty != difficulty }.shuffled(rng) else topicFiltered.shuffled(rng)
    
    val selected = (matchingDiff + others).take(count)
    
    val questions = selected.mapIndexed { index, q ->
        q.copy(
            number = index + 1,
            id = "quick_gen_q_${rng.nextInt().let { if (it < 0) -it else it }}"
        )
    }
    
    val questionTimeSeconds = if (minutes > 0 && questions.isNotEmpty()) {
        (minutes * 60) / questions.size
    } else 0
    
    val testId = "quick_" + Integer.toHexString(rng.nextInt())
    val title = "$defaultTitle: ${context.subtopic}"
    
    return Test(testId, title, questions, questionTimeSeconds)
}
