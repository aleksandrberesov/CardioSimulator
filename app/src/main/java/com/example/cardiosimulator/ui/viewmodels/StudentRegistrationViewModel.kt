package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.data.ExamResultStore
import com.example.cardiosimulator.data.OskeResultStore
import com.example.cardiosimulator.data.StudentStore
import com.example.cardiosimulator.domain.Student
import com.example.cardiosimulator.domain.StudentExportPackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RegisterOutcome { Added, Invalid, Duplicate, SaveFailed }

class StudentRegistrationViewModel(
    private val store: StudentStore,
    private val examStore: ExamResultStore? = null,
    private val oskeStore: OskeResultStore? = null
) : ViewModel() {
    private val _students = MutableStateFlow(store.list())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun register(fullName: String, group: String, email: String?): RegisterOutcome {
        val student = Student.create(fullName, group, email) ?: return RegisterOutcome.Invalid
        if (store.contains(student.fullName, student.group)) return RegisterOutcome.Duplicate
        
        val ok = store.add(student)
        if (ok) {
            _students.value = store.list()
            return RegisterOutcome.Added
        }
        return RegisterOutcome.SaveFailed
    }

    fun remove(id: String) {
        if (store.remove(id)) {
            _students.value = store.list()
        }
    }

    fun exportData(): String {
        val students = store.list()
        val examResults = examStore?.list() ?: emptyList()
        val oskeResults = oskeStore?.list() ?: emptyList()

        val pkg = StudentExportPackage(
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            students = students,
            examResults = examResults,
            oskeResults = oskeResults
        )
        return json.encodeToString(pkg)
    }

    fun importData(jsonContent: String): Pair<Int, Int> {
        val pkg = runCatching { json.decodeFromString<StudentExportPackage>(jsonContent) }.getOrNull()
            ?: return 0 to 0

        var studentsImported = 0
        pkg.students.forEach { s ->
            val wasAdded = !store.contains(s.fullName, s.group)
            if (store.addOrUpdate(s)) {
                if (wasAdded) studentsImported++
            }
        }

        var resultsImported = 0
        
        // 1. Exam Results
        val allExam = examStore?.list() ?: emptyList()
        val incomingExam = pkg.examResults.ifEmpty { pkg.results ?: emptyList() }
        incomingExam.forEach { r ->
            val duplicate = allExam.any {
                it.student.fullName == r.student.fullName &&
                it.student.group == r.student.group &&
                it.testId == r.testId &&
                Math.abs(it.timestamp - r.timestamp) < 2000
            }
            if (!duplicate) {
                if (examStore?.save(r) == true) resultsImported++
            }
        }

        // 2. OSKE Results
        val allOske = oskeStore?.list() ?: emptyList()
        pkg.oskeResults.forEach { r ->
            val duplicate = allOske.any {
                it.student.fullName == r.student.fullName &&
                it.student.group == r.student.group &&
                it.ecgId == r.ecgId &&
                it.formId == r.formId &&
                Math.abs(it.timestamp - r.timestamp) < 2000
            }
            if (!duplicate) {
                if (oskeStore?.save(r) == true) resultsImported++
            }
        }

        _students.value = store.list()
        return studentsImported to resultsImported
    }
}
