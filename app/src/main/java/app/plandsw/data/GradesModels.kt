package app.plandsw.data

import kotlinx.serialization.Serializable

/** Grades for one class form of a subject — on the site "W: (2) 3". */
@Serializable
data class ClassGrade(
    /** Full form name from the site: "Wykład", "Ćwiczenia", "E-learning". */
    val type: String,
    /** Grades per attempt, in order: ["2", "3"] — I termin 2, II termin 3. Empty — no grades yet. */
    val attempts: List<String> = emptyList(),
    /** The current grade is a fail ("2", "NZAL") — the site shows it in red. */
    val failed: Boolean = false,
) {
    val current: String? get() = attempts.lastOrNull()
}

@Serializable
data class CourseGrades(
    /** Subject code in USOS: "S1-00-BAZDAN1-2". */
    val code: String,
    val name: String,
    val classes: List<ClassGrade>,
)

@Serializable
data class TermGrades(
    /** USOS semester code (cdyd_kod): "2025/26L", "2026/27Z". */
    val code: String,
    /** Heading from the site: "Semestr letni 2025/26". */
    val title: String,
    val courses: List<CourseGrades>,
)

@Serializable
data class CachedGrades(val fetchedAt: Long, val terms: List<TermGrades>)

fun gradeKey(termCode: String, courseCode: String, type: String) = "$termCode|$courseCode|$type"

/** A new or changed grade — for the "New grades" banner. */
@Serializable
data class GradeChange(
    val termCode: String,
    val courseCode: String,
    val courseName: String,
    val type: String,
    val value: String,
) {
    val key: String get() = gradeKey(termCode, courseCode, type)
}

/** USOSweb answered "Wymagane zalogowanie" — the user has to sign in again. */
class UsosLoginRequired : Exception("USOS login required")
