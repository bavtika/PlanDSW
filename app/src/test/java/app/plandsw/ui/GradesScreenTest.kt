package app.plandsw.ui

import app.plandsw.data.TermGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
