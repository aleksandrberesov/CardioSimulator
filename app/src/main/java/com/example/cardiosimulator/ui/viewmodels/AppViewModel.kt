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
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket
import java.io.IOException

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
    private val prefs: DataSourcePrefs? = null,
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
                val saved = p.treeUri.first()
                if (saved != null) {
                    loadFromSaf(ctx, saved)
                    // If we successfully loaded saved data, don't force the user
                    // to see the summary screen again.
                    if (_dataState.value is DataState.Ready) {
                        _isDataConfirmed.value = true
                    }
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

    fun updateLanguage(language: Language) {
        if (_selectedLanguage.value == language) return
        appState.updateLanguage(language)
        _selectedLanguage.value = language
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
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
            _tcpConnectionState.value = TcpConnectionState.Connecting
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 5000)
                tcpSocket = socket
                _tcpConnectionState.value = TcpConnectionState.Connected
            } catch (e: IOException) {
                _tcpConnectionState.value = TcpConnectionState.Error(e.message ?: "Unknown error")
                delay(5000)
                if (_tcpConnectionState.value is TcpConnectionState.Error) {
                    _tcpConnectionState.value = TcpConnectionState.Disconnected
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
