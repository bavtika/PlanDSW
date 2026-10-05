package app.plandsw.widget

import app.plandsw.data.Lesson
import java.time.LocalDate
import java.time.LocalDateTime

data class WidgetDay(val date: LocalDate, val lessons: List<Lesson>)

/** Today while it still has unfinished classes, otherwise the nearest following day with classes. */
fun pickWidgetDay(lessons: List<Lesson>, now: LocalDateTime): WidgetDay? {
    val today = now.toLocalDate()
    val todays = lessons.filter { it.date == today }.sortedBy { it.start }
    if (todays.any { it.endAt.isAfter(now) }) return WidgetDay(today, todays)
    val next = lessons.filter { it.date.isAfter(today) }.minByOrNull { it.start } ?: return null
    return WidgetDay(next.date, lessons.filter { it.date == next.date }.sortedBy { it.start })
}
