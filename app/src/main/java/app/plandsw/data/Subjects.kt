package app.plandsw.data

import java.time.Duration
import java.time.LocalDateTime

/** A semester subject: all its classes in order and how many of them have passed as of `now`. */
data class SubjectSummary(
    val name: String,
    val lessons: List<Lesson>,
    /** Class forms in order of first appearance: Wyk, Cw, Lab… */
    val types: List<String>,
    val teachers: List<String>,
    val done: Int,
    val totalMinutes: Long,
) {
    val next: Lesson? get() = lessons.getOrNull(done)
    val finished: Boolean get() = done == lessons.size
}

/** Builds subjects from classes: ongoing ones first, then finished ones; alphabetical within each. */
fun summarizeSubjects(lessons: Collection<Lesson>, now: LocalDateTime): List<SubjectSummary> =
    lessons
        .filter { it.subject.isNotBlank() }
        .groupBy { it.subject.trim() }
        .map { (name, items) ->
            val sorted = items.sortedBy { it.startAt }
            SubjectSummary(
                name = name,
                lessons = sorted,
                types = sorted.map { it.type.trim() }.filter { it.isNotEmpty() }.distinct(),
                teachers = sorted.map { it.teacher.trim() }.filter { it.isNotEmpty() }.distinct(),
                // A class counts as passed once it has ended.
                done = sorted.count { !now.isBefore(it.endAt) },
                totalMinutes = sorted.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() },
            )
        }
        .sortedWith(compareBy({ it.finished }, { it.name.lowercase() }))
