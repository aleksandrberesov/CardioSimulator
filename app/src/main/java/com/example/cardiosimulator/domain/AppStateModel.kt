package com.example.cardiosimulator.domain

data class AppStateModel(
    val initialOperatingMode: OperatingModeModel,
    val operatingModes: List<OperatingModeModel>
) {
    var selectedOperatingMode: OperatingModeModel = initialOperatingMode
        private set

    fun updateMode(newMode: OperatingModeModel) {
        selectedOperatingMode = newMode
    }
}
