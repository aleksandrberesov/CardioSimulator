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
import com.example.cardiosimulator.domain.AssemblyAttempt
import com.example.cardiosimulator.domain.Test
import com.example.cardiosimulator.domain.TestQuestion
import com.example.cardiosimulator.domain.generators.TestGenerator
import com.example.cardiosimulator.network.GroupTestServer
import com.example.cardiosimulator.network.GroupTestService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class TestingMode { Choice, Individual, Group }

class TestViewModel(
    private val resultStore: ExamResultStore? = null,
    private val bankRepository: QuestionBankRepository? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val _mode = MutableStateFlow(TestingMode.Choice)
    val mode: StateFlow<TestingMode> = _mode.asStateFlow()

    private val _activeTest = MutableStateFlow<Test?>(null)
    val activeTest: StateFlow<Test?> = _activeTest.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    private val _selectedOptionId = MutableStateFlow<String?>(null)
    val selectedOptionId: StateFlow<String?> = _selectedOptionId.asStateFlow()

    private val _correctCount = MutableStateFlow(0)
    val correctCount: StateFlow<Int> = _correctCount.asStateFlow()

    private val _assemblyAttempt = MutableStateFlow<AssemblyAttempt?>(null)
    val assemblyAttempt: StateFlow<AssemblyAttempt?> = _assemblyAttempt.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun setMode(mode: TestingMode) {
        _mode.value = mode
    }

    fun start(test: Test) {
        _activeTest.value = test
        _currentIndex.value = 0
        _revealed.value = false
        _selectedOptionId.value = null
        _correctCount.value = 0
        _finished.value = false
        initAssembly()
        resetTimer()
    }

    private fun initAssembly() {
        val question = currentQuestion ?: return
        val assembly = question.assemble
        if (assembly != null) {
            _assemblyAttempt.value = AssemblyAttempt(assembly, _currentIndex.value + 1)
        } else {
            _assemblyAttempt.value = null
        }
    }

    private fun resetTimer() {
        timerJob?.cancel()
        val test = _activeTest.value ?: return
        if (test.questionTimeSeconds > 0) {
            _remainingSeconds.value = test.questionTimeSeconds
            timerJob = viewModelScope.launch {
                while (_remainingSeconds.value > 0 && !_revealed.value) {
                    delay(1000)
                    _remainingSeconds.value -= 1
                }
                if (_remainingSeconds.value == 0 && !_revealed.value) {
                    reveal(null)
                }
            }
        } else {
            _remainingSeconds.value = 0
        }
    }

    fun select(optionId: String) {
        if (_revealed.value) return
        reveal(optionId)
    }

    private fun reveal(optionId: String?) {
        val test = _activeTest.value ?: return
        val question = test.questions.getOrNull(_currentIndex.value) ?: return
        
        _selectedOptionId.value = optionId
        if (optionId == question.correctOptionId) {
            _correctCount.value += 1
        }
        _revealed.value = true
        timerJob?.cancel()
    }

    fun next() {
        val test = _activeTest.value ?: return
        if (_currentIndex.value + 1 < test.questions.size) {
            _currentIndex.value += 1
            _revealed.value = false
            _selectedOptionId.value = null
            initAssembly()
            resetTimer()
        } else {
            _finished.value = true
        }
    }

    fun restart() {
        val test = _activeTest.value ?: return
        start(test)
    }

    fun close() {
        _activeTest.value = null
        _assemblyAttempt.value = null
        timerJob?.cancel()
        _mode.value = TestingMode.Choice
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
                resultStore?.save(result)
            }
        )
        _isGroupSessionActive.value = true
    }

    fun stopGroupSession() {
        groupService?.stopServer()
        _isGroupSessionActive.value = false
    }

    fun placePiece(slotIndex: Int, key: String?) {
        if (_revealed.value) return
        _assemblyAttempt.value?.place(slotIndex, key)
        // In Compose, updating a property of an object in a StateFlow might not trigger re-composition
        // unless we update the StateFlow itself. AssemblyAttempt uses mutableStateOf for placedKey,
        // so it might be fine, but let's be safe if we want to ensure everything reacts.
        _assemblyAttempt.update { it } 
    }

    fun submitAssembly() {
        if (_revealed.value) return
        val attempt = _assemblyAttempt.value ?: return
        if (!attempt.isComplete) return

        if (attempt.allCorrect) {
            _correctCount.value += 1
        }
        _revealed.value = true
        timerJob?.cancel()
    }

    val currentQuestion: TestQuestion?
        get() = _activeTest.value?.questions?.getOrNull(_currentIndex.value)
}
