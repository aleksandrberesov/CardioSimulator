package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.ExamResultStore
import com.example.cardiosimulator.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExaminationViewModel(private val resultStore: ExamResultStore) : ViewModel() {

    private val _activeTest = MutableStateFlow<Test?>(null)
    val activeTest: StateFlow<Test?> = _activeTest.asStateFlow()

    private val _studentInfo = MutableStateFlow<ExamStudentInfo?>(null)
    val studentInfo: StateFlow<ExamStudentInfo?> = _studentInfo.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _selections = MutableStateFlow<Map<String, String>>(emptyMap())
    val selections: StateFlow<Map<String, String>> = _selections.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _lastResult = MutableStateFlow<ExamResult?>(null)
    val lastResult: StateFlow<ExamResult?> = _lastResult.asStateFlow()

    private var timerJob: Job? = null

    fun start(test: Test, student: ExamStudentInfo) {
        _activeTest.value = test
        _studentInfo.value = student
        _currentIndex.value = 0
        _selections.value = emptyMap()
        _lastResult.value = null
        resetTimer()
    }

    private fun resetTimer() {
        timerJob?.cancel()
        val test = _activeTest.value ?: return
        if (test.questionTimeSeconds > 0) {
            _remainingSeconds.value = test.questionTimeSeconds
            timerJob = viewModelScope.launch {
                while (_remainingSeconds.value > 0) {
                    delay(1000)
                    _remainingSeconds.value -= 1
                }
                next()
            }
        } else {
            _remainingSeconds.value = 0
        }
    }

    fun select(optionId: String) {
        val question = currentQuestion ?: return
        _selections.value = _selections.value + (question.id to optionId)
    }

    fun next() {
        val test = _activeTest.value ?: return
        if (_currentIndex.value + 1 < test.questions.size) {
            _currentIndex.value += 1
            resetTimer()
        } else {
            submit()
        }
    }

    fun submit() {
        val test = _activeTest.value ?: return
        val student = _studentInfo.value ?: return
        val result = ExamGrader.grade(test, _selections.value, student)
        _lastResult.value = result
        resultStore.save(result)
        _activeTest.value = null
        timerJob?.cancel()
    }

    fun reset() {
        _activeTest.value = null
        _studentInfo.value = null
        _currentIndex.value = 0
        _selections.value = emptyMap()
        _lastResult.value = null
        timerJob?.cancel()
    }

    // --- Results ---

    private val _results = MutableStateFlow<List<ExamResult>>(emptyList())
    val results: StateFlow<List<ExamResult>> = _results.asStateFlow()

    fun refreshResults() {
        _results.value = resultStore.list()
    }

    val currentQuestion: TestQuestion?
        get() = _activeTest.value?.questions?.getOrNull(_currentIndex.value)
}
