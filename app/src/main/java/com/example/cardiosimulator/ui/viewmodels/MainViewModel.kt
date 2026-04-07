package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.OperatingModeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(
    private val appState: AppStateModel,
    private val repository: Points
) : ViewModel() {
    private val _chartData = MutableStateFlow(Points(emptyList()))
    //val chartData: StateFlow<Points> = _chartData
    val points = repository
    val operatingModes = appState.operatingModes
    private val _selectedOperatingMode = MutableStateFlow(appState.selectedOperatingMode)
    val selectedOperatingMode: StateFlow<OperatingModeModel> = _selectedOperatingMode

    init {
        loadData()
    }

    private fun loadData() {
        _chartData.value = repository
    }

    fun updateOperatingMode(mode: OperatingModeModel) {
        appState.updateMode(mode)
        _selectedOperatingMode.value = mode
    }
}
