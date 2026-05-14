package com.example.cardiosimulator.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.EcgRepository
import com.example.cardiosimulator.data.PathologyGroup
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.data.FileEcgSource
import com.example.cardiosimulator.data.ZipCompressor
import com.example.cardiosimulator.data.ZipDecompressor
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.DerivedLeads
import com.example.cardiosimulator.domain.EditablePart
import com.example.cardiosimulator.domain.EditableSeries
import com.example.cardiosimulator.domain.EcgFileFormat
import com.example.cardiosimulator.domain.UndoStack
import java.io.File
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingModeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.cardiosimulator.domain.EcgSeries
import com.example.cardiosimulator.domain.SeriesPartRef
import com.example.cardiosimulator.domain.WaveformPart
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.network.TcpConnectionState
import com.example.cardiosimulator.network.TcpMessage
import com.example.cardiosimulator.network.TcpProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.InetSocketAddress
import java.net.Socket
import java.io.IOException
import android.provider.OpenableColumns

/**
 * High-level state of the user-controlled ECG dataset.
 *
 * Lifecycle: NotConfigured -> Loading -> (Ready | Error). Re-picking a
 * folder cycles back through Loading.
 */
sealed class DataState {
    object NotConfigured : DataState()
    object Loading : DataState()
    data class Ready(val seriesCount: Int, val partsCount: Int) : DataState()
    data class Error(val reason: Reason) : DataState() {
        enum class Reason { MissingSubdirs, Unreadable, Empty }
    }
}

