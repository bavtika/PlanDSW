package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleDiffTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun lesson(
        day: Int,
        hour: Int,
        subject: String,
        room: String = "S55 111",
        type: String = "Wyk",
        groups: List<String> = listOf("Infor WykS"),
    ) = Lesson(
        start = LocalDateTime.of(2026, 10, day, hour, 0).toString(),
        end = LocalDateTime.of(2026, 10, day, hour + 1, 30).toString(),
        subject = subject,
        type = type,
        room = room,
        groups = groups.joinToString(),
        groupNames = groups,
    )

    @Test
    fun identicalSchedulesHaveNoChanges() {
        val s = listOf(lesson(6, 9, "Bazy danych"), lesson(7, 12, "Fizyka"))
        assertTrue(ScheduleDiff.diff(s, s.toList(), null, today).isEmpty())
    }

    @Test
    fun detectsRoomChange() {
        val c = ScheduleDiff.diff(listOf(lesson(6, 9, "Bazy danych")), listOf(lesson(6, 9, "Bazy danych", room = "S47 210")), null, today)
        assertEquals(listOf(ChangeKind.ROOM), c.map { it.kind })
        assertEquals("S55 111", c[0].old!!.room)
        assertEquals("S47 210", c[0].new!!.room)
    }

    @Test
    fun detectsMove() {
        val c = ScheduleDiff.diff(listOf(lesson(6, 9, "Bazy danych")), listOf(lesson(8, 10, "Bazy danych")), null, today)
        assertEquals(listOf(ChangeKind.MOVED), c.map { it.kind })
        assertEquals(6, c[0].old!!.date.dayOfMonth)
        assertEquals(8, c[0].new!!.date.dayOfMonth)
    }

    @Test
    fun detectsCancelAndAdd() {
        val c = ScheduleDiff.diff(listOf(lesson(6, 9, "Bazy danych")), listOf(lesson(6, 9, "Fizyka")), null, today)
        assertEquals(setOf(ChangeKind.CANCELLED, ChangeKind.ADDED), c.map { it.kind }.toSet())
    }

    @Test
    fun ignoresPastLessons() {
        assertTrue(ScheduleDiff.diff(listOf(lesson(2, 9, "Bazy danych")), emptyList(), null, today).isEmpty())
    }

    @Test
    fun ignoresOtherGroups() {
        val lab = lesson(6, 9, "Sieci", type = "Lab", groups = listOf("Infor Lab1AS"))
        assertTrue(ScheduleDiff.diff(listOf(lab), emptyList(), setOf("Infor Lab2AS"), today).isEmpty())
        assertEquals(1, ScheduleDiff.diff(listOf(lab), emptyList(), setOf("Infor Lab1AS"), today).size)
    }

    @Test
    fun lessonsWithoutGroupsCountForEveryone() {
        val c = ScheduleDiff.diff(emptyList(), listOf(lesson(6, 9, "Godziny rektorskie", groups = emptyList())), setOf("Infor Lab2AS"), today)
        assertEquals(listOf(ChangeKind.ADDED), c.map { it.kind })
    }

    @Test
    fun keepsUnchangedTwinWhenOneOfTwoMoves() {
        val old = listOf(lesson(6, 9, "Bazy danych"), lesson(6, 11, "Bazy danych"))
        val new = listOf(lesson(6, 9, "Bazy danych"), lesson(9, 11, "Bazy danych"))
        val c = ScheduleDiff.diff(old, new, null, today)
        assertEquals(1, c.size)
        assertEquals(ChangeKind.MOVED, c[0].kind)
        assertEquals(11, c[0].old!!.startAt.hour)
        assertEquals(9, c[0].new!!.date.dayOfMonth)
    }
}
