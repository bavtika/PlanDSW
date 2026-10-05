package app.plandsw.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder

/**
 * Parses the USOSweb "moje oceny" page (kontroler.php?_action=dla_stud/studia/oceny/index).
 *
 * usos-frame#oceny → usos-frame-section[section-title="Semestr letni 2025/26"] → table → tr of 4 cells:
 * "Name [CODE]" | programme | grades | "szczegóły" (the dialog URL holds the semester code cdyd_kod).
 * The grades cell has one div per class form: «<a title=" Wykład ">W</a>: <span>(2)</span> <span bold>3</span>».
 * The bold span is the current grade (red means failed), the others are past attempts in parentheses;
 * span.note "(brak ocen)" means there are no grades.
 */
object UsosGradesParser {
    private val courseCode = Regex("""\[([^\]]+)]""")
    private val termCode = Regex("""cdyd_kod=([^&]+)""")

    /**
     * @throws UsosLoginRequired if the "Wymagane zalogowanie" page came back instead of grades.
     * @throws UnexpectedResponseException if the page does not look like a grades page.
     */
    fun parse(html: String): List<TermGrades> {
        val doc = Jsoup.parse(html)
        val frame = doc.selectFirst("usos-frame#oceny")
        if (frame == null) {
            if (doc.title().contains("zalogowanie", ignoreCase = true)) throw UsosLoginRequired()
            throw UnexpectedResponseException("USOS grades page not recognized")
        }
        return frame.select("usos-frame-section").mapNotNull { section ->
            val rows = section.select("table > tbody > tr").filter { it.children().size >= 3 }
            val courses = rows.mapNotNull(::course)
            if (courses.isEmpty()) return@mapNotNull null
            val title = section.attr("section-title").trim()
            val code = rows.firstNotNullOfOrNull { row ->
                row.selectFirst("usos-dialog[url]")?.attr("url")?.let { termCode.find(it)?.groupValues?.get(1) }
            }?.let { URLDecoder.decode(it, "UTF-8") } ?: title
            TermGrades(code, title, courses)
        }
    }

    private fun course(tr: Element): CourseGrades? {
        val cells = tr.children()
        val nameCell = cells[0]
        val name = nameCell.selectFirst("a")?.text()?.trim().orEmpty()
        if (name.isEmpty()) return null
        val code = courseCode.find(nameCell.text())?.groupValues?.get(1).orEmpty()
        val classes = cells[2].select("> div").map(::classGrade)
        return CourseGrades(code, name, classes)
    }

    private fun classGrade(div: Element): ClassGrade {
        val link = div.selectFirst("a")
        val type = link?.attr("title")?.trim()?.ifEmpty { null } ?: link?.text()?.trim().orEmpty()
        val spans = div.select("span").filterNot { it.hasClass("note") }
        val attempts = spans.map { it.text().trim().removeSurrounding("(", ")") }.filter { it.isNotEmpty() }
        val failed = spans.lastOrNull()?.attr("style")?.replace(" ", "")?.contains("color:#d00") == true
        return ClassGrade(type, attempts, failed)
    }
}
