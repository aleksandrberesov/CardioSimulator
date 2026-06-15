package com.example.cardiosimulator.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.CourseZipExtractor
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.FileCourseSource
import com.example.cardiosimulator.data.FilePathologySource
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.PathologyZipExtractor
import com.example.cardiosimulator.data.SampleCourseSeeder
import com.example.cardiosimulator.data.FileOskeSource
import com.example.cardiosimulator.data.OskeRepository
import com.example.cardiosimulator.data.OskeResultStore
import com.example.cardiosimulator.data.SampleOskeSeeder
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.network.TcpConnectionState
import com.example.cardiosimulator.network.TcpMessage
import com.example.cardiosimulator.network.TcpProtocol
import com.example.cardiosimulator.data.ZipCompressor
import kotlinx.coroutines.Dispatchers
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
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

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
    private val appContext: Context? = null,
    val prefs: DataSourcePrefs? = null,
    private val tcpReconnectIntervalMs: Long = 5000L,
) : ViewModel() {

    val operatingModes = appState.operatingModes

    private val _selectedLanguage = MutableStateFlow(currentSystemLanguage(appState.selectedLanguage))
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _selectedOperatingMode = MutableStateFlow(appState.selectedOperatingMode)
    val selectedOperatingMode: StateFlow<OperatingModeModel> = _selectedOperatingMode

    private val _tcpIp = MutableStateFlow(appState.tcpIp)
    val tcpIp: StateFlow<String> = _tcpIp.asStateFlow()

    private val _tcpPort = MutableStateFlow(appState.tcpPort)
    val tcpPort: StateFlow<Int> = _tcpPort.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isDrawerFixed = MutableStateFlow(false)
    val isDrawerFixed: StateFlow<Boolean> = _isDrawerFixed.asStateFlow()

    private val _tcpConnectionState = MutableStateFlow<TcpConnectionState>(TcpConnectionState.Disconnected)
    val tcpConnectionState: StateFlow<TcpConnectionState> = _tcpConnectionState.asStateFlow()

    private val _dataState = MutableStateFlow<DataState>(DataState.NotConfigured)
    val dataState: StateFlow<DataState> = _dataState.asStateFlow()

    // Course bundle lifecycle. `DataState.Ready.pathologyCount` is reused
    // here as a generic "loaded count" — when the courses UI lands the
    // field will be renamed to `itemCount` across both pipelines.
    private val _courseDataState = MutableStateFlow<DataState>(DataState.NotConfigured)
    val courseDataState: StateFlow<DataState> = _courseDataState.asStateFlow()

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

    fun selectCourse(id: String?) {
        _selectedCourseId.value = id
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
                val savedUri = p.treeUri.first()
                if (savedUri != null) {
                    loadFromSaf(ctx, savedUri)
                    if (_dataState.value is DataState.Ready) {
                        _isDataConfirmed.value = true
                    }
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

                p.lastOperatingMode.first()?.let { modeName ->
                    try {
                        val modeId = OperatingMode.valueOf(modeName)
                        operatingModes.find { it.id == modeId }?.let { modeModel ->
                            updateOperatingMode(modeModel, persist = false)
                        }
                    } catch (_: Exception) {}
                }

                // Courses pipeline — restore the user's last picked bundle
                // if one exists, else pick up a previously seeded / extracted
                // `filesDir/courses/` (e.g. the sample template). Failures are
                // silent here; the UI surfaces them via [courseDataState].
                if (courseRepository != null) {
                    val coursesUri = p.coursesTreeUri.first()
                    if (coursesUri != null) {
                        loadCoursesFromSaf(ctx, coursesUri)
                    } else {
                        val source = FileCourseSource(File(ctx.filesDir, COURSES_DIR))
                        if (source.isValid()) {
                            courseRepository.setSource(source)
                            reloadCourses(courseRepository)
                        }
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

    fun updateOperatingMode(mode: OperatingModeModel, persist: Boolean = true) {
        appState.updateMode(mode)
        _selectedOperatingMode.value = mode
        if (persist) {
            viewModelScope.launch {
                prefs?.setLastOperatingMode(mode.id.name)
            }
        }
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
                        sampleRate = null,
                        params = paramsMap,
                    )
                    val header = TcpProtocol.encode(msg) + "\n"
                    socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun sendStopCommand() {
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

    private fun sendUploadArchive() {
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
        viewModelScope.launch {
            p.setTreeUri(uri)
            loadFromSaf(context, uri, forceUnzip = true)
        }
    }

    /**
     * SAF entry point for the courses bundle. Mirrors [setDataFolder]
     * for pathologies — persists the picked URI and re-extracts into
     * `filesDir/courses/`. No confirmation gate.
     */
    fun setCourseDataFolder(context: Context, uri: Uri) {
        val p = prefs ?: return
        if (courseRepository == null) return
        _isDataConfirmed.value = false
        viewModelScope.launch {
            p.setCoursesTreeUri(uri)
            loadCoursesFromSaf(context, uri, forceUnzip = true)
        }
    }

    /**
     * Seeds the bundled starter course into `filesDir/courses/` and loads it
     * — the "New course from template" path for authors with no bundle of
     * their own. Clears any saved SAF courses URI so the seeded copy (not a
     * stale picked zip) is what reloads next launch.
     */
    fun loadSampleCourses(context: Context) {
        val repo = courseRepository ?: return
        _isDataConfirmed.value = false
        viewModelScope.launch {
            _courseDataState.value = DataState.Loading
            prefs?.setCoursesTreeUri(null)
            val targetDir = File(context.filesDir, COURSES_DIR)
            val ok = withContext(Dispatchers.IO) { SampleCourseSeeder.seed(context, targetDir) }
            if (ok) {
                val source = FileCourseSource(targetDir)
                if (source.isValid()) {
                    repo.setSource(source)
                    reloadCourses(repo)
                    return@launch
                }
            }
            _courseDataState.value = DataState.Error(DataState.Error.Reason.Empty)
        }
    }

    fun confirmData() { _isDataConfirmed.value = true }

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

    fun uploadCourses() {
        val socket = tcpSocket ?: return
        val ctx = appContext ?: return
        val sourceDir = File(ctx.filesDir, COURSES_DIR)

        viewModelScope.launch(Dispatchers.IO) {
            val zipFile = ZipCompressor.zipToCache(ctx, sourceDir, "courses_upload.zip") ?: return@launch
            tcpSendMutex.withLock {
                try {
                    val msg = TcpMessage.UploadMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        filename = "Courses.zip",
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

    private suspend fun loadFromSaf(context: Context, uri: Uri, forceUnzip: Boolean = false) {
        val repo = repository ?: return
        _dataState.value = DataState.Loading

        val targetDir = File(context.filesDir, PATHOLOGIES_DIR)
        val fileSource = FilePathologySource(targetDir)

        if (!forceUnzip && fileSource.isValid()) {
            repo.setSource(fileSource)
            if (reload(repo)) return
        }

        val ok = withContext(Dispatchers.IO) {
            PathologyZipExtractor.extract(context, uri, targetDir)
        }
        if (ok) {
            val newSource = FilePathologySource(targetDir)
            if (newSource.isValid()) {
                repo.setSource(newSource)
                if (reload(repo)) return
            }
        }
        _dataState.value = DataState.Error(DataState.Error.Reason.Empty)
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
            true
        }
    }

    // ─── Courses pipeline (mirrors the pathology helpers above) ────────

    private suspend fun loadCoursesFromSaf(
        context: Context,
        uri: Uri,
        forceUnzip: Boolean = false,
    ) {
        val repo = courseRepository ?: return
        _courseDataState.value = DataState.Loading

        val targetDir = File(context.filesDir, COURSES_DIR)
        val fileSource = FileCourseSource(targetDir)

        if (!forceUnzip && fileSource.isValid()) {
            repo.setSource(fileSource)
            if (reloadCourses(repo)) return
        }

        val ok = withContext(Dispatchers.IO) {
            CourseZipExtractor.extract(context, uri, targetDir)
        }
        if (ok) {
            val newSource = FileCourseSource(targetDir)
            if (newSource.isValid()) {
                repo.setSource(newSource)
                if (reloadCourses(repo)) return
            }
        }
        _courseDataState.value = DataState.Error(DataState.Error.Reason.Empty)
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
            true
        }
    }

    companion object {
        /** Subdirectory under `filesDir` where the extracted dataset lives. */
        const val PATHOLOGIES_DIR: String = "pathologies"

        /** Subdirectory under `filesDir` where the extracted course bundle lives. */
        const val COURSES_DIR: String = "courses"

        /** Subdirectory under `filesDir` where the OSKE data lives. */
        const val OSKE_DIR: String = "oske"

        /** Virtual course ID representing the unfiltered list of all rhythms. */
        const val ALL_RHYTHMS_ID: String = "all_rhythms"
    }
}
