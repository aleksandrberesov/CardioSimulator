package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.data.Points
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(
    private val repository: Points
) : ViewModel() {
    private val _chartData = MutableStateFlow(Points(emptyList()))
    val chartData: StateFlow<Points> = _chartData
    val points = repository

    init {
        loadData()
    }

    private fun loadData() {
        _chartData.value = repository
    }
}



