package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Student
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class StudentStore(private val file: File) {

    fun list(): List<Student> {
        if (!file.exists()) return emptyList()
        return runCatching {
            testJson.decodeFromString<List<Student>>(file.readText())
        }.getOrDefault(emptyList())
            .sortedByDescending { it.registeredAt }
    }

    fun add(student: Student): Boolean {
        val current = list().toMutableList()
        if (contains(student.fullName, student.group)) return false
        
        current.add(student)
        return save(current)
    }

    fun addOrUpdate(student: Student): Boolean {
        val current = list().toMutableList()
        val existingIdx = current.indexOfFirst {
            it.fullName.trim().equals(student.fullName.trim(), ignoreCase = true) &&
            it.group.trim().equals(student.group.trim(), ignoreCase = true)
        }
        
        if (existingIdx != -1) {
            val existing = current[existingIdx]
            if (existing.email.isNullOrBlank() && !student.email.isNullOrBlank()) {
                current[existingIdx] = existing.copy(email = student.email)
                return save(current)
            }
            return true
        }
        
        current.add(student)
        return save(current)
    }

    fun remove(id: String): Boolean {
        val current = list().toMutableList()
        val removed = current.removeIf { it.id == id }
        return if (removed) save(current) else true
    }

    fun contains(fullName: String, group: String): Boolean {
        val name = fullName.trim()
        val grp = group.trim()
        return list().any { 
            it.fullName.trim().equals(name, ignoreCase = true) && 
            it.group.trim().equals(grp, ignoreCase = true) 
        }
    }

    private fun save(students: List<Student>): Boolean {
        file.parentFile?.mkdirs()
        return atomicWriteText(file, testJson.encodeToString(students))
    }
}
