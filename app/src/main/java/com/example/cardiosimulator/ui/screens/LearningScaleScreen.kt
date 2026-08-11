package com.example.cardiosimulator.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.viewmodels.*

@Composable
fun LearningScaleScreen(viewModel: LearningScaleViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Toast.makeText(context, "Welcome back, Specialist!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { Footer(state) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Header(state)
            Spacer(modifier = Modifier.height(32.dp))
            
            GlobalProgressSection(state.globalProgress)
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "Карта разделов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionsMap(state.sections)
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Адаптивный план",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AdaptivePlan(
                        tasks = state.tasks,
                        onMarkSolved = { taskId ->
                            val newProgress = viewModel.markDone(taskId)
                            if (newProgress != null) {
                                Toast.makeText(context, "Topic mastered! Section progress: $newProgress%", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    DifficultyControl()
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Аналитика прогресса",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            ProgressHistogram(state.sections)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(state: LearningScaleState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "CARDIO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                text = "SIMULATOR",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.offset(y = (-4).dp)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(level = 4)
            Spacer(modifier = Modifier.width(16.dp))
            UserChip(name = "Dr. Nikolay")
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatItem("Точность", "${state.accuracy}%", state.accuracyChange, LsGreen)
        StatDivider()
        StatItem("Место", state.rank, "Топ 5%", LsAmber)
        StatDivider()
        StatItem("Кейсов", state.cases.toString(), "+12 сегодня", LsGreen)
        StatDivider()
        StatItem("Ср. время", "${state.avgSeconds}с", "–3с", LsGreen)
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "LEVEL $level",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UserChip(name: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(end = 12.dp, start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, sub: String, subColor: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = sub, style = MaterialTheme.typography.labelSmall, color = subColor)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun GlobalProgressSection(progress: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Глобальный прогресс курса",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            color = LsGreen,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun SectionsMap(sections: List<LsSection>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { section ->
            SectionCard(section)
        }
    }
}

@Composable
private fun SectionCard(section: LsSection) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor(section.status).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = section.id.toString(),
                        fontWeight = FontWeight.Bold,
                        color = statusColor(section.status)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { section.progress / 100f },
                            modifier = Modifier
                                .width(60.dp)
                                .height(4.dp)
                                .clip(CircleShape),
                            color = statusColor(section.status),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${section.progress}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    section.subtopics.forEach { subtopic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (subtopic.progress >= 80) LsGreen
                                        else if (subtopic.progress >= 40) LsAmber
                                        else LsRed
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = subtopic.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${subtopic.progress}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptivePlan(tasks: List<PlanTask>, onMarkSolved: (String) -> Unit) {
    var selectedTask by remember { mutableStateOf<PlanTask?>(null) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tasks.forEach { task ->
            TaskCard(task, onClick = { selectedTask = task })
        }
    }
    
    selectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTask = null },
            onSolved = {
                onMarkSolved(task.id)
                selectedTask = null
            }
        )
    }
}

@Composable
private fun TaskCard(task: PlanTask, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (task.type) {
                PlanTaskType.Critical -> Icons.Default.Warning to LsRed
                PlanTaskType.Growth -> Icons.Default.ArrowForward to LsAmber
                PlanTaskType.Fix -> Icons.Default.Refresh to LsGreen
            }
            
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.subtopicName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = task.sectionName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Badge(
                containerColor = color.copy(alpha = 0.1f),
                contentColor = color
            ) {
                Text(
                    text = task.type.name.uppercase(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun TaskDetailDialog(task: PlanTask, onDismiss: () -> Unit, onSolved: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.subtopicName) },
        text = {
            Column {
                Text("Раздел: ${task.sectionName}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Текущий уровень: ${task.progress}%")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (task.type) {
                        PlanTaskType.Critical -> "Критический пробел в знаниях. Рекомендуется немедленное повторение теории и отработка на симуляторе."
                        PlanTaskType.Growth -> "Зона ближайшего развития. Закрепите материал, чтобы выйти на экспертный уровень."
                        PlanTaskType.Fix -> "Повторение — мать учения. Освежите знания по этой теме."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onSolved) {
                Text("Решить кейс")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Позже")
            }
        }
    )
}

@Composable
private fun DifficultyControl() {
    var difficulty by remember { mutableStateOf(45f) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Сложность симуляции",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Slider(
                value = difficulty,
                onValueChange = { difficulty = it },
                valueRange = 5f..95f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("5%", style = MaterialTheme.typography.labelSmall)
                Text("Текущая: ${difficulty.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("95%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProgressHistogram(sections: List<LsSection>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            sections.forEach { section ->
                val color = if (section.progress >= 80) LsGreen
                else if (section.progress >= 40) LsAmber
                else LsRed
                
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(section.progress / 100f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "S${section.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(state: LearningScaleState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Algo: Bayesian 2.1 • Last saved: ${if (state.hasInteracted) "Just now" else "2h ago"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "CardioSimulator v3.4",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            )
        }
    }
}

private fun statusColor(status: SectionStatus): Color = when (status) {
    SectionStatus.Good -> LsGreen
    SectionStatus.Warning -> LsAmber
    SectionStatus.Critical -> LsRed
}
