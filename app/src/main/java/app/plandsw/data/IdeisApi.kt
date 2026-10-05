package app.plandsw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ScheduleData(val from: LocalDate, val to: LocalDate, val lessons: List<Lesson>)

/**
 * Client for harmonogramy.ideis.pl (APR Wirtualny Dziekanat).
 *
 * The site has no public JSON API, so we use its internal endpoints.
 * Tok: POST to the DevExpress grid callback with a date range, the response is grid HTML (see [GridParser]);
 * the semester is split into chunks loaded in parallel. Teacher and room: GET the plan page →
 * grid callback → iCal export, all within one session.
 */
class IdeisApi(
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept-Language", "pl")
                    .header("User-Agent", "PlanDSW-Android/1.0")
                    .build()
            )
        }
        .build(),
) {
    companion object {
        const val BASE = "https://harmonogramy.ideis.pl"
        private const val DX_CUSTOM_CALLBACK = "c0:KV|2;[];GB|35;14|CUSTOMCALLBACK15|[object Object];"
        private val ACADEMIC_TERM = Regex("""(\d{4})/\d{4}\s+(Zima|Lato)""")

        /** How many chunks the semester is split into: 4 parallel requests ≈ 3.6 s instead of ≈ 9.5 s with one. */
        private const val TOK_PARTS = 4
    }

    /** [ical] == null — classes are taken straight from the grid response (the tok iCal has no subgroups). */
    private class Endpoints(val page: String, val callback: String, val grid: String, val params: String, val ical: String?)

    private fun endpoints(t: Target) = when (t.kind) {
        TargetKind.TOK -> Endpoints(
            "/Plany/PlanyTokow/${t.id}", "/Plany/PlanyTokowGridCustom/${t.id}",
            "gridViewPlanyTokow", ";${t.id}", null,
        )
        TargetKind.GROUP -> Endpoints(
            "/Plany/PlanyGrup/${t.id}", "/Plany/PlanyGrupGridCustom/${t.id}",
            "gridViewPlanyGrup", ";${t.id}", "/Plany/WydrukGrupyical/${t.id}",
        )
        TargetKind.TEACHER -> Endpoints(
            "/Plany/PlanyProwadzacych/${t.id}", "/Plany/PlanyProwadzacychGridCustom/${t.id}",
            "gridViewPlanyProwadzacych", ";${t.id}", "/Plany/WydrukProwadzacegoical/${t.id}",
        )
        TargetKind.ROOM -> Endpoints(
            "/Plany/PlanySal/${t.id};s", "/Plany/PlanySalGridCustom/${t.id}?typ=s",
            "gridViewPlanySal", "", "/Plany/WydrukSalical/${t.id}?typObiektu=s",
        )
    }

    /**
     * @param knownRange semester bounds from the cache. If present and the semester has not ended yet,
     * the plan page is not requested.
     */
    suspend fun fetchSchedule(target: Target, knownRange: Pair<LocalDate, LocalDate>? = null): ScheduleData =
        if (target.kind == TargetKind.TOK) fetchTok(target, knownRange) else fetchViaIcal(target)

    /** Tok plan: the grid callback works without a session, so semester chunks are loaded in parallel. */
    private suspend fun fetchTok(target: Target, knownRange: Pair<LocalDate, LocalDate>?): ScheduleData = coroutineScope {
        val ep = endpoints(target)
        val (from, to) = knownRange?.takeIf { !it.second.isBefore(LocalDate.now()) }
            ?: withContext(Dispatchers.IO) { semesterRange(Jsoup.parse(get(newSession(), ep.page), BASE)) }
        val lessons = splitRange(from, to, TOK_PARTS)
            .map { (a, b) ->
                async(Dispatchers.IO) {
                    val html = post(newSession(), ep.callback, gridCallbackBody(ep.grid, "${fmt(a)};${fmt(b)};5${ep.params}"))
                    GridParser.parse(html, ep.grid)
                }
            }
            .awaitAll()
            .flatten()
            .sortedBy { it.start }
        ScheduleData(from, to, lessons)
    }

    /** Teacher/room: the session remembers the object and dates, then serves the iCal. */
    private suspend fun fetchViaIcal(target: Target): ScheduleData = withContext(Dispatchers.IO) {
        val client = newSession()
        val ep = endpoints(target)
        val (from, to) = semesterRange(Jsoup.parse(get(client, ep.page), BASE))
        post(client, ep.callback, gridCallbackBody(ep.grid, "${fmt(from)};${fmt(to)};5${ep.params}"))
        val ics = get(client, ep.ical!!)
        if (!ics.contains("BEGIN:VCALENDAR")) throw UnexpectedResponseException("No VCALENDAR in iCal response")
        ScheduleData(from, to, ICalParser.parse(ics))
    }

    private fun gridCallbackBody(grid: String, params: String) = FormBody.Builder()
        .add("DXCallbackName", grid)
        .add("__DXCallbackArgument", DX_CUSTOM_CALLBACK)
        .add("parametry", params)
        .build()

    // ---- "Plany toków" wizard: Wydział → Kierunek → Nabór → Tryb studiów ----

    /** Faculties, intakes and study modes — they are embedded directly in the tok search page. */
    suspend fun loadTokFilters(): TokFilters = withContext(Dispatchers.IO) {
        val html = get(newSession(), "/Plany/ZnajdzTok?Ukryj=True")
        TokFilters(
            faculties = comboItems(html, "Wydzialy_DDD_L"),
            intakes = comboItems(html, "Nabory_DDD_L"),
            modes = comboItems(html, "TrybStudiow_DDD_L"),
        )
    }

    /** Fields of study of the selected faculty (DevExpress combo box loaded via callback). */
    suspend fun loadKierunki(facultyId: String): List<Option> = withContext(Dispatchers.IO) {
        val text = post(
            newSession(), "/Plany/ZnajdzTokKierunekCombo",
            FormBody.Builder()
                .add("DXCallbackName", "Kierunki")
                .add("__DXCallbackArgument", "c0:LECCP|0;;LBCRI|4;0:999;")
                .add("wydzialy", facultyId)
                .build(),
        )
        parseItems(text)
    }

    suspend fun searchToks(facultyId: String, kierunekId: String, intakeId: String, modeId: String): List<Target> =
        withContext(Dispatchers.IO) {
            val url = BASE.toHttpUrl().newBuilder()
                .addPathSegments("Plany/ZnajdzTok")
                .addQueryParameter("TrybStudiowId", modeId)
                .addQueryParameter("WydzialId", facultyId)
                .addQueryParameter("naborId", intakeId)
                .addQueryParameter("kierunekId", kierunekId)
                .addQueryParameter("specjalnoscId", "")
                .build()
            val html = execute(newSession(), Request.Builder().url(url).build())
            Jsoup.parse(html, BASE).select("tr[id^=gridViewZnajdzTok_DXDataRow]").mapNotNull { tr ->
                val link = tr.selectFirst("a[href*=/Plany/PlanyTokow/]") ?: return@mapNotNull null
                val id = link.attr("href").substringAfterLast('/').toIntOrNull() ?: return@mapNotNull null
                // Columns: EU funding, intake, field of study, specialization, tok.
                val specialty = tr.children().getOrNull(3)?.text()?.replace(' ', ' ')?.trim().orEmpty()
                Target(TargetKind.TOK, id, link.text().trim(), specialty)
            }
        }

    private fun comboItems(html: String, listBoxId: String): List<Option> {
        val start = html.indexOf("'$listBoxId'")
        if (start < 0) return emptyList()
        val itemsStart = html.indexOf("'itemsInfo':[", start)
        val itemsEnd = html.indexOf("]}", itemsStart)
        if (itemsStart < 0 || itemsEnd < 0) return emptyList()
        return parseItems(html.substring(itemsStart, itemsEnd + 1))
    }

    private fun parseItems(text: String): List<Option> =
        Regex("""\{(?:'selected':true,)?'value':(?:'([^']*)'|(-?\d+)),'text':'((?:[^'\\]|\\.)*)'\}""")
            .findAll(text)
            .map { m -> Option(m.groupValues[1].ifEmpty { m.groupValues[2] }, unescapeJs(m.groupValues[3]).trim()) }
            .filter { it.id.isNotEmpty() && it.id != "-1" && it.name.isNotEmpty() }
            .toList()

    private fun unescapeJs(s: String) = s
        .replace(Regex("""\\u([0-9a-fA-F]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\'", "'")
        .replace("\\\\", "\\")

    /**
     * The site only searches for a substring of the group code ("Infor. 1st 1sem WykS"), so "Informatyka 1sem"
     * finds nothing. We send the server the single most informative word, truncated to 5 characters
     * (codes are abbreviated: "Informatyka" → "Infor."), and filter by the remaining words ourselves
     * on code, name, tok and field of study.
     */
    suspend fun searchGroups(query: String): List<Target> = withContext(Dispatchers.IO) {
        val raw = query.lowercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return@withContext emptyList()
        // The server gets the word as typed (diacritics matter there); the local filter ignores diacritics.
        val serverQuery = raw.maxBy { w -> w.length + if (w.any(Char::isLetter)) 100 else 0 }.take(5)
        val words = raw.map { it.fold() }
        val html = post(newSession(), "/Plany/ZnajdzGrupe", FormBody.Builder().add("nazwaGrupy", serverQuery).build())
        Jsoup.parse(html, BASE).select("tr[id^=ZnajdzGrupeGrid_DXDataRow]").mapNotNull { tr ->
            val cells = tr.children()
            val link = cells.getOrNull(1)?.selectFirst("a[href*=/Plany/PlanyGrup/]") ?: return@mapNotNull null
            val id = link.attr("href").substringAfterLast('/').toIntOrNull() ?: return@mapNotNull null
            val haystack = cells.take(4).joinToString(" ") { it.text() }.fold()
            if (words.any { w -> w !in haystack && w.take(5) !in haystack }) return@mapNotNull null
            Target(TargetKind.GROUP, id, link.text().trim(), cells.getOrNull(2)?.text()?.trim().orEmpty())
        }.sortedWith(compareByDescending<Target> { termOrder(it.subtitle) }.thenBy { it.name })
    }

    /** "Surname", "Name Surname" or "Surname Name"; the site treats % as a wildcard. */
    suspend fun searchTeachers(query: String): List<Target> = withContext(Dispatchers.IO) {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return@withContext emptyList()
        val attempts = if (words.size == 1) {
            listOf("" to "${words[0]}%", "${words[0]}%" to "")
        } else {
            listOf(
                words[0] to words.drop(1).joinToString(" ") + "%",
                words.last() to words.dropLast(1).joinToString(" ") + "%",
            )
        }
        for ((first, last) in attempts) {
            val found = teacherSearch(first, last)
            if (found.isNotEmpty()) return@withContext found
        }
        emptyList()
    }

    /** All teachers at once (~950, ~2 MB): "%" as the surname is a match-anything wildcard. */
    suspend fun loadAllTeachers(): List<Target> = withContext(Dispatchers.IO) { teacherSearch("", "%") }

    private fun teacherSearch(firstName: String, lastName: String): List<Target> {
        val html = post(
            newSession(), "/Plany/ZnajdzProwadzacego",
            FormBody.Builder().add("Imie", firstName).add("Nazwisko", lastName).build(),
        )
        return Jsoup.parse(html, BASE).select("table tbody tr").mapNotNull { tr ->
            val link = tr.selectFirst("a[href*=/Plany/PlanyProwadzacych/]") ?: return@mapNotNull null
            val id = link.attr("href").substringAfterLast('/').toIntOrNull() ?: return@mapNotNull null
            val name = tr.selectFirst("td")?.text()?.trim().orEmpty()
            // Cloudflare obfuscates the addresses, but they are easy to decode.
            val email = tr.selectFirst("[data-cfemail]")?.attr("data-cfemail")?.let(::decodeCfEmail).orEmpty()
            Target(TargetKind.TEACHER, id, name, email)
        }
    }

    suspend fun loadRooms(): List<Target> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(get(newSession(), "/Plany/PlanySal"), BASE)
        val onclick = Regex("""WyslijFormularz\('(\d+);([a-z])'\)""")
        // Tree: <li><a onclick="WyslijFormularz('35;b')">building</a><ul><li><a …('338;s')>room</a>…
        doc.select("a[onclick]").filter { onclick.find(it.attr("onclick"))?.groupValues?.get(2) == "b" }
            .flatMap { buildingLink ->
                val buildingName = buildingLink.text().trim()
                val code = buildingName.substringBefore(" - ").trim()
                val li = buildingLink.parent() ?: return@flatMap emptyList()
                li.select("a[onclick]").mapNotNull { a ->
                    val m = onclick.find(a.attr("onclick")) ?: return@mapNotNull null
                    if (m.groupValues[2] != "s") return@mapNotNull null
                    Target(TargetKind.ROOM, m.groupValues[1].toInt(), "$code ${a.text().trim()}", buildingName)
                }
            }
            .distinctBy { it.id }
    }

    private fun semesterRange(page: Document): Pair<LocalDate, LocalDate> {
        // "Cały semestr" radio button: value="2026,9,11\2027,2,7\3" (months are 1-based).
        val value = page.select("input[name=RadioList_Termin]").map { it.attr("value") }
            .firstOrNull { it.endsWith("\\3") }
        val dates = value?.split('\\')?.take(2)?.mapNotNull { part ->
            part.split(',').mapNotNull { it.trim().toIntOrNull() }
                .takeIf { it.size == 3 }
                ?.let { (y, m, d) -> runCatching { LocalDate.of(y, m, d) }.getOrNull() }
        }
        if (dates != null && dates.size == 2) return dates[0] to dates[1]
        val today = LocalDate.now()
        return today.minusMonths(1) to today.plusMonths(5)
    }

    private fun termOrder(tok: String): Int {
        val m = ACADEMIC_TERM.find(tok) ?: return 0
        return m.groupValues[1].toInt() * 2 + if (m.groupValues[2] == "Lato") 1 else 0
    }

    private fun fmt(d: LocalDate) = "${d.year}-${d.monthValue}-${d.dayOfMonth}"

    private fun decodeCfEmail(hex: String): String = runCatching {
        val key = hex.substring(0, 2).toInt(16)
        (2 until hex.length step 2).map { (hex.substring(it, it + 2).toInt(16) xor key).toChar() }.joinToString("")
    }.getOrDefault("")

    private fun newSession(): OkHttpClient = baseClient.newBuilder().cookieJar(MemoryCookieJar()).build()

    private fun get(client: OkHttpClient, path: String) = execute(client, Request.Builder().url(BASE + path).build())

    private fun post(client: OkHttpClient, path: String, body: RequestBody) =
        execute(client, Request.Builder().url(BASE + path).post(body).build())

    private fun execute(client: OkHttpClient, request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }

    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[it.name] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.filter { it.matches(url) }
    }
}
