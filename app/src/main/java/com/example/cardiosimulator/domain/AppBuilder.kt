package com.example.cardiosimulator.domain

class AppBuilder {
    private val modes = mutableListOf<OperatingModeModel>()

    fun addMode(mode: OperatingModeModel): AppBuilder {
        modes.add(mode)
        return this
    }

    fun build(initialModeTitle: String? = null): AppStateModel {
        check(modes.isNotEmpty()) { "At least one operating mode must be added before building." }
        val initialMode = if (initialModeTitle != null) {
            modes.find { it.title == initialModeTitle } ?: modes.first()
        } else {
            modes.first()
        }
        return AppStateModel(
            initialOperatingMode = initialMode,
            operatingModes = modes.toList()
        )
    }
}
