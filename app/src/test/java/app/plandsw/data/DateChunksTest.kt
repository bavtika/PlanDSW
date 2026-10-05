package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateChunksTest {
    @Test
    fun coversWholeRangeWithoutGapsOrOverlaps() {
        val from = LocalDate.of(2026, 9, 11)
        val to = LocalDate.of(2027, 2, 7)
        val parts = splitRange(from, to, 4)
        assertEquals(4, parts.size)
        assertEquals(from, parts.first().first)
        assertEquals(to, parts.last().second)
        parts.zipWithNext().forEach { (a, b) -> assertEquals(a.second.plusDays(1), b.first) }
        parts.forEach { (a, b) -> assertTrue(!b.isBefore(a)) }
    }

    @Test
    fun shortRangeGivesFewerParts() {
        val day = LocalDate.of(2026, 10, 1)
        assertEquals(listOf(day to day), splitRange(day, day, 4))
        assertEquals(2, splitRange(day, day.plusDays(1), 4).size)
    }

    @Test
    fun reversedRangeIsEmpty() {
        assertTrue(splitRange(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), 4).isEmpty())
    }
}
