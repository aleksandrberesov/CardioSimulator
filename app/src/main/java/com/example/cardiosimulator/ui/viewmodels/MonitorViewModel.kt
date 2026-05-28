package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.MonitorModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.domain.OperatingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MonitorViewModel(
    val mode: OperatingMode,
    private val prefs: DataSourcePrefs? = null
) : ViewModel() {
    private val _monitorMode = MutableStateFlow(MonitorModeModel())
    val monitorMode: StateFlow<MonitorModeModel> = _monitorMode.asStateFlow()

    init {
        viewModelScope.launch {
            val modeName = mode.name
            prefs?.gridScheme(modeName)?.first()?.let { schemeName ->
                try {
                    val scheme = GridScheme.valueOf(schemeName)
                    setGridScheme(scheme, persist = false)
                } catch (_: Exception) {}
            }
            prefs?.monitorSpeed(modeName)?.first()?.let { speed ->
                setSpeed(speed, persist = false)
            }
            prefs?.monitorScale(modeName)?.first()?.let { scale ->
                setScale(scale, persist = false)
            }
            prefs?.monitorDisplayScale(modeName)?.first()?.let { displayScale ->
                setDisplayScale(displayScale, persist = false)
            }
            prefs?.monitorSeriesCount(modeName)?.first()?.let { count ->
                setSeriesCount(count, persist = false)
            }
            prefs?.monitorSeriesScheme(modeName)?.first()?.let { schemeName ->
                try {
                    val scheme = SeriesScheme.valueOf(schemeName)
                    setSeriesScheme(scheme, persist = false)
                } catch (_: Exception) {}
            }
        }
    }

    fun setSeriesCount(count: Int, persist: Boolean = true) {
        _monitorMode.update { it.copy(count = count) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorSeriesCount(mode.name, count)
            }
        }
    }

    fun setSeriesScheme(scheme: SeriesScheme, persist: Boolean = true) {
        _monitorMode.update { it.copy(seriesScheme = scheme) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorSeriesScheme(mode.name, scheme.name)
            }
        }
    }

    fun setGridScheme(scheme: GridScheme, persist: Boolean = true) {
        _monitorMode.update { it.copy(gridScheme = scheme) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setGridScheme(mode.name, scheme.name)
            }
        }
    }

    fun setSpeed(speed: Float, persist: Boolean = true) {
        _monitorMode.update { it.copy(speed = speed) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorSpeed(mode.name, speed)
            }
        }
    }

    fun setScale(scale: Float, persist: Boolean = true) {
        _monitorMode.update { it.copy(scale = scale) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorScale(mode.name, scale)
            }
        }
    }

    fun setCalibration(calibration: EcgCalibration) {
        _monitorMode.update { it.copy(calibration = calibration) }
    }

    fun setDisplayScale(displayScale: Float, persist: Boolean = true) {
        _monitorMode.update { it.copy(displayScale = displayScale) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorDisplayScale(mode.name, displayScale)
            }
        }
    }


    fun setIsRunning(isRunning: Boolean) {
        _monitorMode.update { it.copy(isRunning = isRunning) }
    }
}
