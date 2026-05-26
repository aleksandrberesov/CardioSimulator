package com.example.cardiosimulator.domain

class AppBuilder {
    private val modes = mutableListOf<OperatingModeModel>()

    fun addMode(mode: OperatingModeModel): AppBuilder {
        modes.add(mode)
        return this
    }

    fun build(initialMode: OperatingMode? = null): AppStateModel {
        check(modes.isNotEmpty()) { "At least one operating mode must be added before building." }
        val initial = initialMode?.let { id -> modes.find { it.id == id } } ?: modes.first()
        return AppStateModel(
            initialOperatingMode = initial,
            operatingModes = modes.toList(),
        )
    }
}
