package app.plandsw.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses the USOSweb "Sprawdziany" section.
 *
 * Index (dla_stud/studia/sprawdziany/index): one usos-frame per semester, h3 "Semestr letni 2025/26" with
 * span.note "2025/26L", then li items: subject name, subject code and a "pokaż" link with wez_id.
 *
 * Subject page (dla_stud/studia/sprawdziany/pokaz&wez_id=N): div#drzewo holds the "(korzeń)" table followed by
 * div#childrenofN. Every level is a run of table.grey nodes; a node with children is followed by its own
 * div#childrenofM. A node row has cells: icon | "Name <span.note>- max 24.0 pkt</span>" (or "- ocena") |
 * "<b>11.0</b> pkt" (or span.note "brak oceny" / "wynik jest ukryty") | "Wystawiający: … Komentarz: …".
 */
object UsosTestsParser {
    /** A subject in the index: its tree id, code and name. */
    data class Ref(val id: Int, val code: String, val name: String)

    data class IndexTerm(val code: String, val title: String, val refs: List<Ref>)

    private val wezId = Regex("""wez_id=(\d+)""")
    private val maxPoints = Regex("""max\s+(-?[\d.,]+)""")

    /**
     * @throws UsosLoginRequired if the sign-in page came back.
     * @throws UnexpectedResponseException if the page does not look like the tests index.
     */
    fun parseIndex(html: String): List<IndexTerm> {
        val doc = Jsoup.parse(html)
        val main = doc.selectFirst("main") ?: doc.body()
        if (main.selectFirst("h1") == null) {
            if (doc.title().contains("zalogowanie", ignoreCase = true)) throw UsosLoginRequired()
            throw UnexpectedResponseException("USOS tests index not recognized")
        }
        return main.select("usos-frame").mapNotNull { frame ->
            val h3 = frame.selectFirst("h3") ?: return@mapNotNull null
            val title = h3.ownText().trim()
            val code = h3.selectFirst("span.note")?.text()?.trim()?.ifEmpty { null } ?: title
            val refs = frame.select("li").mapNotNull(::ref)
            if (refs.isEmpty()) null else IndexTerm(code, title, refs)
        }
    }

    private fun ref(li: Element): Ref? {
        val link = li.selectFirst("a[href*=sprawdziany/pokaz]") ?: return null
        val id = wezId.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val spans = li.selectFirst("div > div")?.select("> span").orEmpty()
        val name = spans.getOrNull(0)?.text()?.trim().orEmpty()
        if (name.isEmpty()) return null
        return Ref(id, spans.getOrNull(1)?.text()?.trim().orEmpty(), name)
    }

    /**
     * Top-level nodes of a subject's test tree (the "(korzeń)" root itself is skipped).
     * @throws UsosLoginRequired if the sign-in page came back.
     * @throws UnexpectedResponseException if the page has no test tree.
     */
    fun parseCourse(html: String): List<TestNode> {
        val doc = Jsoup.parse(html)
        val tree = doc.selectFirst("div#drzewo")
        if (tree == null) {
            if (doc.title().contains("zalogowanie", ignoreCase = true)) throw UsosLoginRequired()
            throw UnexpectedResponseException("USOS test tree not recognized")
        }
        val top = tree.children().firstOrNull { it.id().startsWith("childrenof") } ?: return emptyList()
        return level(top)
    }

    private fun level(container: Element): List<TestNode> {
        val items = container.children()
        val nodes = mutableListOf<TestNode>()
        for ((i, el) in items.withIndex()) {
            if (el.tagName() != "table" || !el.hasClass("grey")) continue
            // Children, if any, are the div#childrenofN that follows before the next node.
            val children = items.drop(i + 1)
                .takeWhile { it.tagName() != "table" }
                .firstOrNull { it.id().startsWith("childrenof") }
                ?.let(::level)
                .orEmpty()
            node(el, children)?.let(nodes::add)
        }
        return nodes
    }

    private fun node(table: Element, children: List<TestNode>): TestNode? {
        val cells = table.selectFirst("tr")?.children() ?: return null
        val nameCell = cells.getOrNull(1) ?: return null
        val name = nameCell.ownText().trim()
        if (name.isEmpty()) return null
        val note = nameCell.selectFirst("span.note")?.text().orEmpty()
        val valueCell = cells.getOrNull(2)
        val value = valueCell?.selectFirst("b")?.text()?.trim()?.ifEmpty { null }
        val info = cells.getOrNull(3)?.text().orEmpty()
        return TestNode(
            name = name,
            isGrade = note.contains("ocena"),
            value = value,
            max = maxPoints.find(note)?.groupValues?.get(1),
            hidden = value == null && valueCell?.text()?.contains("ukryty") == true,
            comment = info.substringAfter("Komentarz:", "").trim(),
            children = children,
        )
    }
}
