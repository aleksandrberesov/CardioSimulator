package com.example.cardiosimulator.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.FileOskeSource
import com.example.cardiosimulator.data.OskeRepository
import com.example.cardiosimulator.data.OskeResultStore
import com.example.cardiosimulator.data.SampleOskeSeeder
import com.example.cardiosimulator.data.FileTestSource
import com.example.cardiosimulator.data.TestRepository
import com.example.cardiosimulator.data.ExamResultStore
import com.example.cardiosimulator.data.FileQuestionBankSource
import com.example.cardiosimulator.data.QuestionBankRepository
import com.example.cardiosimulator.data.TestThemeStore
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.AppEdition
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.MasteryReport
import com.example.cardiosimulator.domain.MasteryRollup
import com.example.cardiosimulator.domain.Taxonomy
import com.example.cardiosimulator.domain.Test
import com.example.cardiosimulator.domain.TestSeed
import com.example.cardiosimulator.network.TcpConnectionState
import com.example.cardiosimulator.network.TcpMessage
import com.example.cardiosimulator.network.TcpProtocol
import com.example.cardiosimulator.data.ZipCompressor
import com.example.cardiosimulator.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.cardiosimulator.data.EncryptedPathologySource
import com.example.cardiosimulator.data.EncryptedCourseSource
import com.example.cardiosimulator.data.OverlayPathologySource
import com.example.cardiosimulator.data.OverlayCourseSource
import com.example.cardiosimulator.data.EncryptedArchive
import com.example.cardiosimulator.data.crypto.ChunkedPackChannel
import com.example.cardiosimulator.data.crypto.ContentCrypto
import com.example.cardiosimulator.data.crypto.WritableEncryptedOverlay
import java.io.FileInputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import kotlin.math.roundToInt

/**
 * High-level state of the user-controlled ECG dataset.
 *
 * Lifecycle: NotConfigured -> Loading -> (Ready | Error). Re-picking a
 * ZIP cycles back through Loading.
 */
sealed class DataState {
    data object NotConfigured : DataState()
    data object Loading : DataState()
    data class Ready(val pathologyCount: Int) : DataState()
    data class Error(val reason: Reason) : DataState() {
        enum class Reason { Unreadable, Empty, BadManifest }
    }
}

/** Report DTO for post-import feedback (Part B). */
data class CourseLoadReport(
    val success: Boolean,
    val fileName: String,
    val courses: List<CourseLoadSummary>,
    val totalLectures: Int,
    val previewCourseTitle: String?,
    val previewLectureTitle: String?,
    val previewSnippet: String?,
) {
    val courseCount get() = courses.size
    /** Manifest advertises lectures but none yielded readable body text. */
    val structureWithoutContent get() =
        success && totalLectures > 0 && previewSnippet.isNullOrEmpty()
}

/** Per-course summary for [CourseLoadReport]. */
data class CourseLoadSummary(val title: String, val lectureCount: Int, val languages: List<String>)

/**
 * Snapshot of the current background loading process (extraction, counting, etc).
 */
data class LoadingInfo(
    val title: String = "",
    val statusLine: String = "",     // "243 / 1057 records · 23%"
    val detail: String = "",         // current record file
    val percent: Int = 0,
    val indeterminate: Boolean = true,
    val canCancel: Boolean = false,
)

/**
 * Central application view-model. Owns:
 *
 * - Persistent app settings (language, theme, TCP target) via
 *   [DataSourcePrefs].
 * - The current [PathologyRepository] and its [dataState] lifecycle.
 * - The TCP socket and its connection state.
 *
 * Phase 4 of the architecture migration adds editor state hooked off the
 * same repository (in-memory mutable copy of a [com.example.cardiosimulator.domain.PathologyFile],
 * save back through [FilePathologySource.writePathology]).
 */
