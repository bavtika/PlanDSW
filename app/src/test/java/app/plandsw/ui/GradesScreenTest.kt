package app.plandsw.ui

import app.plandsw.data.TermGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesScreenTest {
    private fun term(code: String, title: String) = TermGrades(code, title, emptyList())

    @Test
    fun winterTerm() {
        assertEquals(TermSeason(winter = true, years = "2026/27"), termSeason(term("2026/27Z", "Semestr zimowy 2026/27")))
    }

    @Test
    fun summerTerm() {
        assertEquals(TermSeason(winter = false, years = "2025/26"), termSeason(term("2025/26L", "Semestr letni 2025/26")))
    }

    @Test
    fun unknownCodeHasNoSeason() {
        assertNull(termSeason(term("2025/26", "Rok akademicki 2025/26")))
    }

    @Test
    fun pointsLoseSitePadding() {
        assertEquals("63", trimPoints("63.00"))
        assertEquals("17.5", trimPoints("17.50"))
        assertEquals("0.75", trimPoints("0.75"))
        assertEquals("12", trimPoints("12"))
    }

    @Test
    fun failingGrades() {
        assertTrue(isFailingGrade("2"))
        assertTrue(isFailingGrade("2,0"))
        assertTrue(isFailingGrade("NZAL"))
        assertFalse(isFailingGrade("3,5"))
        assertFalse(isFailingGrade("ZAL"))
    }
}
