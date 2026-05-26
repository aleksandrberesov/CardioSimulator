package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.Lecture

object MockCourses {
    val courses = listOf(
        Course(
            id = "general",
            title = "Общий курс (Все графики)",
            description = "Все доступные ритмы ЭКГ"
        ),
        Course(
            id = "med_college",
            title = "Медицинский колледж",
            description = "Базовый курс",
            lectures = listOf(
                Lecture(
                    id = "med_col_lec1",
                    title = "Лекция 1: Основы ЭКГ",
                    textContent = "Электрокардиография (ЭКГ) — методика регистрации и исследования электрических полей, образующихся при работе сердца.\n\nВ этой лекции мы рассмотрим нормальный синусовый ритм.",
                    attachedPathologyIds = listOf("01_sinus_rhythm") // Need actual IDs from manifest, assuming this exists or user can change it
                ),
                Lecture(
                    id = "med_col_lec2",
                    title = "Лекция 2: Аритмии",
                    textContent = "Аритмия — нарушение частоты, ритмичности и последовательности возбуждения и сокращения сердца.\n\nРассмотрим брадикардию и тахикардию.",
                    attachedPathologyIds = listOf("02_sinus_bradycardia", "03_sinus_tachycardia")
                )
            )
        ),
        Course(
            id = "med_uni",
            title = "Медицинский университет",
            description = "Продвинутый курс",
            lectures = listOf(
                Lecture(
                    id = "med_uni_lec1",
                    title = "Лекция 1: Инфаркт миокарда",
                    textContent = "Инфаркт миокарда — одна из клинических форм ишемической болезни сердца, протекающая с развитием ишемического некроза участка миокарда.",
                    attachedPathologyIds = listOf("23_ami_anteroseptal")
                )
            )
        )
    )
}
