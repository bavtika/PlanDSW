package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSummaryTest {
    private val course by lazy {
        TestCourse(101, "S1-00-FIZ2-2", "Fizyka II", UsosTestsParser.parseCourse(javaClass.getResource("/usos/sprawdzian.html")!!.readText()))
    }
    private val summary by lazy { summarizeTests(course) }

    @Test
    fun attemptSuffixIsRemoved() {
        assertEquals("Ocena z wykładu", gradeBaseName("Ocena z wykładu - II termin"))
        assertEquals("Ocena końcowa", gradeBaseName("Ocena końcowa"))
    }

    @Test
    fun finalGradeIsPulledOut() {
        assertEquals(GradeLine("Ocena końcowa z przedmiotu", listOf("2"), hidden = false), summary.final)
        assertTrue(summary.otherGrades.isEmpty())
    }

    @Test
    fun groupsKeepPointsAndItems() {
        assertEquals(listOf("Ćwiczenia", "Wykład"), summary.groups.map { it.name })
        val cw = summary.groups[0]
        assertEquals("21.00" to "42.00", cw.value to cw.max)
        assertEquals(listOf("Kolokwium nr 1", "Kolokwium nr 2", "Kartkówki"), cw.items.map { it.name })
    }

    @Test
    fun emptyAttemptsAreDroppedAndOthersMerged() {
        // "II termin" has no grade yet: only the first attempt is left.
        assertEquals(listOf(GradeLine("Ocena z ćwiczeń", listOf("3,5"), hidden = false)), summary.groups[0].grades)
    }

    @Test
    fun hiddenGradeStaysVisibleAsHidden() {
        val wy = summary.groups[1]
        assertTrue(wy.hidden)
        assertEquals(listOf(GradeLine("Ocena z wykładu", emptyList(), hidden = true)), wy.grades)
    }

    @Test
    fun retakeIsMergedIntoOneLine() {
        val nodes = listOf(
            TestNode("Ocena końcowa - I termin", isGrade = true, value = "2"),
            TestNode("Ocena końcowa - II termin", isGrade = true, value = "3,5"),
        )
        val s = summarizeTests(TestCourse(1, "K", "X", nodes))
        assertEquals(listOf("2", "3,5"), s.final!!.attempts)
        assertEquals("3,5", s.final!!.current)
    }

    @Test
    fun nothingEnteredMeansEmpty() {
        val s = summarizeTests(TestCourse(1, "K", "X", listOf(TestNode("Ocena - I termin", isGrade = true))))
        assertNull(s.final)
        assertTrue(s.isEmpty)
    }
}
