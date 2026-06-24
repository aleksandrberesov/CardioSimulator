package com.example.cardiosimulator.ui.viewmodels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.ExamResultStore
import com.example.cardiosimulator.data.QuestionBankRepository
import com.example.cardiosimulator.domain.*
import com.example.cardiosimulator.domain.generators.TestGenerator
import com.example.cardiosimulator.network.GroupTestServer
import com.example.cardiosimulator.network.GroupTestService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ExaminationViewModel(
    private val resultStore: ExamResultStore,
    private val bankRepository: QuestionBankRepository? = null,
    private val appContext: Context? = null
) : ViewModel() {

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

    // --- Individual Mode ---

    fun startIndividual(test: Test, student: ExamStudentInfo) {
        start(test, student)
    }

    fun generateAndStartIndividual(count: Int, theme: String?, student: ExamStudentInfo) {
        val bank = bankRepository?.questions() ?: return
        val test = TestGenerator.generate(bank, count, theme)
        start(test, student)
    }

    private fun start(test: Test, student: ExamStudentInfo) {
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

    // --- Group Mode ---

    private var groupService: GroupTestService? = null
    private val _isGroupSessionActive = MutableStateFlow(false)
    val isGroupSessionActive: StateFlow<Boolean> = _isGroupSessionActive.asStateFlow()

    private val _groupIp = MutableStateFlow<String?>(null)
    val groupIp: StateFlow<String?> = _groupIp.asStateFlow()

    private val _participants = MutableStateFlow<List<GroupTestServer.Participant>>(emptyList())
    val participants: StateFlow<List<GroupTestServer.Participant>> = _participants.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GroupTestService.LocalBinder
            groupService = binder.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            groupService = null
        }
    }

    init {
        appContext?.let {
            val intent = Intent(it, GroupTestService::class.java)
            it.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        viewModelScope.launch {
            while (true) {
                delay(2000)
                groupService?.let {
                    _participants.value = it.getParticipants()
                }
            }
        }
    }

    override fun onCleared() {
        appContext?.unbindService(serviceConnection)
        super.onCleared()
    }

    fun startGroupSession(count: Int, theme: String?) {
        val bank = bankRepository?.questions() ?: return
        val ctx = appContext ?: return
        
        _groupIp.value = GroupTestServer.getLocalIpAddress()
        
        groupService?.startServer(
            port = 8080,
            generateTest = { name, group -> TestGenerator.generate(bank, count, theme) },
            resolveImage = { qid -> 
                val q = bank.find { it.id == qid } ?: return@startServer null
                q.imagePath?.let { path -> File(ctx.filesDir, "${AppViewModel.TEST_IMAGES_DIR}/$path") }
            },
            onResult = { result ->
                resultStore.save(result)
                refreshResults()
            }
        )
        _isGroupSessionActive.value = true
    }

    fun stopGroupSession() {
        groupService?.stopServer()
        _isGroupSessionActive.value = false
    }

    // --- Results ---

    private val _results = MutableStateFlow<List<ExamResult>>(emptyList())
    val results: StateFlow<List<ExamResult>> = _results.asStateFlow()

    fun refreshResults() {
        _results.value = resultStore.list()
    }

    val currentQuestion: TestQuestion?
        get() = _activeTest.value?.questions?.getOrNull(_currentIndex.value)

    fun reset() {
        _activeTest.value = null
        _studentInfo.value = null
        _currentIndex.value = 0
        _selections.value = emptyMap()
        _lastResult.value = null
        timerJob?.cancel()
    }
}
