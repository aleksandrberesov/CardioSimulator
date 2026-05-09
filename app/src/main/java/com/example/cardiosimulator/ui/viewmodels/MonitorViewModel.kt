package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.MonitorModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MonitorViewModel(private val prefs: DataSourcePrefs? = null) : ViewModel() {
    private val _monitorMode = MutableStateFlow(MonitorModeModel())
    val monitorMode: StateFlow<MonitorModeModel> = _monitorMode.asStateFlow()

    init {
        viewModelScope.launch {
            prefs?.gridScheme?.first()?.let { schemeName ->
                try {
                    val scheme = GridScheme.valueOf(schemeName)
                    setGridScheme(scheme, persist = false)
                } catch (_: Exception) {}
            }
        }
    }

    fun setSeriesCount(count: Int) {
        _monitorMode.update { it.copy(count = count) }
    }

    fun setSeriesScheme(scheme: SeriesScheme) {
        _monitorMode.update { it.copy(seriesScheme = scheme) }
    }

    fun setGridScheme(scheme: GridScheme, persist: Boolean = true) {
        _monitorMode.update { it.copy(gridScheme = scheme) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setGridScheme(scheme.name)
            }
        }
    }

    fun setSpeed(speed: Int) {
        _monitorMode.update { it.copy(speed = speed) }
    }

    fun setScale(scale: Float) {
        _monitorMode.update { it.copy(scale = scale) }
    }

    fun setCalibration(calibration: EcgCalibration) {
        _monitorMode.update { it.copy(calibration = calibration) }
    }

    fun setDisplayScale(displayScale: Float) {
        _monitorMode.update { it.copy(displayScale = displayScale) }
    }

    fun setIsRunning(isRunning: Boolean) {
        _monitorMode.update { it.copy(isRunning = isRunning) }
    }
}
