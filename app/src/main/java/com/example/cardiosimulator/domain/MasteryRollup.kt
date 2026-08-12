package com.example.cardiosimulator.domain

import kotlin.math.roundToInt

/**
 * Answered/correct tally for one bucket (a subtopic, a section, or a group).
 */
data class MasteryStat(val answered: Int = 0, val correct: Int = 0) {
    /**
     * Mastery as a 0–100 percent (0 when nothing has been answered in this bucket).
     */
    val progress: Int
        get() = if (answered == 0) 0 else (100.0 * correct / answered).roundToInt()

    fun add(isCorrect: Boolean): MasteryStat =
        MasteryStat(answered + 1, correct + if (isCorrect) 1 else 0)
}

/**
 * The result of rolling graded exam attempts up through the [Taxonomy]: how the student
 * is doing per course subtopic (X.Y), per top-level section, and per rhythm group.
 */
data class MasteryReport(
    val bySubtopic: Map<String, MasteryStat>,
    val bySection: Map<Int, MasteryStat>,
    val byGroup: Map<String, MasteryStat>,
    val totalAnswered: Int,
    val totalCorrect: Int
) {
    /**
     * True once at least one graded, taxonomy-tagged question has been answered — the signal
     * the dashboard uses to switch from the demo seed to real data.
     */
    val hasData: Boolean get() = totalAnswered > 0

    /** Mastery for a subtopic key (X.Y), or an empty tally when it has no attempts. */
    fun subtopic(subtopicKey: String): MasteryStat =
        bySubtopic[subtopicKey] ?: MasteryStat()

    /** Mastery for a top-level section, or an empty tally when it has no attempts. */
    fun section(section: Int): MasteryStat =
        bySection[section] ?: MasteryStat()

    companion object {
        val Empty = MasteryReport(emptyMap(), emptyMap(), emptyMap(), 0, 0)
    }
}

/**
 * Rolls graded [ExamResult]s up into [MasteryReport] using the taxonomy.
 * Pure (no IO) so it is fully unit-testable.
 */
object MasteryRollup {
    fun compute(results: Iterable<ExamResult>, taxonomy: Taxonomy): MasteryReport {
        val bySubtopic = mutableMapOf<String, MasteryStat>()
        val bySection = mutableMapOf<Int, MasteryStat>()
        val byGroup = mutableMapOf<String, MasteryStat>()
        var totalAnswered = 0
        var totalCorrect = 0

        for (result in results) {
            for (q in result.questions) {
                if (q.acronyms.isEmpty()) continue

                // Resolve this question's distinct buckets so multiple acronyms landing in the same
                // subtopic/section/group count the answer only once there.
                val subtopics = mutableSetOf<String>()
                val sections = mutableSetOf<Int>()
                val groups = mutableSetOf<String>()
                for (acronym in q.acronyms) {
                    val e = taxonomy.find(acronym) ?: continue
                    subtopics.add(e.subtopicKey)
                    sections.add(e.section)
                    if (e.group.isNotEmpty()) groups.add(e.group)
                }
                if (subtopics.isEmpty()) continue // no recognized acronym

                for (key in subtopics) {
                    bySubtopic[key] = (bySubtopic[key] ?: MasteryStat()).add(q.isCorrect)
                }
                for (sec in sections) {
                    bySection[sec] = (bySection[sec] ?: MasteryStat()).add(q.isCorrect)
                }
                for (g in groups) {
                    byGroup[g] = (byGroup[g] ?: MasteryStat()).add(q.isCorrect)
                }

                totalAnswered++
                if (q.isCorrect) totalCorrect++
            }
        }

        return MasteryReport(bySubtopic, bySection, byGroup, totalAnswered, totalCorrect)
    }
}
