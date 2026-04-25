package com.example.cardiosimulator.ui.viewmodels

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.EcgRepository
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

import com.example.cardiosimulator.domain.Language

class AppViewModel(
    private val appState: AppStateModel,
    private val ecgRepository: EcgRepository? = null,
) : ViewModel() {
    val operatingModes = appState.operatingModes

    private val _selectedLanguage = MutableStateFlow(currentSystemLanguage(appState.selectedLanguage))
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _selectedOperatingMode = MutableStateFlow(appState.selectedOperatingMode)
    val selectedOperatingMode: StateFlow<OperatingModeModel> = _selectedOperatingMode

    private val _rhythms = MutableStateFlow<List<PathologyGroup>>(emptyList())
    val rhythms: StateFlow<List<PathologyGroup>> = _rhythms.asStateFlow()

    private val _selectedRhythm = MutableStateFlow<PathologyGroup?>(null)
    val selectedRhythm: StateFlow<PathologyGroup?> = _selectedRhythm.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<Lead, Points>>(emptyMap())
    val waveforms: StateFlow<Map<Lead, Points>> = _waveforms.asStateFlow()

    init {
        ecgRepository?.let { repo ->
            viewModelScope.launch {
                val list = withContext(Dispatchers.IO) {
                    repo.load()
                    repo.pathologies()
                }
                _rhythms.value = list
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
