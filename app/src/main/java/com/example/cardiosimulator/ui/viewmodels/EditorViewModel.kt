package com.example.cardiosimulator.ui.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.PathologyFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages editing a single [PathologyFile].
 * Works with raw ADC samples directly.
 */
class EditorViewModel(
    private val repository: PathologyRepository
) : ViewModel() {

    private val _targetFile = mutableStateOf<PathologyFile?>(null)
    val targetFile: State<PathologyFile?> = _targetFile

    private val _focusedLead = MutableStateFlow(Lead.II)
    val focusedLead: StateFlow<Lead> = _focusedLead.asStateFlow()

    private val _dirtyLeads = MutableStateFlow<Set<Lead>>(emptySet())
    val dirtyLeads: StateFlow<Set<Lead>> = _dirtyLeads.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun selectPathology(id: String) {
        viewModelScope.launch {
            val file = repository.readPathology(id)
            _targetFile.value = file
            _dirtyLeads.value = emptySet()
            _focusedLead.value = Lead.II
        }
    }

    fun selectLead(lead: Lead) {
        _focusedLead.value = lead
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

    fun save() {
        val file = _targetFile.value ?: return
        if (_dirtyLeads.value.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Phase 4: writePathology will be added to repository
                repository.writePathology(file)
                _dirtyLeads.value = emptySet()
            } finally {
                _isSaving.value = false
            }
        }
    }
}
