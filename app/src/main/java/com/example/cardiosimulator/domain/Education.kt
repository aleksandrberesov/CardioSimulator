package com.example.cardiosimulator.domain

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String,
    val title: String,
    val description: String = "",
    val lectures: List<Lecture> = emptyList()
)

@Serializable
data class Lecture(
    val id: String,
    val title: String,
    val textContent: String = "",
    val attachedPathologyIds: List<String> = emptyList(),
    // We could add image URIs later if needed
)
