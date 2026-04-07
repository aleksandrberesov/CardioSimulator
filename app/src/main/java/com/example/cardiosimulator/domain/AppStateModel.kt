package com.example.cardiosimulator.domain

data class AppStateModel(val initialOperatingMode: String) {
    var selectedOperatingMode: String = initialOperatingMode
        private set

    val operatingModes = listOf("Item 1", "Item 2", "Item 3", "Item 4")
    fun updateMode(newMode: String) {
        selectedOperatingMode = newMode
    }
}
