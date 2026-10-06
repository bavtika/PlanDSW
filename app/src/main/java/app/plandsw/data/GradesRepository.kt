package app.plandsw.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Grades from USOS: disk cache, signed-in flag and unseen new grades. */
class GradesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("plan", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "grades.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val api by lazy { UsosApi(WebViewCookieJar(), WebSettings.getDefaultUserAgent(context)) }
    private val silentLogin = UsosSilentLogin(context.applicationContext)

    /** The user signed in to USOS and has not signed out (the session itself may have expired). */
    var loggedIn: Boolean
        get() = prefs.getBoolean("usosLoggedIn", false)
        set(value) {
            prefs.edit().putBoolean("usosLoggedIn", value).apply()
        }

    /** New grades the user has not seen yet; newest first, at most 50. */
    var unseen: List<GradeChange>
        get() = prefs.getString("unseenGrades", null)
            ?.let { runCatching { json.decodeFromString<List<GradeChange>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs.edit().putString("unseenGrades", json.encodeToString(value)).apply()
        }

    suspend fun cached(): CachedGrades? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else runCatching { json.decodeFromString<CachedGrades>(file.readText()) }.getOrNull()
    }

    /** @throws UsosLoginRequired if the USOSweb session expired and silent re-login failed. */
    suspend fun refresh(): CachedGrades {
        val old = cached()
        val terms = try {
            api.fetchGrades()
        } catch (e: UsosLoginRequired) {
            // USOSweb session expired — sign in again using the saved Microsoft cookies.
            if (!silentLogin.run()) throw e
            api.fetchGrades()
        }
        // An empty page with a non-empty cache is a parsing failure, not "all grades disappeared".
        if (terms.isEmpty() && !old?.terms.isNullOrEmpty()) {
            throw UnexpectedResponseException("Empty USOS grades while cache has grades")
        }
        // Tests are a second, independent section: if it fails, keep the cached tests and still update grades.
        val tests = try {
            api.fetchTests()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val fresh = CachedGrades(
            fetchedAt = System.currentTimeMillis(),
            terms = terms,
            tests = tests ?: old?.tests.orEmpty(),
            testsLoaded = tests != null || old?.testsLoaded == true,
        )
        val changes = GradesDiff.changes(old?.terms, terms) +
            if (tests != null) GradesDiff.testChanges(old?.takeIf { it.testsLoaded }?.tests, tests) else emptyList()
        withContext(Dispatchers.IO) { file.writeText(json.encodeToString(fresh)) }
        if (changes.isNotEmpty()) {
            val keys = changes.map { it.key }.toSet()
            unseen = (changes + unseen.filterNot { it.key in keys }).take(50)
        }
        return fresh
    }

    fun logout() {
        loggedIn = false
        unseen = emptyList()
        file.delete()
        // The in-app WebView is only used for USOS sign-in — clear all its cookies (USOSweb, CAS, Microsoft).
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
