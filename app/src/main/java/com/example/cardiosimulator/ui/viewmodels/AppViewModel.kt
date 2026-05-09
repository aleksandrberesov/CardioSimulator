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
import com.example.cardiosimulator.data.ZipDecompressor
import com.example.cardiosimulator.domain.AppStateModel
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
    private val ecgRepository: EcgRepository? = null,
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

    private val _rhythms = MutableStateFlow<List<PathologyGroup>>(emptyList())
    val rhythms: StateFlow<List<PathologyGroup>> = _rhythms.asStateFlow()

    private val _allSeries = MutableStateFlow<List<EcgSeries>>(emptyList())
    val allSeries: StateFlow<List<EcgSeries>> = _allSeries.asStateFlow()

    private val _allParts = MutableStateFlow<List<WaveformPart>>(emptyList())
    val allParts: StateFlow<List<WaveformPart>> = _allParts.asStateFlow()

    private val _selectedRhythm = MutableStateFlow<PathologyGroup?>(null)
    val selectedRhythm: StateFlow<PathologyGroup?> = _selectedRhythm.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<Lead, Points>>(emptyMap())
    val waveforms: StateFlow<Map<Lead, Points>> = _waveforms.asStateFlow()

    private val _dataState = MutableStateFlow<DataState>(DataState.NotConfigured)
    val dataState: StateFlow<DataState> = _dataState.asStateFlow()

    private val _isDataConfirmed = MutableStateFlow(false)
    val isDataConfirmed: StateFlow<Boolean> = _isDataConfirmed.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _lastAck = MutableStateFlow<TcpMessage.AckMessage?>(null)
    val lastAck: StateFlow<TcpMessage.AckMessage?> = _lastAck.asStateFlow()

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
                _dataState.value = DataState.Ready(_allSeries.value.size, _allParts.value.size)
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
                    
                    // Auto-upload the current data if available
                    val currentUri = prefs?.treeUri?.first()
                    val ctx = appContext
                    if (currentUri != null && ctx != null) {
                        uploadZipFile(ctx, currentUri)
                    }

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

    fun sendStartCommand(pathology: String? = null) {
        val socket = tcpSocket ?: return
        if (_tcpConnectionState.value !is TcpConnectionState.Connected) return
        
        val pName = pathology ?: _selectedRhythm.value?.pathology

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val paramsMap = mutableMapOf<String, String>()
                if (pName != null) {
                    paramsMap["pathology"] = pName
                }
                // Also include the display title if we have it
                _selectedRhythm.value?.displayTitle?.let { 
                    paramsMap["name"] = it 
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

    fun selectRhythm(pathology: String) {
        val group = _rhythms.value.firstOrNull { it.pathology == pathology } ?: return
        _selectedRhythm.value = group
        val repo = ecgRepository ?: return
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                group.seriesIdentityByLead.mapValues { (_, identy) ->
                    Points(repo.assembleWaveform(identy))
                }
            }
            _waveforms.value = map
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
            uploadZipFile(context, uri)
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
        val rhythms = repo.pathologies()
        val series = repo.allSeries()
        val parts = repo.allParts()
        _rhythms.value = rhythms
        _allSeries.value = series
        _allParts.value = parts
        return if (series.isEmpty() && parts.isEmpty()) {
            false
        } else {
            _dataState.value = DataState.Ready(series.size, parts.size)
            true
        }
    }
}
