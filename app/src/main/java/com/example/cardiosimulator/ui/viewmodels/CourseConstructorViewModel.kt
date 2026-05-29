package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.domain.OperatingMode

class CourseConstructorViewModel(
    val repository: CourseRepository,
    val mode: OperatingMode,
    val prefs: DataSourcePrefs?
) : ViewModel() {
    // Phase 3 stub
}
