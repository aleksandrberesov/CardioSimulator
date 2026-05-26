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
            prefs?.monitorSpeed?.first()?.let { speed ->
                setSpeed(speed, persist = false)
            }
            prefs?.monitorScale?.first()?.let { scale ->
                setScale(scale, persist = false)
            }
            prefs?.monitorDisplayScale?.first()?.let { displayScale ->
                setDisplayScale(displayScale, persist = false)
            }
            prefs?.monitorSeriesCount?.first()?.let { count ->
                setSeriesCount(count, persist = false)
            }
            prefs?.monitorSeriesScheme?.first()?.let { schemeName ->
                try {
                    val scheme = SeriesScheme.valueOf(schemeName)
                    setSeriesScheme(scheme, persist = false)
                } catch (_: Exception) {}
            }
            prefs?.monitorBlankSheet?.first()?.let { isBlank ->
                setBlankSheet(isBlank, persist = false)
            }
        }
    }

    fun setSeriesCount(count: Int, persist: Boolean = true) {
        _monitorMode.update { it.copy(count = count) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorSeriesCount(count)
            }
        }
    }

    fun setSeriesScheme(scheme: SeriesScheme, persist: Boolean = true) {
        _monitorMode.update { it.copy(seriesScheme = scheme) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorSeriesScheme(scheme.name)
            }
        }
    }

    fun setGridScheme(scheme: GridScheme, persist: Boolean = true) {
        _monitorMode.update { it.copy(gridScheme = scheme) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setGridScheme(scheme.name)
            }
        }
    }

    fun setSpeed(speed: Int, persist: Boolean = true) {
        _monitorMode.update { it.copy(speed = speed) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorSpeed(speed)
            }
        }
    }

    fun setScale(scale: Float, persist: Boolean = true) {
        _monitorMode.update { it.copy(scale = scale) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorScale(scale)
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
                prefs?.setMonitorDisplayScale(displayScale)
            }
        }
    }

    fun setIsRunning(isRunning: Boolean) {
        _monitorMode.update { it.copy(isRunning = isRunning) }
    }

    fun setBlankSheet(isBlank: Boolean, persist: Boolean = true) {
        _monitorMode.update { it.copy(isBlankSheet = isBlank) }
        if (persist) {
            viewModelScope.launch {
                prefs?.setMonitorBlankSheet(isBlank)
            }
        }
    }
}
