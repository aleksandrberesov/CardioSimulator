package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.PathologyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-mode view-model exposing the manifest's pathology index and the
 * baseline-zeroed waveforms for the currently selected pathology. The
 * editor variant reads through the same [PathologyRepository] but mutates
 * its in-memory copy via [AppViewModel].
 */
class RhythmViewModel(
    val repository: PathologyRepository,
    private val mode: OperatingMode,
    private val prefs: DataSourcePrefs? = null,
) : ViewModel() {

    private val _rhythms = MutableStateFlow<List<PathologyEntry>>(emptyList())
    val rhythms: StateFlow<List<PathologyEntry>> = _rhythms.asStateFlow()

    private val _selectedRhythm = MutableStateFlow<PathologyEntry?>(null)
    val selectedRhythm: StateFlow<PathologyEntry?> = _selectedRhythm.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<Lead, Points>>(emptyMap())
    val waveforms: StateFlow<Map<Lead, Points>> = _waveforms.asStateFlow()

    private val _comparisonWaveforms = MutableStateFlow<Map<Int, Points>>(emptyMap())
    val comparisonWaveforms: StateFlow<Map<Int, Points>> = _comparisonWaveforms.asStateFlow()

    private val _significantPoints = MutableStateFlow<List<com.example.cardiosimulator.domain.SignificantPoint>>(emptyList())
    val significantPoints: StateFlow<List<com.example.cardiosimulator.domain.SignificantPoint>> = _significantPoints.asStateFlow()

    init {
        viewModelScope.launch {
            repository.manifestFlow.collectLatest { manifest ->
                if (manifest != null) {
                    val entries = repository.pathologies()
                    
                    // Enrichment: If manifest entries are missing Russian names, try to 
                    // peek-read them from the .dat files.
                    val missingNames = entries.filter { it.nameRu.isNullOrBlank() }
                    if (missingNames.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            val enriched = entries.map { entry ->
                                if (entry.nameRu.isNullOrBlank()) {
                                    val file = repository.readPathology(entry.id)
                                    if (file?.nameRu != null) {
                                        entry.copy(nameRu = file.nameRu)
                                    } else entry
                                } else entry
                            }
                            withContext(Dispatchers.Main) {
                                _rhythms.value = enriched
                            }
                        }
                    } else {
                        _rhythms.value = entries
                    }

                    // Update selected rhythm if it's in the list
                    _selectedRhythm.value?.let { current ->
                        val updated = _rhythms.value.find { it.id == current.id }
                        if (updated != null) {
                            _selectedRhythm.value = updated
                        }
                    }
                }
            }
        }
    }

    fun loadManifest() {
        viewModelScope.launch {
            repository.loadManifest()
            
            // Try to restore last selected rhythm
            val lastId = prefs?.lastRhythmId(mode.name)?.first()
            if (lastId != null && _selectedRhythm.value == null) {
                selectRhythm(lastId, persist = false)
            } else {
                _selectedRhythm.value?.let { current -> selectRhythm(current.id) }
            }
        }
    }

    fun selectRhythm(id: String, persist: Boolean = true) {
        val entry = _rhythms.value.firstOrNull { it.id == id } ?: return
        _selectedRhythm.value = entry
        
        if (persist) {
            viewModelScope.launch {
                prefs?.setLastRhythmId(mode.name, id)
            }
        }

        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { repository.readPathology(id) }
            _significantPoints.value = file?.significantPoints ?: emptyList()

            val leadOrder = repository.manifest()?.leadOrder ?: Lead.entries
            val map = withContext(Dispatchers.IO) {
                leadOrder.mapNotNull { lead ->
                    repository.leadWaveform(id, lead)?.let { lead to it }
                }.toMap()
            }
            _waveforms.value = map
        }
    }

    fun refresh() {
        _selectedRhythm.value?.let { selectRhythm(it.id) }
    }

    fun loadComparisonWaveform(paneIndex: Int, pathologyId: String, lead: Lead) {
        viewModelScope.launch {
            val points = withContext(Dispatchers.IO) {
                repository.leadWaveform(pathologyId, lead)
            }
            if (points != null) {
                _comparisonWaveforms.value = _comparisonWaveforms.value + (paneIndex to points)
            }
        }
    }
}
