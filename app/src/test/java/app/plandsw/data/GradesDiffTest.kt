package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GradesDiffTest {
    private fun term(vararg courses: Pair<String, List<String>>) = listOf(
        TermGrades(
            "2025/26L", "Semestr letni 2025/26",
            courses.map { (name, attempts) -> CourseGrades("KOD-$name", name, listOf(ClassGrade("Wykład", attempts))) },
        )
    )

    @Test
    fun firstLoadReportsNothing() {
        assertEquals(emptyList<GradeChange>(), GradesDiff.changes(null, term("Fizyka" to listOf("4"))))
    }

    @Test
    fun newGrade() {
        assertEquals(
            listOf(GradeChange("2025/26L", "KOD-Fizyka", "Fizyka", "Wykład", "4")),
            GradesDiff.changes(term("Fizyka" to emptyList()), term("Fizyka" to listOf("4"))),
        )
    }

    @Test
    fun unchangedGrades() {
        assertEquals(emptyList<GradeChange>(), GradesDiff.changes(term("Fizyka" to listOf("4")), term("Fizyka" to listOf("4"))))
    }

    @Test
    fun retakeWithSameValueIsNew() {
        val changes = GradesDiff.changes(term("Fizyka" to listOf("2")), term("Fizyka" to listOf("2", "2")))
        assertEquals(listOf("2"), changes.map { it.value })
    }

    @Test
    fun gradeInCourseThatWasNotThere() {
        val changes = GradesDiff.changes(
            term("Fizyka" to listOf("4")),
            term("Fizyka" to listOf("4"), "Etyka" to listOf("ZAL")),
        )
        assertEquals(listOf("Etyka"), changes.map { it.courseName })
    }

    @Test
    fun removedGradeIsNotReported() {
        assertEquals(emptyList<GradeChange>(), GradesDiff.changes(term("Fizyka" to listOf("4")), term("Fizyka" to emptyList())))
    }
}
