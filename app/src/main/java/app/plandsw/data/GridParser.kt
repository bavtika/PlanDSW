package app.plandsw.data

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Parses the HTML that the plan's DevExpress grid returns in response to a callback.
 *
 * Rows come in sequence: "Data Zajęć: 2026.10.01 czwartek" (group header by date),
 * followed by that day's classes. Columns are matched by header, not by position —
 * the site lets users hide and reorder them.
 */
object GridParser {
    private val time = DateTimeFormatter.ofPattern("H:mm")
    private val dateRegex = Regex("""(\d{4})\.(\d{2})\.(\d{2})""")

    fun parse(html: String, grid: String): List<Lesson> {
        val doc = Jsoup.parse(html)
        val headerId = Regex("""${Regex.escape(grid)}_col(\d+)""")
        val headers = doc.select("td[id^=${grid}_col]")
            .mapNotNull { td -> headerId.matchEntire(td.id())?.let { it.groupValues[1].toInt() to td } }
            .sortedBy { it.first }
            .map { it.second }
            .map { it.text().clean() }
        fun col(name: String) = headers.indexOfFirst { it.startsWith(name, ignoreCase = true) }
        val iStart = col("Czas od")
        val iEnd = col("Czas do")
        val iGroups = col("Grupy")
        val iSubject = col("Zajęcia")
        val iType = col("Forma zaj")
        val iRoom = col("Sala")
        val iTeacher = col("Prowadzący")
        val iExam = col("Forma zaliczenia")
        val iRemarks = col("Uwagi")
        // Without grid headers this is not a grid response (server error, markup changed) —
        // an empty list here would look like "all classes were cancelled".
        if (iStart < 0 || iSubject < 0) throw UnexpectedResponseException("Grid $grid not found in response")

        val lessons = mutableListOf<Lesson>()
        var date: LocalDate? = null
        for (tr in doc.select("tr[id^=${grid}_DXGroupRow], tr[id^=${grid}_DXDataRow]")) {
            if (tr.id().contains("GroupRow")) {
                date = dateRegex.find(tr.text())?.let { m ->
                    val (y, mo, d) = m.destructured
                    runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
                }
                continue
            }
            val day = date ?: continue
            val cells = tr.children().filterNot { it.hasClass("dxgvIndentCell") || it.hasClass("dxgvAIC") }
            fun cell(i: Int) = if (i < 0) "" else cells.getOrNull(i)?.text()?.clean().orEmpty()
            val start = runCatching { LocalTime.parse(cell(iStart), time) }.getOrNull() ?: continue
            val end = runCatching { LocalTime.parse(cell(iEnd), time) }.getOrNull() ?: start
            val groupNames = cells.getOrNull(iGroups)
                ?.select("a")?.map { it.text().clean() }?.filter { it.isNotEmpty() }
                ?.ifEmpty { null }
                ?: cell(iGroups).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            lessons += Lesson(
                start = day.atTime(start).toString(),
                end = day.atTime(end).toString(),
                subject = cell(iSubject),
                type = cell(iType),
                room = cell(iRoom),
                teacher = cell(iTeacher),
                groups = groupNames.joinToString(", "),
                examForm = cell(iExam),
                remarks = cell(iRemarks).takeUnless { it.equals("Brak", ignoreCase = true) }.orEmpty(),
                groupNames = groupNames,
            )
        }
        return lessons.sortedBy { it.start }
    }

    private fun String.clean() = replace(' ', ' ').trim()
}
