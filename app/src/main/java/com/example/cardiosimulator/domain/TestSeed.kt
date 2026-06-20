package com.example.cardiosimulator.domain

object TestSeed {
    const val SAMPLE_TEST_ID = "sample"
    private const val SAMPLE_QUESTION_SECONDS = 300

    fun sample(pathologyIds: List<String>): Test {
        fun ecg(i: Int) = pathologyIds.getOrNull(i)

        val questions = listOf(
            TestQuestion(
                id = "q1",
                number = 1,
                text = "Найти депрессию (если она имеется), проверить, является ли она вторичной; если нет — отнести её в разряд «патологической, требующей дифференциальной диагностики (ДД)».",
                options = listOf(
                    TestOption("a", "Депрессия присутствует"),
                    TestOption("b", "Отсутствует, так как…"),
                    TestOption("c", "Да, вторичная"),
                    TestOption("d", "ПДД")
                ),
                correctOptionId = "a",
                comment = "На графике видно, что в сегменте AVL и V5, V7 чётко видны подъёмы сегмента ST.",
                pathologyId = ecg(0)
            ),
            TestQuestion(
                id = "q2",
                number = 2,
                text = "Определите основной ритм на представленной электрокардиограмме.",
                options = listOf(
                    TestOption("a", "Синусовый ритм"),
                    TestOption("b", "Фибрилляция предсердий"),
                    TestOption("c", "Желудочковая тахикардия"),
                    TestOption("d", "АВ-блокада III степени")
                ),
                correctOptionId = "a",
                comment = "Зубец P предшествует каждому комплексу QRS, интервалы R–R регулярны — это синусовый ритм.",
                pathologyId = ecg(1)
            ),
            TestQuestion(
                id = "q3",
                number = 3,
                text = "Оцените частоту сердечных сокращений по интервалу R–R.",
                options = listOf(
                    TestOption("a", "Менее 60 в минуту (брадикардия)"),
                    TestOption("b", "60–90 в минуту (нормосистолия)"),
                    TestOption("c", "Более 100 в минуту (тахикардия)"),
                    TestOption("d", "Определить невозможно")
                ),
                correctOptionId = "b",
                comment = "Интервал R–R соответствует частоте около 75 ударов в минуту — нормосистолия.",
                pathologyId = ecg(2)
            )
        )

        return Test(SAMPLE_TEST_ID, "Демонстрационный тест", questions, SAMPLE_QUESTION_SECONDS)
    }
}
