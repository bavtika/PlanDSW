package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SubjectsTest {
    private val now = LocalDateTime.of(2026, 10, 10, 12, 0)

    private fun lesson(day: Int, hour: Int, subject: String, type: String = "Wyk", teacher: String = "dr Nowak") = Lesson(
        start = LocalDateTime.of(2026, 10, day, hour, 0).toString(),
        end = LocalDateTime.of(2026, 10, day, hour + 1, 30).toString(),
        subject = subject,
        type = type,
        teacher = teacher,
    )

    @Test
    fun groupsLessonsBySubjectInDateOrder() {
        val s = summarizeSubjects(
            listOf(lesson(20, 9, "Fizyka"), lesson(3, 9, "Fizyka", "Cw", "mgr Kowal"), lesson(12, 9, "Fizyka")),
            now,
        ).single()
        assertEquals("Fizyka", s.name)
        assertEquals(listOf(3, 12, 20), s.lessons.map { it.date.dayOfMonth })
        assertEquals(listOf("Cw", "Wyk"), s.types)
        assertEquals(listOf("mgr Kowal", "dr Nowak"), s.teachers)
        assertEquals(270L, s.totalMinutes)
    }

    @Test
    fun countsFinishedLessonsAndNext() {
        // 10.10 11:00–12:30 is still running at 12:00, so it is not done.
        val s = summarizeSubjects(listOf(lesson(3, 9, "Bazy"), lesson(10, 11, "Bazy"), lesson(17, 9, "Bazy")), now).single()
        assertEquals(1, s.done)
        assertEquals(10, s.next!!.date.dayOfMonth)
        assertFalse(s.finished)
    }

    @Test
    fun finishedSubjectsGoLast() {
        val list = summarizeSubjects(
            listOf(lesson(3, 9, "Algebra"), lesson(20, 9, "Zarządzanie"), lesson(21, 9, "bazy danych")),
            now,
        )
        assertEquals(listOf("bazy danych", "Zarządzanie", "Algebra"), list.map { it.name })
        assertTrue(list.last().finished)
        assertNull(list.last().next)
    }

    @Test
    fun trimsNamesAndSkipsBlankSubjects() {
        val list = summarizeSubjects(listOf(lesson(20, 9, "Fizyka "), lesson(21, 9, "Fizyka"), lesson(22, 9, " ")), now)
        assertEquals(listOf("Fizyka" to 2), list.map { it.name to it.lessons.size })
    }
}
