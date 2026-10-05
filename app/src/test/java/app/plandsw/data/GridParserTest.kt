package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GridParserTest {
    private val grid = "gridViewPlanyTokow"
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesEveryDataRow() {
        val html = fixture("grid_tok_week.html")
        val rows = Regex("${grid}_DXDataRow\\d+\"").findAll(html).count()
        assertTrue(rows > 0)
        assertEquals(rows, GridParser.parse(html, grid).size)
    }

    @Test
    fun fillsLessonFields() {
        val week = LocalDate.of(2026, 10, 5)..LocalDate.of(2026, 10, 11)
        GridParser.parse(fixture("grid_tok_week.html"), grid).forEach {
            assertTrue(it.subject.isNotBlank())
            assertTrue(it.groupNames.isNotEmpty())
            assertTrue(it.date in week)
            assertTrue(it.endAt.isAfter(it.startAt))
        }
    }

    @Test(expected = UnexpectedResponseException::class)
    fun rejectsHtmlWithoutGrid() {
        GridParser.parse("<html><body>Błąd serwera</body></html>", grid)
    }
}
