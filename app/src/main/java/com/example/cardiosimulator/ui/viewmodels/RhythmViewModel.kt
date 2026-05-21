package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _rhythms = MutableStateFlow<List<PathologyEntry>>(emptyList())
    val rhythms: StateFlow<List<PathologyEntry>> = _rhythms.asStateFlow()

    private val _selectedRhythm = MutableStateFlow<PathologyEntry?>(null)
    val selectedRhythm: StateFlow<PathologyEntry?> = _selectedRhythm.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<Lead, Points>>(emptyMap())
    val waveforms: StateFlow<Map<Lead, Points>> = _waveforms.asStateFlow()

    fun loadManifest() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { repository.pathologies() }
            _rhythms.value = entries
            _selectedRhythm.value?.let { current -> selectRhythm(current.id) }
        }
    }

    fun selectRhythm(id: String) {
        val entry = _rhythms.value.firstOrNull { it.id == id } ?: return
        _selectedRhythm.value = entry
        viewModelScope.launch {
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
}
