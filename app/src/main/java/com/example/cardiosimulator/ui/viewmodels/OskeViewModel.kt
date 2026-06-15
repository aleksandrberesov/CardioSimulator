package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.OskeRepository
import com.example.cardiosimulator.data.OskeResultStore
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OskeSubMode { Exam, Results }

class OskeViewModel(
    val repository: OskeRepository,
    val resultStore: OskeResultStore,
    val pathologyRepository: PathologyRepository
) : ViewModel() {

    private val _subMode = MutableStateFlow(OskeSubMode.Exam)
    val subMode: StateFlow<OskeSubMode> = _subMode.asStateFlow()

    fun setSubMode(mode: OskeSubMode) {
        _subMode.value = mode
    }

    // --- Exam State ---

    private val _activeForm = MutableStateFlow<OskeForm?>(null)
    val activeForm: StateFlow<OskeForm?> = _activeForm.asStateFlow()

    private val _studentInfo = MutableStateFlow<OskeStudentInfo?>(null)
    val studentInfo: StateFlow<OskeStudentInfo?> = _studentInfo.asStateFlow()

    private val _selections = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val selections: StateFlow<Map<String, List<String>>> = _selections.asStateFlow()

    private val _activeEcgId = MutableStateFlow<String?>(null)
    val activeEcgId: StateFlow<String?> = _activeEcgId.asStateFlow()

    private val _lastResult = MutableStateFlow<OskeResult?>(null)
    val lastResult: StateFlow<OskeResult?> = _lastResult.asStateFlow()

    fun startExam(student: OskeStudentInfo, specialty: OskeSpecialty, ecgId: String) {
        val formId = when (specialty) {
            OskeSpecialty.Therapy -> "therapy"
            OskeSpecialty.Cardiology, OskeSpecialty.FunctionalDiagnostics -> "cardiology"
        }
        val form = repository.getForm(formId)
        if (form != null) {
            _activeForm.value = form
            _studentInfo.value = student
            _activeEcgId.value = ecgId
            _selections.value = emptyMap()
            _lastResult.value = null
        }
    }

    fun selectOption(questionId: String, optionId: String, kind: OskeAnswerKind) {
        val current = _selections.value[questionId] ?: emptyList()
        val next = when (kind) {
            OskeAnswerKind.Single -> listOf(optionId)
            OskeAnswerKind.Multi -> {
                if (current.contains(optionId)) current - optionId
                else current + optionId
            }
        }
        _selections.value = _selections.value + (questionId to next)
    }

    fun submitExam() {
        val form = _activeForm.value ?: return
        val ecgId = _activeEcgId.value ?: return
        val student = _studentInfo.value ?: return
        val key = repository.getAnswerKey(ecgId, form.formId) ?: return

        val result = OskeGrader.grade(form, key, _selections.value, student, ecgId)
        _lastResult.value = result
        resultStore.save(result)
    }

    fun resetExam() {
        _activeForm.value = null
        _studentInfo.value = null
        _activeEcgId.value = null
        _selections.value = emptyMap()
        _lastResult.value = null
    }

    // --- Results State ---

    private val _results = MutableStateFlow<List<OskeResult>>(emptyList())
    val results: StateFlow<List<OskeResult>> = _results.asStateFlow()

    fun refreshResults() {
        _results.value = resultStore.list()
    }

    fun getEcgIdsWithKeys(specialty: OskeSpecialty): List<String> {
        val formId = when (specialty) {
            OskeSpecialty.Therapy -> "therapy"
            OskeSpecialty.Cardiology, OskeSpecialty.FunctionalDiagnostics -> "cardiology"
        }
        return repository.getAnswerKeyEcgIds(formId)
    }

    // --- Constructor State ---

    private val _constructorSpecialty = MutableStateFlow(OskeSpecialty.Therapy)
    val constructorSpecialty: StateFlow<OskeSpecialty> = _constructorSpecialty.asStateFlow()

    private val _constructorSelectedEcgId = MutableStateFlow<String?>(null)
    val constructorSelectedEcgId: StateFlow<String?> = _constructorSelectedEcgId.asStateFlow()

    fun setConstructorSelection(specialty: OskeSpecialty, ecgId: String?) {
        _constructorSpecialty.value = specialty
        _constructorSelectedEcgId.value = ecgId
    }

    private val _constructorForm = MutableStateFlow<OskeForm?>(null)
    val constructorForm: StateFlow<OskeForm?> = _constructorForm.asStateFlow()

    private val _constructorEcgId = MutableStateFlow<String?>(null)
    val constructorEcgId: StateFlow<String?> = _constructorEcgId.asStateFlow()

    private val _constructorSelections = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val constructorSelections: StateFlow<Map<String, List<String>>> = _constructorSelections.asStateFlow()

    fun setupConstructor(specialty: OskeSpecialty, ecgId: String) {
        val formId = when (specialty) {
            OskeSpecialty.Therapy -> "therapy"
            OskeSpecialty.Cardiology, OskeSpecialty.FunctionalDiagnostics -> "cardiology"
        }
        val form = repository.getForm(formId)
        if (form != null) {
            _constructorForm.value = form
            _constructorEcgId.value = ecgId
            val key = repository.getAnswerKey(ecgId, formId)
            _constructorSelections.value = key?.correctOptionIds ?: emptyMap()
        }
    }

    fun selectConstructorOption(questionId: String, optionId: String, kind: OskeAnswerKind) {
        val current = _constructorSelections.value[questionId] ?: emptyList()
        val next = when (kind) {
            OskeAnswerKind.Single -> listOf(optionId)
            OskeAnswerKind.Multi -> {
                if (current.contains(optionId)) current - optionId
                else current + optionId
            }
        }
        _constructorSelections.value = _constructorSelections.value + (questionId to next)
    }

    fun saveAnswerKey() {
        val form = _constructorForm.value ?: return
        val ecgId = _constructorEcgId.value ?: return
        val key = OskeAnswerKey(ecgId, form.formId, _constructorSelections.value)
        repository.saveAnswerKey(key)
    }
}
