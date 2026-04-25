package com.example.cardiosimulator.domain

enum class Language {
    EN, RU, CH, ES
}

data class AppStateModel(
    val initialOperatingMode: OperatingModeModel,
    val operatingModes: List<OperatingModeModel>,
    val initialLanguage: Language = Language.EN
) {
    var selectedOperatingMode: OperatingModeModel = initialOperatingMode
        private set

    var selectedLanguage: Language = initialLanguage
        private set

    fun updateMode(newMode: OperatingModeModel) {
        selectedOperatingMode = newMode
    }

    fun updateLanguage(newLanguage: Language) {
        selectedLanguage = newLanguage
    }
}