class AppViewModel(
    private val appState: AppStateModel,
    val ecgRepository: EcgRepository? = null,
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

    private val _tcpConnectionState = MutableStateFlow<TcpConnectionState>(TcpConnectionState.Disconnected)
    val tcpConnectionState: StateFlow<TcpConnectionState> = _tcpConnectionState.asStateFlow()

    private val _dataState = MutableStateFlow<DataState>(DataState.NotConfigured)
    val dataState: StateFlow<DataState> = _dataState.asStateFlow()

    private val _isDataConfirmed = MutableStateFlow(false)
    val isDataConfirmed: StateFlow<Boolean> = _isDataConfirmed.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _lastAck = MutableStateFlow<TcpMessage.AckMessage?>(null)
    val lastAck: StateFlow<TcpMessage.AckMessage?> = _lastAck.asStateFlow()

    // ─── Editor state ────────────────────────────────────────────────────
    //
    // EditablePart / EditableSeries are the mutable working copies the editor
    // mutates. `dirtyParts` / `dirtySeries` track which identies need to be
    // flushed to disk. `undo*` tracks the per-record undo stack.

    private val editableParts: MutableMap<String, EditablePart> = mutableMapOf()
    private val editableSeries: MutableMap<String, EditableSeries> = mutableMapOf()
    private val partUndo: MutableMap<String, UndoStack<EditablePart>> = mutableMapOf()
    private val seriesUndo: MutableMap<String, UndoStack<EditableSeries>> = mutableMapOf()

    private val _dirtyParts = MutableStateFlow<Set<String>>(emptySet())
    val dirtyParts: StateFlow<Set<String>> = _dirtyParts.asStateFlow()

    private val _dirtySeries = MutableStateFlow<Set<String>>(emptySet())
    val dirtySeries: StateFlow<Set<String>> = _dirtySeries.asStateFlow()

    /** True if there is any unsaved edit anywhere. Drives save-confirm dialog. */
    val hasUnsavedEdits: Boolean
        get() = _dirtyParts.value.isNotEmpty() || _dirtySeries.value.isNotEmpty()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    sealed class ExportResult {
        data class Success(val fileName: String) : ExportResult()
        data class Failure(val reason: String) : ExportResult()
    }

    fun clearExportResult() { _exportResult.value = null }

    init {
        // Restore a previously picked folder, if any. If none is stored, we
        // stay in NotConfigured and the UI will show the picker. The repo
        // may also be unavailable in @Preview — in that case we leave the
        // state as NotConfigured but UI components can ignore it.
        val repo = ecgRepository
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

                p.isDarkTheme.first()?.let { isDark ->
                    _isDarkTheme.value = isDark
                }
            }
        } else if (repo != null) {
            // Preview / asset-only path (no prefs available, e.g. @Preview):
            // load whatever the source has and force-mark the dataset Ready
            // so the preview doesn't get stuck on the DataSourceScreen when
            // assets are empty.
            viewModelScope.launch {
                reload(repo)
                _dataState.value = DataState.Ready(repo.allSeries().size, repo.allParts().size)
            }
        } else {
            // No repository at all (rare; pure UI preview). Treat as Ready so
            // gating composables don't block.
            _dataState.value = DataState.Ready(0, 0)
        }
    }

    fun updateLanguage(language: Language, persist: Boolean = true) {
        if (_selectedLanguage.value == language) return
        appState.updateLanguage(language)
        _selectedLanguage.value = language
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        if (persist) {
            viewModelScope.launch {
                prefs?.setLanguageTag(language.tag)
            }
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
    }

    fun updateTcpConnection(ip: String, port: Int) {
        appState.updateTcpConnection(ip, port)
        _tcpIp.value = ip
        _tcpPort.value = port
        viewModelScope.launch {
            prefs?.setTcpConnection(ip, port)
        }
    }

    fun updateDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        viewModelScope.launch {
            prefs?.setDarkTheme(isDark)
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

                    // Upload is now manual: see `uploadEditedData()`. The
                    // previous auto-upload re-sent the originally-picked ZIP
                    // URI, which would silently transmit stale data after
                    // edits — see plan Phase 0 / risks.

                    val reader = socket.getInputStream().bufferedReader()
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val message = TcpProtocol.decodeOrNull(line)
                        if (message is TcpMessage.AckMessage) {
                            _lastAck.value = message
                        }
                    }
                } catch (e: IOException) {
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
            try {
                tcpSocket?.close()
            } catch (e: IOException) {
                // Ignore
            } finally {
                tcpSocket = null
                _tcpConnectionState.value = TcpConnectionState.Disconnected
            }
        }
    }

    private fun uploadZipFile(context: Context, uri: Uri) {
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return

        viewModelScope.launch(Dispatchers.IO) {
            _isUploading.value = true
            _lastAck.value = null
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val size = pfd.statSize
                    val filename = getFileName(context, uri) ?: "data.zip"

                    val uploadMsg = TcpMessage.UploadMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        filename = filename,
                        size = size
                    )

                    val header = TcpProtocol.encode(uploadMsg) + "\n"
                    val outputStream = socket.getOutputStream()
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    java.io.FileInputStream(pfd.fileDescriptor).use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    outputStream.flush()
                }
            } catch (e: Exception) {
                // Handle upload error (e.g. log or update state)
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    fun sendStartCommand(pathology: String? = null, name: String? = null) {
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val paramsMap = mutableMapOf<String, String>()
                if (pathology != null) {
                    paramsMap["pathology"] = pathology
                }
                if (name != null) {
                    paramsMap["name"] = name
                }

                val msg = TcpMessage.StartCommand(
                    id = java.util.UUID.randomUUID().toString(),
                    sampleRate = null,
                    params = paramsMap
                )
                val header = TcpProtocol.encode(msg) + "\n"
                socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun sendStopCommand() {
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msg = TcpMessage.StopCommand(id = java.util.UUID.randomUUID().toString())
                val header = TcpProtocol.encode(msg) + "\n"
                socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    /**
     * Called by [com.example.cardiosimulator.ui.screens.DataSourceScreen]
     * after the user picks a folder via SAF. Persists the URI, swaps the
     * repository's source, and reloads.
     */
    fun setDataFolder(context: Context, uri: Uri) {
        val p = prefs ?: return
        _isDataConfirmed.value = false
        viewModelScope.launch {
            p.setTreeUri(uri)
            loadFromSaf(context, uri, forceUnzip = true)
            // Upload no longer fires implicitly; user triggers it from the
            // editor or settings.
        }
    }

    fun confirmData() {
        _isDataConfirmed.value = true
    }

    private suspend fun loadFromSaf(context: Context, uri: Uri, forceUnzip: Boolean = false) {
        val repo = ecgRepository ?: return
        _dataState.value = DataState.Loading

        val targetDir = File(context.filesDir, "unzipped_ecg")
        val fileSource = FileEcgSource(targetDir)

        // Optimization: if we are NOT forcing an unzip and we already have valid content,
        // use it. This happens on app startup.
        if (!forceUnzip && fileSource.isValid()) {
            repo.setSource(fileSource)
            if (reload(repo)) return
        }

        val ok = withContext(Dispatchers.IO) {
            ZipDecompressor.unzip(context, uri, targetDir)
        }

        if (ok) {
            // Re-initialize source to find the new directories if they moved
            val newSource = FileEcgSource(targetDir)
            if (newSource.isValid()) {
                repo.setSource(newSource)
                if (reload(repo)) return
            }
        }

        _dataState.value = DataState.Error(DataState.Error.Reason.Empty)
    }

    private suspend fun reload(repo: EcgRepository): Boolean {
        withContext(Dispatchers.IO) { repo.load() }
        val series = repo.allSeries()
        val parts = repo.allParts()
        
        return if (series.isEmpty() && parts.isEmpty()) {
            // Reset editor state when source swaps under us.
            editableParts.clear()
            editableSeries.clear()
            partUndo.clear()
            seriesUndo.clear()
            _dirtyParts.value = emptySet()
            _dirtySeries.value = emptySet()
            false
        } else {
            _dataState.value = DataState.Ready(series.size, parts.size)
            true
        }
    }

    // ─── Editor: editable part/series accessors ───────────────────────────

    /** Returns (or lazily creates) an editable copy of the part with [identy]. */
    fun editablePart(identy: String): EditablePart? {
        editableParts[identy]?.let { return it }
        val repo = ecgRepository ?: return null
        val part = repo.allParts().firstOrNull { it.identy == identy } ?: return null
        // Best-effort guess at the file name on disk: identy + ".txt".
        val fileName = guessPartFileName(identy)
        val ep = EditablePart.from(part, fileName)
        editableParts[identy] = ep
        return ep
    }

    /** Returns (or lazily creates) an editable copy of the series with [identy]. */
    fun editableSeries(identy: String): EditableSeries? {
        editableSeries[identy]?.let { return it }
        val repo = ecgRepository ?: return null
        val s = repo.allSeries().firstOrNull { it.identy == identy } ?: return null
        val es = EditableSeries.from(s)
        editableSeries[identy] = es
        return es
    }

    private fun guessPartFileName(identy: String): String {
        val repo = ecgRepository ?: return "$identy.txt"
        val src = repoSource() ?: return "$identy.txt"
        val parts = src.listParts()
        // Heuristic: the file whose first line matches identy:<x>.
        for (name in parts) {
            val text = src.readPart(name) ?: continue
            if (text.lineSequence().firstOrNull()?.startsWith("identy:") == true &&
                text.lineSequence().firstOrNull()?.substringAfter(':')?.trim() == identy
            ) return name
        }
        return "$identy.txt"
    }

    private fun repoSource(): com.example.cardiosimulator.data.EcgSource? {
        // Reflection-free reach: EcgRepository keeps source private, so we
        // route through the prefs-derived dir.
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "unzipped_ecg")
        if (!dir.exists()) return null
        return FileEcgSource(dir)
    }

    /** Push current state to undo and apply [mutator] to the part. */
    fun mutatePart(identy: String, mutator: (EditablePart) -> Unit): EditablePart? {
        val ep = editablePart(identy) ?: return null
        partUndo.getOrPut(identy) { UndoStack() }.push(ep.copy(
            anchors = ep.anchors.toMutableList(),
        ))
        mutator(ep)
        // Anchors changed → invalidate baked samples (but only when anchors
        // are present; legacy parts without anchors keep their original
        // samples so we never lose data).
        if (ep.anchors.isNotEmpty()) ep.samples = null
        markPartDirty(identy)
        return ep
    }

    fun mutateSeries(identy: String, mutator: (EditableSeries) -> Unit): EditableSeries? {
        val es = editableSeries(identy) ?: return null
        seriesUndo.getOrPut(identy) { UndoStack() }.push(es.copy(
            partRefs = es.partRefs.toMutableList(),
        ))
        mutator(es)
        markSeriesDirty(identy)
        return es
    }

    fun undoPart(identy: String): EditablePart? {
        val snap = partUndo[identy]?.pop() ?: return null
        editableParts[identy] = snap
        markPartDirty(identy)
        return snap
    }

    fun undoSeries(identy: String): EditableSeries? {
        val snap = seriesUndo[identy]?.pop() ?: return null
        editableSeries[identy] = snap
        markSeriesDirty(identy)
        return snap
    }

    private fun markPartDirty(identy: String) {
        _dirtyParts.value = _dirtyParts.value + identy
    }

    private fun markSeriesDirty(identy: String) {
        _dirtySeries.value = _dirtySeries.value + identy
    }

    /** Flush all editable parts/series to disk via the writable source. */
    fun saveAll() {
        val ctx = appContext ?: return
        val targetDir = File(ctx.filesDir, "unzipped_ecg")
        val src = FileEcgSource(targetDir)
        if (!src.isWritable()) return
        viewModelScope.launch(Dispatchers.IO) {
            val savedParts = mutableSetOf<String>()
            for (id in _dirtyParts.value) {
                val ep = editableParts[id] ?: continue
                val name = ep.fileName.ifEmpty { "$id.txt" }
                if (src.writePart(name, EcgFileFormat.writePart(ep.toWaveformPart()))) {
                    savedParts += id
                }
            }
            val savedSeries = mutableSetOf<String>()
            for (id in _dirtySeries.value) {
                val es = editableSeries[id] ?: continue
                val name = es.fileName.ifEmpty { "$id.txt" }
                if (src.writeSeries(name, EcgFileFormat.writeSeries(es.toEcgSeries()))) {
                    savedSeries += id
                }
            }
            _dirtyParts.value = _dirtyParts.value - savedParts
            _dirtySeries.value = _dirtySeries.value - savedSeries
        }
    }

    /** Discard all in-memory edits, reverting to the on-disk snapshot. */
    fun discardEdits() {
        editableParts.clear()
        editableSeries.clear()
        partUndo.clear()
        seriesUndo.clear()
        _dirtyParts.value = emptySet()
        _dirtySeries.value = emptySet()
        // Force a fresh reload from disk.
        val repo = ecgRepository ?: return
        viewModelScope.launch {
            repo.setSource(repo.let {
                val ctx = appContext ?: return@launch
                FileEcgSource(File(ctx.filesDir, "unzipped_ecg"))
            })
            reload(repo)
        }
    }

    /**
     * Export the current dataset (with all saved edits applied to disk) as a
     * ZIP at the user-picked [destUri]. Saves any pending edits first so the
     * export reflects the latest in-memory state.
     */
    fun exportZip(context: Context, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            // Flush pending edits synchronously (best-effort).
            saveAll()
            val sourceDir = File(context.filesDir, "unzipped_ecg")
            val ok = ZipCompressor.zip(context, sourceDir, destUri)
            val name = getFileName(context, destUri) ?: "ecg_export.zip"
            _exportResult.value = if (ok) ExportResult.Success(name)
                else ExportResult.Failure("Export failed")
        }
    }

    /**
     * Toggle a flag on a block (SeriesPartRef) inside the series with
     * [seriesIdenty]. Pushes onto undo before mutation.
     */
    fun toggleBlockFlag(seriesIdenty: String, blockIndex: Int, flag: com.example.cardiosimulator.domain.BlockFlags) {
        mutateSeries(seriesIdenty) { es ->
            if (blockIndex in es.partRefs.indices) {
                val ref = es.partRefs[blockIndex]
                val newFlags = if (ref.flags.has(flag)) ref.flags - flag else ref.flags + flag
                es.partRefs[blockIndex] = ref.copy(flags = newFlags)
            }
        }
    }

    /** Reorder a block within the series. */
    fun reorderBlock(seriesIdenty: String, from: Int, to: Int) {
        mutateSeries(seriesIdenty) { es ->
            if (from in es.partRefs.indices && to in es.partRefs.indices) {
                val moved = es.partRefs.removeAt(from)
                es.partRefs.add(to, moved)
            }
        }
    }

    /**
     * Generate the four limb-derived leads (III, aVR, aVL, aVF) from the
     * I and II parts of [pathology], creating new parts and appending them
     * to the matching series. Skipped silently if I or II is missing.
     */
    fun generateDerivedLimbLeads(pathology: String) {
        val repo = ecgRepository ?: return
        val rhythms = repo.pathologies()
        val grp = rhythms.firstOrNull { it.pathology == pathology } ?: return
        val sIid = grp.seriesIdentityByLead[Lead.I] ?: return
        val sIIid = grp.seriesIdentityByLead[Lead.II] ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            val partsI = repo.assembleWaveformParts(sIid)
            val partsII = repo.assembleWaveformParts(sIIid)
            // Bake at part level so identical ordering and lengths are
            // preserved; the result lead is a new series whose parts mirror
            // partsI / partsII positionally.
            for (target in DerivedLeads.DerivableFromIandII) {
                val targetId = grp.seriesIdentityByLead[target]
                if (targetId == null) continue
                val targetEditable = editableSeries(targetId) ?: continue
                val newRefs = mutableListOf<SeriesPartRef>()
                for ((idx, refI) in targetEditable.partRefs.withIndex()) {
                    val partI = partsI.getOrNull(idx) ?: continue
                    val partII = partsII.getOrNull(idx) ?: continue
                    val derivedSamples = DerivedLeads.combineIII_aVR_aVL_aVF(
                        partI.samples.map { it.toFloat() },
                        partII.samples.map { it.toFloat() },
                        target,
                    )
                    val newPart = WaveformPart(
                        identy = "${target.name}_derived_${refI.partIdenty}",
                        title = "${target.name} ${partI.title.removePrefix(Lead.I.name).trim()}",
                        lead = target,
                        pathology = pathology,
                        amplitude = partI.amplitude,
                        duration = partI.duration,
                        samples = derivedSamples.map { it.toInt() + 1024 },
                        source = partI.source?.copy(lead = target, anchors = emptyList()),
                        aMax = partI.aMax,
                        aValue = partI.aValue,
                    )
                    editableParts[newPart.identy] = EditablePart.from(newPart, "${newPart.identy}.txt")
                    markPartDirty(newPart.identy)
                    newRefs += refI.copy(partIdenty = newPart.identy)
                }
                mutateSeries(targetId) { es ->
                    es.partRefs.clear()
                    es.partRefs.addAll(newRefs)
                }
            }
        }
    }

    /**
     * Generate V1/V3/V4/V5 by angular projection from V2 and V6. See
     * [DerivedLeads.combineV1_V3_V4_V5] for the math.
     */
    fun generateDerivedPrecordialLeads(pathology: String) {
        val repo = ecgRepository ?: return
        val rhythms = repo.pathologies()
        val grp = rhythms.firstOrNull { it.pathology == pathology } ?: return
        val sV2id = grp.seriesIdentityByLead[Lead.V2] ?: return
        val sV6id = grp.seriesIdentityByLead[Lead.V6] ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val partsV2 = repo.assembleWaveformParts(sV2id)
            val partsV6 = repo.assembleWaveformParts(sV6id)
            for (target in DerivedLeads.DerivableFromV2andV6) {
                val targetId = grp.seriesIdentityByLead[target] ?: continue
                val targetEditable = editableSeries(targetId) ?: continue
                val newRefs = mutableListOf<SeriesPartRef>()
                for ((idx, refV) in targetEditable.partRefs.withIndex()) {
                    val pV2 = partsV2.getOrNull(idx) ?: continue
                    val pV6 = partsV6.getOrNull(idx) ?: continue
                    val derived = DerivedLeads.combineV1_V3_V4_V5(
                        pV2.samples.map { it.toFloat() },
                        pV6.samples.map { it.toFloat() },
                        target,
                    )
                    val newPart = WaveformPart(
                        identy = "${target.name}_derived_${refV.partIdenty}",
                        title = "${target.name} ${pV2.title.removePrefix(Lead.V2.name).trim()}",
                        lead = target,
                        pathology = pathology,
                        amplitude = pV2.amplitude,
                        duration = pV2.duration,
                        samples = derived.map { it.toInt() + 1024 },
                        source = pV2.source?.copy(lead = target, anchors = emptyList()),
                        aMax = pV2.aMax,
                        aValue = pV2.aValue,
                    )
                    editableParts[newPart.identy] = EditablePart.from(newPart, "${newPart.identy}.txt")
                    markPartDirty(newPart.identy)
                    newRefs += refV.copy(partIdenty = newPart.identy)
                }
                mutateSeries(targetId) { es ->
                    es.partRefs.clear()
                    es.partRefs.addAll(newRefs)
                }
            }
        }
    }

    /**
     * Push edited data over an existing TCP connection. Re-zips the unzipped
     * dataset to a cache file first so stale ZIP-on-disk content is not
     * transmitted. Manual: not called automatically on connect.
     */
    fun uploadEditedData() {
        val ctx = appContext ?: return
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return
        viewModelScope.launch(Dispatchers.IO) {
            saveAll()
            val sourceDir = File(ctx.filesDir, "unzipped_ecg")
            val tmp = ZipCompressor.zipToCache(ctx, sourceDir, "edited_ecg.zip") ?: return@launch
            _isUploading.value = true
            try {
                val uploadMsg = TcpMessage.UploadMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    filename = tmp.name,
                    size = tmp.length(),
                )
                val header = TcpProtocol.encode(uploadMsg) + "\n"
                val outputStream = socket.getOutputStream()
                outputStream.write(header.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                tmp.inputStream().use { ins ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (ins.read(buf).also { read = it } != -1) outputStream.write(buf, 0, read)
                }
                outputStream.flush()
            } catch (_: Exception) {
                // best-effort; surface via lastAck if the server replies
            } finally {
                _isUploading.value = false
            }
        }
    }
}
