package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.domain.Lead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EditorViewModel : ViewModel() {
    private val _focusedLead = MutableStateFlow<Lead?>(Lead.II)
    val focusedLead: StateFlow<Lead?> = _focusedLead.asStateFlow()

    private val _selectedPartIndex = MutableStateFlow<Int?>(null)
    val selectedPartIndex: StateFlow<Int?> = _selectedPartIndex.asStateFlow()

    private val _selectedAnchorIndex = MutableStateFlow<Int?>(null)
    val selectedAnchorIndex: StateFlow<Int?> = _selectedAnchorIndex.asStateFlow()

    private val _partFilter = MutableStateFlow("")
    val partFilter: StateFlow<String> = _partFilter.asStateFlow()

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val _showPartsDropdown = MutableStateFlow(false)
    val showPartsDropdown: StateFlow<Boolean> = _showPartsDropdown.asStateFlow()

    private val _showSeriesDropdown = MutableStateFlow(false)
    val showSeriesDropdown: StateFlow<Boolean> = _showSeriesDropdown.asStateFlow()

    fun setFocusedLead(lead: Lead?) {
        _focusedLead.value = lead
        _selectedPartIndex.value = null
        _selectedAnchorIndex.value = null
    }

    fun setSelectedPartIndex(index: Int?) {
        _selectedPartIndex.value = index
        _selectedAnchorIndex.value = null
    }

    fun setSelectedAnchorIndex(index: Int?) {
        _selectedAnchorIndex.value = index
    }

    fun setPartFilter(filter: String) {
        _partFilter.value = filter
    }

    fun setShowSaveDialog(show: Boolean) {
        _showSaveDialog.value = show
    }

    fun setShowPartsDropdown(show: Boolean) {
        _showPartsDropdown.value = show
    }

    fun setShowSeriesDropdown(show: Boolean) {
        _showSeriesDropdown.value = show
    }
}
