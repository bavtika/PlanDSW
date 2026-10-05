package app.plandsw.widget

import app.plandsw.data.Lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetDayTest {
    private fun lesson(day: Int, hour: Int) = Lesson(
        start = LocalDateTime.of(2026, 10, day, hour, 0).toString(),
        end = LocalDateTime.of(2026, 10, day, hour + 1, 30).toString(),
        subject = "S$day-$hour",
    )

    private val week = listOf(lesson(5, 9), lesson(5, 12), lesson(7, 10))

    @Test
    fun showsTodayWhileClassesRemain() {
        val day = pickWidgetDay(week, LocalDateTime.of(2026, 10, 5, 12, 30))!!
        assertEquals(LocalDate.of(2026, 10, 5), day.date)
        assertEquals(2, day.lessons.size)
    }

    @Test
    fun switchesToNextDayAfterLastClass() {
        val day = pickWidgetDay(week, LocalDateTime.of(2026, 10, 5, 14, 0))!!
        assertEquals(LocalDate.of(2026, 10, 7), day.date)
        assertEquals(1, day.lessons.size)
    }

    @Test
    fun nullWhenNothingAhead() {
        assertNull(pickWidgetDay(week, LocalDateTime.of(2026, 10, 8, 8, 0)))
    }
}
