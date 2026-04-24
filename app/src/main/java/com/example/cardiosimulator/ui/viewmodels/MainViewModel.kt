package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.EcgRepository
import com.example.cardiosimulator.data.PathologyGroup
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.OperatingModeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val appState: AppStateModel,
    private val repository: Points,
    private val ecgRepository: EcgRepository? = null,
) : ViewModel() {
    val points = repository
    val operatingModes = appState.operatingModes

    private val _selectedOperatingMode = MutableStateFlow(appState.selectedOperatingMode)
    val selectedOperatingMode: StateFlow<OperatingModeModel> = _selectedOperatingMode

    private val _rhythms = MutableStateFlow<List<PathologyGroup>>(emptyList())
    val rhythms: StateFlow<List<PathologyGroup>> = _rhythms.asStateFlow()

    private val _selectedRhythm = MutableStateFlow<PathologyGroup?>(null)
    val selectedRhythm: StateFlow<PathologyGroup?> = _selectedRhythm.asStateFlow()

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

    fun updateOperatingMode(mode: OperatingModeModel) {
        appState.updateMode(mode)
        _selectedOperatingMode.value = mode
    }

    fun selectRhythm(pathology: String) {
        _selectedRhythm.value = _rhythms.value.firstOrNull { it.pathology == pathology }
    }
}
