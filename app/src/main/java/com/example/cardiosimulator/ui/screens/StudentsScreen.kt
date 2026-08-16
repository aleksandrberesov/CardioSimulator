package com.example.cardiosimulator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import com.example.cardiosimulator.domain.Student
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.viewmodels.RegisterOutcome
import com.example.cardiosimulator.ui.viewmodels.StudentRegistrationViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StudentsScreen(
    viewModel: StudentRegistrationViewModel,
    onViewLearningScale: (com.example.cardiosimulator.domain.Student) -> Unit
) {
    val students by viewModel.students.collectAsState()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<RegisterOutcome?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = viewModel.exportData()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                importStatus = "Export successful"
            }.onFailure {
                importStatus = "Export failed: ${it.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                if (json != null) {
                    val (s, r) = viewModel.importData(json)
                    importStatus = "Imported $s students and $r results"
                }
            }.onFailure {
                importStatus = "Import failed: ${it.message}"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Card
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = stringResource(R.string.students_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.students_subtitle),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; outcome = null },
                    label = { Text(stringResource(R.string.exam_field_full_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it; outcome = null },
                    label = { Text(stringResource(R.string.exam_field_group)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; outcome = null },
                    label = { Text(stringResource(R.string.students_field_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val result = viewModel.register(fullName, group, email)
                        outcome = result
                        if (result == RegisterOutcome.Added) {
                            fullName = ""
                            group = ""
                            email = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = fullName.isNotBlank() && group.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.students_register))
                }

                if (outcome != null) {
                    val (text, color) = when (outcome) {
                        RegisterOutcome.Added -> stringResource(R.string.students_added) to Positive
                        RegisterOutcome.Duplicate -> stringResource(R.string.students_duplicate) to ElectrodeFaultRed
                        RegisterOutcome.Invalid -> stringResource(R.string.students_invalid) to ElectrodeFaultRed
                        RegisterOutcome.SaveFailed -> stringResource(R.string.students_save_failed) to ElectrodeFaultRed
                        null -> "" to Color.Transparent
                    }
                    Text(
                        text = text,
                        color = color,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }

        // Roster Card
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.students_list_title)} (${students.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row {
                        TextButton(onClick = { importLauncher.launch("application/json") }) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.students_import))
                        }
                        TextButton(onClick = {
                            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            exportLauncher.launch("students_export_$date.json")
                        }) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.students_export))
                        }
                    }
                }

                if (importStatus != null) {
                    Text(
                        text = importStatus!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (students.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.students_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(students, key = { it.id }) { student ->
                            StudentRow(
                                student = student,
                                dateText = dateFormatter.format(Date(student.registeredAt)),
                                onViewLearningScale = { onViewLearningScale(student) },
                                onRemove = { viewModel.remove(student.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentRow(
    student: com.example.cardiosimulator.domain.Student,
    dateText: String,
    onViewLearningScale: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = student.fullName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            val details = listOfNotNull(
                student.group,
                student.email,
                dateText
            ).joinToString(" · ")
            Text(text = details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        IconButton(onClick = onViewLearningScale) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = stringResource(R.string.students_learning_scale),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.students_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
