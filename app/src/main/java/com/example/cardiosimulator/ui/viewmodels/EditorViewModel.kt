package com.example.cardiosimulator.ui.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages editing a single [PathologyFile].
 * Works with raw ADC samples directly.
 */
class EditorViewModel(
    private val repository: PathologyRepository,
    private val prefs: DataSourcePrefs? = null
) : ViewModel() {

    init {
        viewModelScope.launch {
            prefs?.lastEditorRhythmId?.first()?.let { id ->
                selectPathology(id, persist = false)
            }
        }
    }

    private val _targetFile = mutableStateOf<PathologyFile?>(null)
    val targetFile: State<PathologyFile?> = _targetFile

    private val _focusedLead = MutableStateFlow(Lead.II)
    val focusedLead: StateFlow<Lead> = _focusedLead.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _dirtyLeads = MutableStateFlow<Set<Lead>>(emptySet())
    val dirtyLeads: StateFlow<Set<Lead>> = _dirtyLeads.asStateFlow()

    private val _isMetadataDirty = MutableStateFlow(false)
    val isMetadataDirty: StateFlow<Boolean> = _isMetadataDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun selectPathology(id: String, persist: Boolean = true) {
        viewModelScope.launch {
            val file = repository.readPathology(id)
            _targetFile.value = file
            _dirtyLeads.value = emptySet()
            _isMetadataDirty.value = false
            _focusedLead.value = Lead.II
            _selectedIndex.value = 0
            if (persist) {
                prefs?.setLastEditorRhythmId(id)
            }
        }
    }

    fun selectLead(lead: Lead) {
        _focusedLead.value = lead
        _selectedIndex.value = 0
    }

    fun selectIndex(index: Int) {
        val stream = _targetFile.value?.leads?.get(_focusedLead.value) ?: return
        if (index in stream.samples.indices) {
            _selectedIndex.value = index
        }
    }

    fun selectNext() {
        val stream = _targetFile.value?.leads?.get(_focusedLead.value) ?: return
        if (_selectedIndex.value < stream.samples.size - 1) {
            _selectedIndex.value++
        }
    }

    fun selectPrevious() {
        if (_selectedIndex.value > 0) {
            _selectedIndex.value--
        }
    }

    fun moveSelectedUp() {
        val lead = _focusedLead.value
        val stream = _targetFile.value?.leads?.get(lead) ?: return
        val index = _selectedIndex.value
        if (index in stream.samples.indices) {
            setSample(lead, index, stream.samples[index] + 1)
        }
    }

    fun moveSelectedDown() {
        val lead = _focusedLead.value
        val stream = _targetFile.value?.leads?.get(lead) ?: return
        val index = _selectedIndex.value
        if (index in stream.samples.indices) {
            setSample(lead, index, stream.samples[index] - 1)
        }
    }

    fun setSample(lead: Lead, index: Int, adcValue: Int) {
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        if (index !in stream.samples.indices) return
        if (stream.samples[index] == adcValue) return

        // Update the sample in-place (or copy-on-write if preferred for Compose)
        // Since it's a MutableState holding the whole file, we need a new file instance
        // or a way to notify Compose.
        val newSamples = stream.samples.copyOf()
        newSamples[index] = adcValue
        
        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = stream.copy(samples = newSamples)
        
        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead
    }

    fun revertLead(lead: Lead) {
        val id = _targetFile.value?.id ?: return
        viewModelScope.launch {
            val originalFile = repository.readPathology(id) ?: return@launch
            val originalStream = originalFile.leads[lead] ?: return@launch
            
            val currentFile = _targetFile.value ?: return@launch
            val newLeads = currentFile.leads.toMutableMap()
            newLeads[lead] = originalStream
            
            _targetFile.value = currentFile.copy(leads = newLeads)
            _dirtyLeads.value -= lead
        }
    }

    fun rename(newTitle: String, language: com.example.cardiosimulator.domain.Language) {
        val currentFile = _targetFile.value ?: return
        val updatedFile = if (language == com.example.cardiosimulator.domain.Language.RU) {
            currentFile.copy(nameRu = newTitle)
        } else {
            currentFile.copy(titleEn = newTitle)
        }
        if (updatedFile != currentFile) {
            _targetFile.value = updatedFile
            _isMetadataDirty.value = true
        }
    }

    fun save() {
        val file = _targetFile.value ?: return
        if (_dirtyLeads.value.isEmpty() && !_isMetadataDirty.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Phase 4: writePathology will be added to repository
                repository.writePathology(file)
                _dirtyLeads.value = emptySet()
                _isMetadataDirty.value = false
            } finally {
                _isSaving.value = false
            }
        }
    }
}
