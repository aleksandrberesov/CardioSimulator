package com.example.cardiosimulator.domain

import androidx.annotation.StringRes
import com.example.cardiosimulator.R

enum class OperatingMode(@param:StringRes val titleRes: Int) {
    Teaching(R.string.mode_teaching),
    Testing(R.string.mode_testing),
    Examination(R.string.mode_examination),
    OSKE(R.string.mode_oske),
    Constructor(R.string.mode_editor),
    CourseConstructor(R.string.mode_course_constructor)
}

data class OperatingModeModel(
    val id: OperatingMode,
    val description: String = ""
)
