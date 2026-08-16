package com.example.cardiosimulator.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Student(
    val id: String,
    val fullName: String,
    val group: String,
    val email: String? = null,
    val registeredAt: Long,          // epoch millis
) {
    companion object {
        fun create(fullName: String, group: String, email: String?): Student? {
            val name = fullName.trim()
            val grp = group.trim()
            if (name.isBlank() || grp.isBlank()) return null
            
            return Student(
                id = UUID.randomUUID().toString(),
                fullName = name,
                group = grp,
                email = email?.trim()?.ifBlank { null },
                registeredAt = System.currentTimeMillis()
            )
        }
    }

    fun toExamInfo() = ExamStudentInfo(fullName, group)
}
