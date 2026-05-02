package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.MonitorModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.data.AdcScale
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

    fun setSpeed(speed: Int) {
        _monitorMode.update { 
            val horizontalScale = if (speed == 50) 2.0f else 1.0f
            it.copy(
                speed = speed,
                adcScale = it.adcScale.copy(horizontalPixelsPerSample = horizontalScale)
            )
        }
    }

    fun setScale(scale: Float) {
        _monitorMode.update { 
            val verticalScale = 0.5f * scale
            it.copy(
                scale = scale,
                adcScale = it.adcScale.copy(verticalPixelsPerUnit = verticalScale)
            )
        }
    }

    fun setAdcScale(adcScale: AdcScale) {
        _monitorMode.update { it.copy(adcScale = adcScale) }
    }
}