class AppViewModel(
    private val appState: AppStateModel,
    val repository: PathologyRepository? = null,
    val courseRepository: CourseRepository? = null,
    val oskeRepository: OskeRepository? = null,
    val oskeResultStore: OskeResultStore? = null,
    val testRepository: TestRepository? = null,
    val questionBankRepository: QuestionBankRepository? = null,
    val testThemeStore: TestThemeStore? = null,
    val examResultStore: ExamResultStore? = null,
    val appContext: Context? = null,
    val prefs: DataSourcePrefs? = null,
    private val tcpReconnectIntervalMs: Long = 5000L,
) : ViewModel() {

    val operatingModes = appState.operatingModes

    private val _selectedLanguage = MutableStateFlow(currentSystemLanguage(appState.selectedLanguage))
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _preserveCourseSelection = mutableStateOf(false)
    val preserveCourseSelection: Boolean get() = _preserveCourseSelection.value

    fun setPreserveCourseSelection(value: Boolean) {
        _preserveCourseSelection.value = value
    }

    private val _selectedOperatingMode = MutableStateFlow(appState.selectedOperatingMode)
    val selectedOperatingMode: StateFlow<OperatingModeModel> = _selectedOperatingMode

    private val _pendingMode = MutableStateFlow<OperatingModeModel?>(null)
    val pendingMode: StateFlow<OperatingModeModel?> = _pendingMode.asStateFlow()

    /** The active screen registers this to veto/deferred-confirm leaving it (null = no guard). */
    var leaveGuard: (() -> Boolean)? = null

    fun requestOperatingMode(mode: OperatingModeModel) {
        if (mode.id == _selectedOperatingMode.value.id) return
        if (leaveGuard?.invoke() == false) {
            _pendingMode.value = mode
            return
        }
        updateOperatingMode(mode)
    }

    fun confirmPendingMode() {
        _pendingMode.value?.let { updateOperatingMode(it) }
        _pendingMode.value = null
    }

    fun cancelPendingMode() {
        _pendingMode.value = null
    }

    private val _pendingTest = MutableStateFlow<Test?>(null)
    val pendingTest: StateFlow<Test?> = _pendingTest.asStateFlow()

    fun setPendingTest(test: Test?) {
        _pendingTest.value = test
    }

    private val _tcpIp = MutableStateFlow(appState.tcpIp)
    val tcpIp: StateFlow<String> = _tcpIp.asStateFlow()

    private val _tcpPort = MutableStateFlow(appState.tcpPort)
    val tcpPort: StateFlow<Int> = _tcpPort.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isDrawerFixed = MutableStateFlow(false)
    val isDrawerFixed: StateFlow<Boolean> = _isDrawerFixed.asStateFlow()

    private val _isRhythmListGrouped = MutableStateFlow(true)
    val isRhythmListGrouped: StateFlow<Boolean> = _isRhythmListGrouped.asStateFlow()

    private val _isClinicalMode = MutableStateFlow(false)
    val isClinicalMode: StateFlow<Boolean> = _isClinicalMode.asStateFlow()

    private val _collapsedRhythmGroups = MutableStateFlow<Set<String>>(emptySet())
    val collapsedRhythmGroups: StateFlow<Set<String>> = _collapsedRhythmGroups.asStateFlow()

    private val _collapsedSubgroups = MutableStateFlow<Set<String>>(emptySet())
    val collapsedSubgroups: StateFlow<Set<String>> = _collapsedSubgroups.asStateFlow()

    private val _tcpConnectionState = MutableStateFlow<TcpConnectionState>(TcpConnectionState.Disconnected)
    val tcpConnectionState: StateFlow<TcpConnectionState> = _tcpConnectionState.asStateFlow()

    private val _dataState = MutableStateFlow<DataState>(DataState.NotConfigured)
    val dataState: StateFlow<DataState> = _dataState.asStateFlow()

    private val _masteryReport = MutableStateFlow(MasteryReport.Empty)
    val masteryReport: StateFlow<MasteryReport> = _masteryReport.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    private val _loadingInfo = MutableStateFlow(LoadingInfo())
    val loadingInfo: StateFlow<LoadingInfo> = _loadingInfo.asStateFlow()

    private var extractionJob: Job? = null
    internal var pointsJob: Job? = null

    fun cancelLoading() { extractionJob?.cancel() }

    // Course bundle lifecycle. `DataState.Ready.pathologyCount` is reused
    // here as a generic "loaded count" — when the courses UI lands the
    // field will be renamed to `itemCount` across both pipelines.
    private val _courseDataState = MutableStateFlow<DataState>(DataState.NotConfigured)
    val courseDataState: StateFlow<DataState> = _courseDataState.asStateFlow()

    private val _courseLoadReport = MutableStateFlow<CourseLoadReport?>(null)
    val courseLoadReport: StateFlow<CourseLoadReport?> = _courseLoadReport.asStateFlow()

    fun clearCourseLoadReport() { _courseLoadReport.value = null }

    /**
     * Course index derived from the loaded course manifest, sorted by
     * English title. Each [CourseEntry] carries its
     * [CourseEntry.pathologies], so this doubles as the course →
     * pathologies map that [com.example.cardiosimulator.ui.panels.RhythmSelector]
     * uses to scope the rhythm list. Empty when no bundle is loaded.
     */
    val courses: StateFlow<List<CourseEntry>> =
        courseRepository?.manifestFlow
            ?.map { m ->
                val entries = m?.entries?.sortedBy { it.titleEn.lowercase() } ?: emptyList()
                val allRhythmsEntry = CourseEntry(
                    id = ALL_RHYTHMS_ID,
                    titleEn = "All Rhythms",
                    nameRu = "Все ритмы",
                    lecturesCount = 0
                )
                listOf(allRhythmsEntry) + entries
            }
            ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
            ?: MutableStateFlow<List<CourseEntry>>(emptyList()).asStateFlow()

    private val _selectedCourseId = MutableStateFlow<String?>(ALL_RHYTHMS_ID)
    val selectedCourseId: StateFlow<String?> = _selectedCourseId.asStateFlow()

    private val _showMonitorOverlay = MutableStateFlow(false)
    val showMonitorOverlay: StateFlow<Boolean> = _showMonitorOverlay.asStateFlow()

    fun selectCourse(id: String?) {
        val same = _selectedCourseId.value == id
        _selectedCourseId.value = id
        if (id != ALL_RHYTHMS_ID) {
            _showMonitorOverlay.value = false
        }
        // Force a refresh if selecting the same course (e.g. from a reload context)
        if (same) triggerRefresh()
    }

    fun triggerRefresh() {
        _refreshTrigger.value++
    }

    fun setShowMonitorOverlay(show: Boolean) {
        _showMonitorOverlay.value = show
    }

    private val _isDataConfirmed = MutableStateFlow(false)
    val isDataConfirmed: StateFlow<Boolean> = _isDataConfirmed.asStateFlow()

    private val tcpSendMutex = kotlinx.coroutines.sync.Mutex()

    init {
        val repo = repository
        val ctx = appContext
        val p = prefs
        if (repo != null && ctx != null && p != null) {
            viewModelScope.launch {
                // CSP2 Content Pack (Phase 2): Copy bundled packs to filesDir if they exist in assets.
                // Ciphertext copy is safe for the "no-plaintext-at-rest" invariant.
                withContext(Dispatchers.IO) {
                    copyBundledPacks(ctx)
                    cleanupExtractedData(ctx)
                    dropLegacyPicks(ctx)
                }

                val savedUri = p.treeUri.first()
                if (savedUri != null) {
                    loadFromSafPack(ctx, savedUri, isCourse = false)
                    if (_dataState.value is DataState.Ready) {
                        _isDataConfirmed.value = true
                    }
                }

                // If no SAF pick exists (or it failed), try the encrypted pack baseline.
                if (_dataState.value is DataState.NotConfigured) {
                    tryLoadPathologyPack(ctx, repo)
                }

                p.languageTag.first()?.let { tag ->
                    Language.fromTag(tag)?.let { updateLanguage(it, persist = false) }
                }

                val savedIp = p.tcpIp.first()
                val savedPort = p.tcpPort.first()
                if (savedIp != null || savedPort != null) {
                    val ip = savedIp ?: _tcpIp.value
                    val port = savedPort ?: _tcpPort.value
                    _tcpIp.value = ip
                    _tcpPort.value = port
                    appState.updateTcpConnection(ip, port)
                }

                p.isDarkTheme.first()?.let { isDark -> _isDarkTheme.value = isDark }

                // The app intentionally always launches on Teaching mode (the default
                // selectedOperatingMode). Restore of lastOperatingMode was removed to match
                // the Windows port behavior.

                // Courses pipeline — restore the user's last picked bundle
                // if one exists, else pick up the encrypted pack baseline.
                if (courseRepository != null) {
                    val coursesUri = p.coursesTreeUri.first()
                    if (coursesUri != null) {
                        loadFromSafPack(ctx, coursesUri, isCourse = true)
                    }

                    if (_courseDataState.value is DataState.NotConfigured) {
                        tryLoadCoursePack(ctx, courseRepository)
                    }
                }

                // OSKE pipeline
                if (oskeRepository != null) {
                    val targetDir = File(ctx.filesDir, OSKE_DIR)
                    val source = FileOskeSource(targetDir)
                    if (File(targetDir, "manifest.txt").canRead()) {
                        oskeRepository.swapSource(source)
                    } else {
                        withContext(Dispatchers.IO) {
                            SampleOskeSeeder.seed(ctx, targetDir)
                            oskeRepository.swapSource(FileOskeSource(targetDir))
                        }
                    }
                }

                // Testing & Examination pipeline
                if (testRepository != null) {
                    val testsDir = File(ctx.filesDir, TESTS_DIR)
                    val source = FileTestSource(testsDir)
                    testRepository.swapSource(source)
                    
                    if (questionBankRepository != null) {
                        val bankDir = File(ctx.filesDir, TEST_BANK_DIR)
                        val bankSource = FileQuestionBankSource(bankDir)
                        questionBankRepository.import(bankSource.readQuestions()) // Reload from source
                    }

                    if (examResultStore != null) {
                        viewModelScope.launch {
                            examResultStore.resultsChanged.collect {
                                withContext(Dispatchers.IO) {
                                    val report = MasteryRollup.compute(examResultStore.list(), Taxonomy.shared)
                                    _masteryReport.value = report
                                }
                            }
                        }
                    }

                    // Seed the demo test and question bank once pathologies are loaded
                    viewModelScope.launch {
                        dataState.collect { state ->
                            if (state is DataState.Ready) {
                                val pathologyIds = repo.pathologies().map { it.id }
                                if (pathologyIds.isNotEmpty()) {
                                    // 1. Seed demo test if missing
                                    if (testRepository.tests().isEmpty()) {
                                        val demoTest = TestSeed.sample(pathologyIds)
                                        testRepository.writeTest(demoTest)
                                    }
                                    
                                    // 2. Seed question bank if missing (Part A requirement)
                                    if (questionBankRepository != null && questionBankRepository.questions().isEmpty()) {
                                        val bankQuestions = TestSeed.bankQuestions(pathologyIds)
                                        questionBankRepository.import(bankQuestions)
                                    }
                                    
                                    // Seed themes if missing
                                    testThemeStore?.readThemes()
                                }
                            }
                        }
                    }
                }
            }
        } else if (repo != null) {
            // Asset-only / preview path: try to load the bundled manifest.
            viewModelScope.launch {
                if (withContext(Dispatchers.IO) { repo.loadManifest() }) {
                    _dataState.value = DataState.Ready(repo.pathologies().size)
                } else {
                    _dataState.value = DataState.Ready(0)
                }
            }
        } else {
            _dataState.value = DataState.Ready(0)
        }
    }

    fun updateLanguage(language: Language, persist: Boolean = true) {
        if (_selectedLanguage.value == language) return
        appState.updateLanguage(language)
        _selectedLanguage.value = language
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        if (persist) {
            viewModelScope.launch { prefs?.setLanguageTag(language.tag) }
        }
    }

    private fun currentSystemLanguage(default: Language): Language {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!locales.isEmpty) locales.get(0)?.toLanguageTag() else null
        return Language.fromTag(tag) ?: default
    }

    fun updateOperatingMode(mode: OperatingModeModel) {
        appState.updateMode(mode)
        _selectedOperatingMode.value = mode
        // The mode is not persisted: the app always launches on Teaching (see MainActivity).
    }

    fun updateTcpConnection(ip: String, port: Int) {
        appState.updateTcpConnection(ip, port)
        _tcpIp.value = ip
        _tcpPort.value = port
        viewModelScope.launch { prefs?.setTcpConnection(ip, port) }
    }

    fun updateDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        viewModelScope.launch { prefs?.setDarkTheme(isDark) }
    }

    fun setDrawerFixed(fixed: Boolean) {
        _isDrawerFixed.value = fixed
    }

    fun setRhythmListGrouped(grouped: Boolean) {
        _isRhythmListGrouped.value = grouped
    }

    fun setClinicalMode(enabled: Boolean) {
        _isClinicalMode.value = enabled
        if (enabled) {
            _isRhythmListGrouped.value = true
        }
    }

    fun toggleRhythmGroupCollapsed(groupKey: String) {
        val current = _collapsedRhythmGroups.value
        if (current.contains(groupKey)) {
            _collapsedRhythmGroups.value = current - groupKey
        } else {
            _collapsedRhythmGroups.value = current + groupKey
        }
    }

    fun toggleSubgroupCollapsed(subgroupKey: String) {
        val current = _collapsedSubgroups.value
        if (current.contains(subgroupKey)) {
            _collapsedSubgroups.value = current - subgroupKey
        } else {
            _collapsedSubgroups.value = current + subgroupKey
        }
    }

    fun expandAllRhythms() {
        _collapsedRhythmGroups.value = emptySet()
        _collapsedSubgroups.value = emptySet()
    }

    fun collapseAllRhythms(groupKeys: Set<String>, subgroupKeys: Set<String>) {
        _collapsedRhythmGroups.value = _collapsedRhythmGroups.value + groupKeys
        _collapsedSubgroups.value = _collapsedSubgroups.value + subgroupKeys
    }

    fun expandGroupAndSubgroup(groupKey: String?, subgroupKey: String?) {
        if (groupKey != null) {
            _collapsedRhythmGroups.value = _collapsedRhythmGroups.value - groupKey
        }
        if (subgroupKey != null) {
            _collapsedSubgroups.value = _collapsedSubgroups.value - subgroupKey
        }
    }

    fun toggleTcpConnection() {
        val currentState = _tcpConnectionState.value
        if (currentState is TcpConnectionState.Disconnected || currentState is TcpConnectionState.Error) {
            connectTcp()
        } else {
            disconnectTcp()
        }
    }

    fun dismissTcpError() {
        if (_tcpConnectionState.value is TcpConnectionState.Error) {
            _tcpConnectionState.value = TcpConnectionState.Disconnected
        }
    }

    private var tcpSocket: Socket? = null
    private var connectionJob: kotlinx.coroutines.Job? = null

    private fun connectTcp() {
        val ip = _tcpIp.value
        val port = _tcpPort.value
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _tcpConnectionState.value = TcpConnectionState.Connecting
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(ip, port), tcpReconnectIntervalMs.toInt())
                    tcpSocket = socket
                    _tcpConnectionState.value = TcpConnectionState.Connected

                    sendUploadArchive()

                    // Drain incoming frames so a socket EOF (disconnect) is detected.
                    val reader = socket.getInputStream().bufferedReader()
                    while (isActive) {
                        reader.readLine() ?: break
                    }
                } catch (_: IOException) {
                    // Connection lost or failed to connect
                } finally {
                    try { socket.close() } catch (_: Exception) {}
                    if (tcpSocket == socket) tcpSocket = null
                }

                if (isActive) {
                    _tcpConnectionState.value = TcpConnectionState.Disconnected
                    delay(tcpReconnectIntervalMs)
                }
            }
        }
    }

    private fun disconnectTcp() {
        connectionJob?.cancel()
        stopPointsStream()
        viewModelScope.launch(Dispatchers.IO) {
            try { tcpSocket?.close() } catch (_: IOException) {}
            tcpSocket = null
            _tcpConnectionState.value = TcpConnectionState.Disconnected
        }
    }

    fun sendStartCommand(pathology: String? = null, name: String? = null) {
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return

        viewModelScope.launch(Dispatchers.IO) {
            tcpSendMutex.withLock {
                try {
                    val paramsMap = mutableMapOf<String, String>()
                    if (pathology != null) paramsMap["pathology"] = pathology
                    if (name != null) paramsMap["name"] = name
                    val msg = TcpMessage.StartCommand(
                        id = java.util.UUID.randomUUID().toString(),
                        sampleRate = EcgCalibration().sampleRateHz.roundToInt(),
                        params = paramsMap,
                    )
                    val header = TcpProtocol.encode(msg) + "\n"
                    socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()

                    startPointsStream(pathology, socket)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun sendStopCommand() {
        stopPointsStream()
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return
        viewModelScope.launch(Dispatchers.IO) {
            tcpSendMutex.withLock {
                try {
                    val msg = TcpMessage.StopCommand(id = java.util.UUID.randomUUID().toString())
                    val header = TcpProtocol.encode(msg) + "\n"
                    socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                } catch (_: Exception) {
                }
            }
        }
    }

    internal fun startPointsStream(pathologyId: String?, socket: Socket) {
        stopPointsStream()
        if (pathologyId == null || repository == null) return

        val waveforms = mutableMapOf<Lead, FloatArray>()
        repository.manifest()?.leadOrder?.forEach { lead ->
            repository.leadWaveform(pathologyId, lead)?.let { points ->
                if (points.values.isNotEmpty()) {
                    waveforms[lead] = points.values.toFloatArray()
                }
            }
        }

        if (waveforms.isEmpty()) return

        pointsJob = viewModelScope.launch(Dispatchers.IO) {
            pointsLoopAsync(pathologyId, waveforms, socket)
        }
    }

    internal fun stopPointsStream() {
        pointsJob?.cancel()
        pointsJob = null
    }

    internal suspend fun pointsLoopAsync(
        pathologyId: String,
        waveforms: Map<Lead, FloatArray>,
        socketAtLaunch: Socket
    ) {
        val sampleRateHz = EcgCalibration().sampleRateHz
        val chunkSize = 50
        val periodMs = (chunkSize * 1000.0 / sampleRateHz).toLong()
        val cursors = waveforms.keys.associateWith { 0 }.toMutableMap()

        while (currentCoroutineContext().isActive) {
            // Stale-socket and connection-state guard
            if (tcpSocket !== socketAtLaunch || _tcpConnectionState.value !is TcpConnectionState.Connected) {
                break
            }

            tcpSendMutex.withLock {
                try {
                    val out = socketAtLaunch.getOutputStream()
                    waveforms.forEach { (lead, values) ->
                        val offset = cursors[lead] ?: 0
                        val count = minOf(chunkSize, values.size)
                        val chunk = FloatArray(count)
                        for (i in 0 until count) {
                            chunk[i] = values[(offset + i) % values.size]
                        }

                        val msg = TcpMessage.PointsMessage(
                            id = java.util.UUID.randomUUID().toString(),
                            lead = lead,
                            identy = pathologyId,
                            offset = offset,
                            values = chunk.toList()
                        )
                        val frame = TcpProtocol.encode(msg) + "\n"
                        out.write(frame.toByteArray(Charsets.UTF_8))
                        
                        cursors[lead] = (offset + count) % values.size
                    }
                    out.flush()
                } catch (_: Exception) {
                    // Socket error, loop will bail on next check or via disconnectTcp
                }
            }
            delay(periodMs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPointsStream()
    }

    private fun sendUploadArchive() {
        if (AppEdition.IS_LIMITED) return // Interim: no upload in student edition

        val socket = tcpSocket ?: return
        val ctx = appContext ?: return
        val sourceDir = File(ctx.filesDir, PATHOLOGIES_DIR)

        viewModelScope.launch(Dispatchers.IO) {
            val zipFile = ZipCompressor.zipToCache(ctx, sourceDir, "upload.zip") ?: return@launch
            tcpSendMutex.withLock {
                try {
                    val msg = TcpMessage.UploadMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        filename = "Pathologies.zip",
                        size = zipFile.length()
                    )
                    val header = TcpProtocol.encode(msg) + "\n"
                    val out = socket.getOutputStream()
                    out.write(header.toByteArray(Charsets.UTF_8))
                    zipFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                } catch (_: Exception) {
                } finally {
                    zipFile.delete()
                }
            }
        }
    }

    fun setDataFolder(context: Context, uri: Uri) {
        val p = prefs ?: return
        _isDataConfirmed.value = false
        extractionJob = viewModelScope.launch {
            val stats = getFileStats(context, uri)
            p.setTreeUri(uri)
            p.setTreeUriStats(stats.first, stats.second)
            try {
                loadFromSafPack(context, uri, isCourse = false)
            } catch (ce: CancellationException) {
                _loadingInfo.value = LoadingInfo()
                _dataState.value = DataState.NotConfigured
                throw ce
            }
        }
    }

    /**
     * SAF entry point for the courses bundle. Mirrors [setDataFolder]
     * for pathologies.
     */
    fun setCourseDataFolder(context: Context, uri: Uri) {
        val p = prefs ?: return
        if (courseRepository == null) return
        _isDataConfirmed.value = false
        viewModelScope.launch {
            val stats = getFileStats(context, uri)
            p.setCoursesTreeUri(uri)
            p.setCoursesTreeUriStats(stats.first, stats.second)
            loadFromSafPack(context, uri, isCourse = true)
            val fileName = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name ?: "course pack"
            _courseLoadReport.value = buildCourseLoadReport(fileName, _courseDataState.value is DataState.Ready)
        }
    }

    /**
     * Loads the bundled starter course.
     */
    fun loadSampleCourses(context: Context) {
        val repo = courseRepository ?: return
        _isDataConfirmed.value = false
        viewModelScope.launch {
            prefs?.setCoursesTreeUri(null)
            tryLoadCoursePack(context, repo)
        }
    }

    fun confirmData() { _isDataConfirmed.value = true }

    fun setWelcomeOptOut(optOut: Boolean) {
        viewModelScope.launch {
            prefs?.setWelcomeOptOut(optOut)
        }
    }

    fun exportZip(context: Context, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceDir = File(context.filesDir, PATHOLOGIES_DIR)
            ZipCompressor.zip(context, sourceDir, destUri)
        }
    }

    fun exportCoursesZip(context: Context, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceDir = File(context.filesDir, COURSES_DIR)
            ZipCompressor.zip(context, sourceDir, destUri)
        }
    }


    private suspend fun loadFromSafPack(context: Context, uri: Uri, isCourse: Boolean) {
        val repo = if (isCourse) courseRepository else repository
        val prefs = prefs ?: return
        if (repo == null) return

        val stateFlow = if (isCourse) _courseDataState else _dataState
        stateFlow.value = DataState.Loading
        _loadingInfo.value = LoadingInfo(
            title = context.getString(R.string.data_source_preparing),
            indeterminate = true, canCancel = false
        )

        val isPack = withContext(Dispatchers.IO) { isPack(context, uri) }
        if (!isPack) {
            // Drop legacy pick and fallback
            if (isCourse) prefs.setCoursesTreeUri(null) else prefs.setTreeUri(null)
            if (isCourse) tryLoadCoursePack(context, repo as CourseRepository) else tryLoadPathologyPack(context, repo as PathologyRepository)
            return
        }

        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Failed to open PFD")
                val channel = ChunkedPackChannel(FileInputStream(pfd.fileDescriptor).channel)
                val archive = EncryptedArchive(channel)
                
                val hash = getPackHash(context, uri)
                val overlayFile = WritableEncryptedOverlay.getOverlayFile(context.filesDir, hash)
                val overlay = WritableEncryptedOverlay(overlayFile)

                withContext(Dispatchers.Main) {
                    if (isCourse) {
                        val source = OverlayCourseSource(EncryptedCourseSource(archive), overlay)
                        (repo as CourseRepository).setSource(source)
                        reloadCourses(repo)
                    } else {
                        val source = OverlayPathologySource(EncryptedPathologySource(archive), overlay)
                        (repo as PathologyRepository).setSource(source)
                        reload(repo)
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    stateFlow.value = DataState.Error(DataState.Error.Reason.Unreadable)
                }
            }
        }
    }

    private fun isPack(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && ContentCrypto.looksLikePack(magic)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getPackHash(context: Context, uri: Uri): String {
        val name = try {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name
        } catch (e: Exception) {
            null
        } ?: "unknown"
        return name.hashCode().toString(16)
    }

    private suspend fun dropLegacyPicks(context: Context) {
        val p = prefs ?: return
        val uri = p.treeUri.first()
        if (uri != null && !isPack(context, uri)) {
            p.setTreeUri(null)
        }
        val courseUri = p.coursesTreeUri.first()
        if (courseUri != null && !isPack(context, courseUri)) {
            p.setCoursesTreeUri(null)
        }
    }

    private fun cleanupExtractedData(context: Context) {
        File(context.filesDir, PATHOLOGIES_DIR).deleteRecursively()
        File(context.filesDir, COURSES_DIR).deleteRecursively()
    }

    private suspend fun reload(repo: PathologyRepository): Boolean {
        val ok = withContext(Dispatchers.IO) { repo.loadManifest() }
        if (!ok) {
            _dataState.value = DataState.Error(DataState.Error.Reason.BadManifest)
            return false
        }
        val count = repo.pathologies().size
        return if (count == 0) {
            _dataState.value = DataState.Error(DataState.Error.Reason.Empty)
            false
        } else {
            _dataState.value = DataState.Ready(count)
            triggerRefresh()
            true
        }
    }

    // ─── Courses pipeline (mirrors the pathology helpers above) ────────

    private fun buildCourseLoadReport(fileName: String, loaded: Boolean): CourseLoadReport {
        val repo = courseRepository
        if (!loaded || repo == null)
            return CourseLoadReport(false, fileName, emptyList(), 0, null, null, null)

        val summaries = mutableListOf<CourseLoadSummary>()
        var total = 0
        var pc: String? = null
        var pl: String? = null
        var ps: String? = null

        for (entry in repo.courses()) {
            if (entry.id == ALL_RHYTHMS_ID) continue
            val course = repo.readCourse(entry.id)
            val count = course?.lectures?.size ?: entry.lecturesCount
            total += count
            summaries.add(CourseLoadSummary(displayTitle(entry.nameRu, entry.titleEn, entry.id),
                                            count, course?.languages ?: emptyList()))
            
            if (ps != null || course == null) continue
            
            // Try to find a lecture with content for the preview
            for (item in contentItems(course)) {
                val lang = course.languages.firstOrNull() ?: "en"
                val text = repo.readLecture(entry.id, item.id, lang)?.rawHtml?.let { plainTextPreview(it, 400) }
                if (!text.isNullOrBlank()) {
                    pc = displayTitle(entry.nameRu, entry.titleEn, entry.id)
                    pl = displayTitle(item.nameRu, item.titleEn, item.id)
                    ps = text
                    break
                }
            }
        }
        return CourseLoadReport(true, fileName, summaries, total, pc, pl, ps)
    }

    private data class ContentItem(val id: String, val titleEn: String, val nameRu: String?)

    private fun contentItems(course: com.example.cardiosimulator.domain.Course): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        course.lectures.forEach { items.add(ContentItem(it.id, it.titleEn, it.nameRu)) }
        // Also include topics just in case they have a corresponding lecture file (Part B parity)
        course.topics.forEach { items.add(ContentItem(it.id, it.titleEn, it.nameRu)) }
        return items
    }

    private fun displayTitle(nameRu: String?, titleEn: String, id: String): String {
        return if (selectedLanguage.value == Language.RU && !nameRu.isNullOrBlank()) {
            nameRu
        } else {
            titleEn.ifBlank { nameRu ?: id }
        }
    }

    private fun plainTextPreview(html: String, maxLength: Int): String {
        val plain = androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (plain.length > maxLength) plain.take(maxLength) + "…" else plain
    }

    private suspend fun reloadCourses(repo: CourseRepository): Boolean {
        val ok = withContext(Dispatchers.IO) { repo.loadManifest() }
        if (!ok) {
            _courseDataState.value = DataState.Error(DataState.Error.Reason.BadManifest)
            return false
        }
        val count = repo.courses().size
        return if (count == 0) {
            _courseDataState.value = DataState.Error(DataState.Error.Reason.Empty)
            false
        } else {
            _courseDataState.value = DataState.Ready(count)
            triggerRefresh()
            true
        }
    }

    private fun getFileStats(context: Context, uri: Uri): Pair<Long, Long> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (cursor.moveToFirst()) {
                    val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    val modified = if (modifiedIndex != -1) cursor.getLong(modifiedIndex) else 0L
                    size to modified
                } else 0L to 0L
            } ?: (0L to 0L)
        } catch (e: Exception) {
            0L to 0L
        }
    }

    private fun copyBundledPacks(ctx: Context) {
        val packs = listOf("Pathologies.pak", "Courses.pak")
        packs.forEach { fileName ->
            val dest = File(ctx.filesDir, fileName)
            if (!dest.exists()) {
                runCatching {
                    ctx.assets.open(fileName).use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private suspend fun tryLoadPathologyPack(ctx: Context, repo: PathologyRepository) {
        val pak = File(ctx.filesDir, "Pathologies.pak")
        if (!pak.canRead()) return
        
        withContext(Dispatchers.IO) {
            runCatching {
                val channel = ChunkedPackChannel(FileInputStream(pak).channel)
                val archive = EncryptedArchive(channel)
                
                val overlayFile = WritableEncryptedOverlay.getOverlayFile(ctx.filesDir, "bundled_pathologies")
                val overlay = WritableEncryptedOverlay(overlayFile)
                
                val baseSource = EncryptedPathologySource(archive)
                val source = OverlayPathologySource(baseSource, overlay)
                if (source.readManifest() != null) {
                    withContext(Dispatchers.Main) {
                        repo.setSource(source)
                        reload(repo)
                    }
                }
            }
        }
    }

    private suspend fun tryLoadCoursePack(ctx: Context, repo: CourseRepository) {
        val pak = File(ctx.filesDir, "Courses.pak")
        if (!pak.canRead()) return

        withContext(Dispatchers.IO) {
            runCatching {
                val channel = ChunkedPackChannel(FileInputStream(pak).channel)
                val archive = EncryptedArchive(channel)
                
                val overlayFile = WritableEncryptedOverlay.getOverlayFile(ctx.filesDir, "bundled_courses")
                val overlay = WritableEncryptedOverlay(overlayFile)
                
                val baseSource = EncryptedCourseSource(archive)
                val source = OverlayCourseSource(baseSource, overlay)
                if (source.readManifest() != null) {
                    withContext(Dispatchers.Main) {
                        repo.setSource(source)
                        reloadCourses(repo)
                    }
                }
            }
        }
    }

    companion object {
        /** Subdirectory under `filesDir` where the extracted dataset lives. */
        const val PATHOLOGIES_DIR: String = "pathologies"

        /** Subdirectory under `filesDir` where the extracted course bundle lives. */
        const val COURSES_DIR: String = "courses"

        /** Subdirectory under `filesDir` where the OSKE data lives. */
        const val OSKE_DIR: String = "oske"

        /** Subdirectory under `filesDir` where the tests live. */
        const val TESTS_DIR: String = "tests"

        /** Subdirectory under `filesDir` where the question bank lives. */
        const val TEST_BANK_DIR: String = "tests/bank"

        /** Subdirectory under `filesDir` where test images live. */
        const val TEST_IMAGES_DIR: String = "tests/images"

        /** Subdirectory under `filesDir` where exam results live. */
        const val TEST_RESULTS_DIR: String = "tests/results"

        /** Virtual course ID representing the unfiltered list of all rhythms. */
        const val ALL_RHYTHMS_ID: String = "all_rhythms"
    }
}
