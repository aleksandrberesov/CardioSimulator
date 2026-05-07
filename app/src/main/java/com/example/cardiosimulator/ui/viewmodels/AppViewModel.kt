package com.example.cardiosimulator.ui.viewmodels

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.EcgExport
import com.example.cardiosimulator.data.EcgRepository
import com.example.cardiosimulator.data.FtpClient
import com.example.cardiosimulator.data.PathologyGroup
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingModeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.cardiosimulator.domain.EcgSeries
import com.example.cardiosimulator.domain.WaveformPart
import com.example.cardiosimulator.domain.Language
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class FtpSendStatus {
    object Idle : FtpSendStatus()
    object Sending : FtpSendStatus()
    data class Success(val remotePath: String, val bytes: Int) : FtpSendStatus()
    data class Error(val message: String) : FtpSendStatus()
}

class AppViewModel(
    private val appState: AppStateModel,
    private val ecgRepository: EcgRepository? = null,
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

    private val _ftpPort = MutableStateFlow(appState.ftpPort)
    val ftpPort: StateFlow<Int> = _ftpPort.asStateFlow()

    private val _ftpUser = MutableStateFlow(appState.ftpUser)
    val ftpUser: StateFlow<String> = _ftpUser.asStateFlow()

    private val _ftpPassword = MutableStateFlow(appState.ftpPassword)
    val ftpPassword: StateFlow<String> = _ftpPassword.asStateFlow()

    private val _ftpRemotePath = MutableStateFlow(appState.ftpRemotePath)
    val ftpRemotePath: StateFlow<String> = _ftpRemotePath.asStateFlow()

    private val _ftpStatus = MutableStateFlow<FtpSendStatus>(FtpSendStatus.Idle)
    val ftpStatus: StateFlow<FtpSendStatus> = _ftpStatus.asStateFlow()

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

    init {
        ecgRepository?.let { repo ->
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    repo.load()
                }
                _rhythms.value = repo.pathologies()
                _allSeries.value = repo.allSeries()
                _allParts.value = repo.allParts()
            }
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

    fun updateFtpCredentials(port: Int, user: String, password: String, remotePath: String) {
        appState.updateFtpCredentials(port, user, password, remotePath)
        _ftpPort.value = port
        _ftpUser.value = user
        _ftpPassword.value = password
        _ftpRemotePath.value = remotePath
    }

    fun resetFtpStatus() {
        _ftpStatus.value = FtpSendStatus.Idle
    }

    fun sendModelViaFtp() {
        if (_ftpStatus.value is FtpSendStatus.Sending) return
        val rhythm = _selectedRhythm.value
        val waveforms = _waveforms.value
        if (rhythm == null || waveforms.isEmpty()) {
            _ftpStatus.value = FtpSendStatus.Error("no_rhythm")
            return
        }
        val host = _tcpIp.value
        val port = _ftpPort.value
        val user = _ftpUser.value
        val password = _ftpPassword.value
        val remotePath = _ftpRemotePath.value
        if (host.isBlank() || port <= 0 || remotePath.isBlank()) {
            _ftpStatus.value = FtpSendStatus.Error("invalid_settings")
            return
        }

        _ftpStatus.value = FtpSendStatus.Sending
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .format(Date())
                    val payload = EcgExport.toCsv(
                        rhythm = rhythm.displayTitle,
                        timestampIso = timestamp,
                        waveforms = waveforms,
                    )
                    FtpClient(
                        host = host,
                        port = port,
                        username = user,
                        password = password,
                    ).upload(remotePath, payload)
                    payload.size
                }
            }
            _ftpStatus.value = result.fold(
                onSuccess = { bytes -> FtpSendStatus.Success(remotePath, bytes) },
                onFailure = { t -> FtpSendStatus.Error(t.message ?: t::class.java.simpleName) },
            )
        }
    }

    fun selectRhythm(pathology: String) {
        val group = _rhythms.value.firstOrNull { it.pathology == pathology } ?: return
        _selectedRhythm.value = group
        val repo = ecgRepository ?: return
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                group.seriesIdentyByLead.mapValues { (_, identy) ->
                    Points(repo.assembleWaveform(identy))
                }
            }
            _waveforms.value = map
        }
    }
}
