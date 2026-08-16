package com.example.cardiosimulator.domain

import kotlinx.serialization.Serializable

@Serializable
data class StudentExportPackage(
    val version: Int = 1,
    val exportedAt: String,
    val students: List<Student>,
    val examResults: List<ExamResult>,
    val oskeResults: List<OskeResult>,
    /** Legacy fallback mapping for older exports. */
    val results: List<ExamResult>? = null
)
