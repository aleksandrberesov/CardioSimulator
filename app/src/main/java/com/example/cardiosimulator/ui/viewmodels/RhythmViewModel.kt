package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.EcgRepository
import com.example.cardiosimulator.data.PathologyGroup
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.EcgSeries
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.WaveformPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RhythmViewModel(private val ecgRepository: EcgRepository) : ViewModel() {
    private val _rhythms = MutableStateFlow<List<PathologyGroup>>(emptyList())
    val rhythms: StateFlow<List<PathologyGroup>> = _rhythms.asStateFlow()

    private val _selectedRhythm = MutableStateFlow<PathologyGroup?>(null)
    val selectedRhythm: StateFlow<PathologyGroup?> = _selectedRhythm.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<Lead, Points>>(emptyMap())
    val waveforms: StateFlow<Map<Lead, Points>> = _waveforms.asStateFlow()

    private val _allSeries = MutableStateFlow<List<EcgSeries>>(emptyList())
    val allSeries: StateFlow<List<EcgSeries>> = _allSeries.asStateFlow()

    private val _allParts = MutableStateFlow<List<WaveformPart>>(emptyList())
    val allParts: StateFlow<List<WaveformPart>> = _allParts.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { ecgRepository.pathologies() }
            val series = withContext(Dispatchers.IO) { ecgRepository.allSeries() }
            val parts = withContext(Dispatchers.IO) { ecgRepository.allParts() }
            _rhythms.value = list
            _allSeries.value = series
            _allParts.value = parts
            
            // If we already had a selected rhythm, we might need to refresh its waveforms
            _selectedRhythm.value?.let { current ->
                selectRhythm(current.pathology)
            }
        }
    }

    fun selectRhythm(pathology: String) {
        val group = _rhythms.value.firstOrNull { it.pathology == pathology } ?: return
        _selectedRhythm.value = group
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                group.seriesIdentityByLead.mapValues { (_, identy) ->
                    Points(ecgRepository.assembleWaveform(identy))
                }
            }
            _waveforms.value = map
        }
    }

    fun updateWaveform(lead: Lead, points: Points) {
        val current = _waveforms.value.toMutableMap()
        current[lead] = points
        _waveforms.value = current
    }
}
