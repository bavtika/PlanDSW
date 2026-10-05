package app.plandsw.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Splits [from]..[to] (inclusive) into at most [parts] adjacent ranges of nearly equal length —
 * with no gaps or overlaps. Used to load a semester in parallel chunks.
 */
fun splitRange(from: LocalDate, to: LocalDate, parts: Int): List<Pair<LocalDate, LocalDate>> {
    require(parts >= 1)
    if (to.isBefore(from)) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to) + 1
    val n = minOf(parts.toLong(), days).toInt()
    val base = days / n
    val extra = days % n
    var start = from
    return (0 until n).map { i ->
        val length = base + if (i < extra) 1 else 0
        val end = start.plusDays(length - 1)
        (start to end).also { start = end.plusDays(1) }
    }
}
