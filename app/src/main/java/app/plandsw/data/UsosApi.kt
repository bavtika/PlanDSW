package app.plandsw.data

import kotlinx.coroutines.Dispatchers
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
    suspend fun fetchGrades(): List<TermGrades> = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(GRADES_URL).build()).execute().use { resp ->
            if (resp.code == 403 || resp.isRedirect) throw UsosLoginRequired()
            if (!resp.isSuccessful) throw IOException("USOS responded with HTTP ${resp.code}")
            UsosGradesParser.parse(resp.body?.string().orEmpty())
        }
    }
}
