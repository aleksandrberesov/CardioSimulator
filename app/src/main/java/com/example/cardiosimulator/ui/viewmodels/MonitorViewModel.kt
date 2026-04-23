package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.MonitorModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MonitorViewModel : ViewModel() {
    private val _monitorMode = MutableStateFlow(MonitorModeModel())
    val monitorMode: StateFlow<MonitorModeModel> = _monitorMode.asStateFlow()

    fun setSeriesCount(count: Int) {
        _monitorMode.update { it.copy(count = count) }
    }

    fun setSeriesScheme(scheme: SeriesScheme) {
        _monitorMode.update { it.copy(seriesScheme = scheme) }
    }

    fun setGridScheme(scheme: GridScheme) {
        _monitorMode.update { it.copy(gridScheme = scheme) }
    }

    fun toggleGridScheme() {
        _monitorMode.update { 
            val newScheme = if (it.gridScheme == GridScheme.Pink) GridScheme.BlueGray else GridScheme.Pink
            it.copy(gridScheme = newScheme)
        }
    }
}
