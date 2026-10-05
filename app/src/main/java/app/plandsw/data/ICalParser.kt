package app.plandsw.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Parses the iCal export of harmonogramy.ideis.pl.
 *
 * All class details live in DESCRIPTION as "Key: value" lines:
 * Przedmiot, Grupy (actually the class form: Wyk/Cw/Lab…), Toki nauki, Sala,
 * Prowadzący, Forma zaliczenia, Temat, Uwagi.
 */
object ICalParser {
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(ics: String): List<Lesson> {
        val unfolded = ics.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "")
        val lessons = mutableListOf<Lesson>()
        var event: MutableMap<String, String>? = null
        for (line in unfolded.lineSequence()) {
            when {
                line == "BEGIN:VEVENT" -> event = mutableMapOf()
                line == "END:VEVENT" -> {
                    event?.let { toLesson(it) }?.let(lessons::add)
                    event = null
                }
                event != null -> {
                    val colon = line.indexOf(':')
                    if (colon > 0) event[line.substring(0, colon).substringBefore(';')] = line.substring(colon + 1)
                }
            }
        }
        return lessons.sortedBy { it.start }
    }

    private fun toLesson(props: Map<String, String>): Lesson? {
        val start = props["DTSTART"]?.let(::parseDateTime) ?: return null
        val end = props["DTEND"]?.let(::parseDateTime) ?: start
        val fields = unescape(props["DESCRIPTION"].orEmpty())
            .lines()
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            }
            .toMap()
        val subject = fields["Przedmiot"]?.takeIf { it.isNotBlank() }
            ?: unescape(props["SUMMARY"].orEmpty()).trim()
        return Lesson(
            start = start.toString(),
            end = end.toString(),
            subject = subject,
            type = fields["Grupy"].orEmpty(),
            room = fields["Sala"].orEmpty(),
            teacher = fields["Prowadzący"].orEmpty(),
            groups = fields["Toki nauki"].orEmpty(),
            examForm = fields["Forma zaliczenia"].orEmpty(),
            remarks = fields["Uwagi"].orEmpty(),
            topic = fields["Temat"].orEmpty(),
        )
    }

    private fun parseDateTime(value: String): LocalDateTime? = runCatching {
        val v = value.removeSuffix("Z")
        if (v.length == 8) LocalDateTime.parse(v + "T000000", dateTime) else LocalDateTime.parse(v, dateTime)
    }.getOrNull()

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                append(if (n == 'n' || n == 'N') '\n' else n)
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
