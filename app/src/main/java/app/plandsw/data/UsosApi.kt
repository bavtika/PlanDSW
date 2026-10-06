package app.plandsw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for USOSweb (usosweb.ideis.pl). It has no sign-in of its own: the session is created by the WebView
 * on the sign-in screen (CAS → Microsoft), and its cookies reach this client through [cookieJar].
 * [userAgent] matches the WebView's, so the session cannot tell app requests from the browser.
 */
class UsosApi(cookieJar: CookieJar, userAgent: String) {
    companion object {
        const val BASE = "https://usosweb.ideis.pl"
        const val LOGIN_URL = "$BASE/kontroler.php?_action=logowaniecas/index"
        const val GRADES_URL = "$BASE/kontroler.php?_action=dla_stud/studia/oceny/index"
        const val TESTS_URL = "$BASE/kontroler.php?_action=dla_stud/studia/sprawdziany/index"
        fun testUrl(id: Int) = "$BASE/kontroler.php?_action=dla_stud/studia/sprawdziany/pokaz&wez_id=$id"

        /** Straight to Microsoft sign-in, skipping the CAS "Zmiana logowania" page with its button — for silent sign-in. */
        val SILENT_LOGIN_URL = "https://login.wsb.pl/cas/clientredirect?client_name=SAML2CASClient&locale=pl&service=" +
            URLEncoder.encode(LOGIN_URL, "UTF-8")

        /** CAS has returned us to USOSweb, no longer on logowaniecas (usually home/index) — sign-in is complete. */
        fun isAfterLogin(url: String): Boolean {
            val host = runCatching { URI(url).host }.getOrNull() ?: return false
            return host == "usosweb.ideis.pl" && !url.contains("logowaniecas")
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Without a session USOSweb returns 403 or redirects to CAS — either way sign-in is needed, not a redirect.
        .followRedirects(false)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept-Language", "pl")
                    .header("User-Agent", userAgent)
                    .build()
            )
        }
        .build()

    /** @throws UsosLoginRequired if there is no session or it has expired. */
    suspend fun fetchGrades(): List<TermGrades> = UsosGradesParser.parse(get(GRADES_URL))

    /**
     * The "Sprawdziany" section: the index, then every subject's tree (4 requests at a time).
     * @throws UsosLoginRequired if there is no session or it has expired.
     */
    suspend fun fetchTests(): List<TermTests> = coroutineScope {
        val index = UsosTestsParser.parseIndex(get(TESTS_URL))
        val limit = Semaphore(4)
        index.map { term ->
            val courses = term.refs.map { ref ->
                async { limit.withPermit { TestCourse(ref.id, ref.code, ref.name, UsosTestsParser.parseCourse(get(testUrl(ref.id)))) } }
            }
            term to courses
        }.map { (term, courses) -> TermTests(term.code, term.title, courses.awaitAll()) }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.code == 403 || resp.isRedirect) throw UsosLoginRequired()
            if (!resp.isSuccessful) throw IOException("USOS responded with HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }
}
