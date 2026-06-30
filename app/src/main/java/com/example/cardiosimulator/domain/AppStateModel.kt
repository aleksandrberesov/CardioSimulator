package com.example.cardiosimulator.domain

enum class Language(val tag: String, val displayName: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
    ZH("zh", "中文"),
    ES("es", "Español"),
    HI("hi", "हिन्दी");

    companion object {
        fun fromTag(tag: String?): Language? {
            if (tag.isNullOrEmpty()) return null
            val primary = tag.substringBefore('-').lowercase()
            return entries.firstOrNull { it.tag == primary }
        }
    }
}

data class AppStateModel(
    val initialOperatingMode: OperatingModeModel,
    val operatingModes: List<OperatingModeModel>,
    val initialLanguage: Language = Language.EN,
    val initialTcpIp: String = "192.168.1.100",
    val initialTcpPort: Int = 8080
) {
    var selectedOperatingMode: OperatingModeModel = initialOperatingMode
        private set

    var selectedLanguage: Language = initialLanguage
        private set

    var tcpIp: String = initialTcpIp
        private set

    var tcpPort: Int = initialTcpPort
        private set

    fun updateMode(newMode: OperatingModeModel) {
        selectedOperatingMode = newMode
    }

    fun updateLanguage(newLanguage: Language) {
        selectedLanguage = newLanguage
    }

    fun updateTcpConnection(ip: String, port: Int) {
        tcpIp = ip
        tcpPort = port
    }
}
