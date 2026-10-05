package app.plandsw.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** "v1.10.0" > "1.9.3"; missing parts count as zeros, non-numeric suffixes ("-beta") are ignored. */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.')
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** An update, if the release is newer than the current version and contains an APK. */
fun pickUpdate(release: GithubRelease, currentVersion: String): AppUpdate? {
    if (release.draft || release.prerelease) return null
    val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
    if (compareVersions(release.tag, currentVersion) <= 0) return null
    return AppUpdate(release.tag.removePrefix("v"), release.body.orEmpty().trim(), apk.url, apk.size)
}

/** Asks GitHub for the latest release of the app's repository. */
class UpdateChecker(private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun availableUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val release = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub HTTP ${r.code}")
            json.decodeFromString<GithubRelease>(r.body!!.string())
        }
        pickUpdate(release, currentVersion)
    }

    companion object {
        const val REPO = "bavtika/PlanDSW"
    }
}
