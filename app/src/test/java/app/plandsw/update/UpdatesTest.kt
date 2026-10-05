package app.plandsw.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(compareVersions("1.10.0", "1.9.3") > 0)
        assertTrue(compareVersions("v1.1.0", "1.1") == 0)
        assertTrue(compareVersions("1.0.0", "1.0.1") < 0)
        assertTrue(compareVersions("2.0.0-beta", "1.9.9") > 0)
    }

    private val release = Json { ignoreUnknownKeys = true }.decodeFromString<GithubRelease>(
        """
        {"tag_name":"v1.2.0","name":"Plan DSW 1.2.0","body":"New: widget","draft":false,"prerelease":false,
         "assets":[{"name":"PlanDSW-1.2.0.apk","browser_download_url":"https://example.com/a.apk","size":1500000}]}
        """.trimIndent()
    )

    @Test
    fun offersNewerRelease() {
        val update = pickUpdate(release, "1.1.0")!!
        assertEquals("1.2.0", update.version)
        assertEquals("https://example.com/a.apk", update.apkUrl)
        assertEquals("New: widget", update.notes)
    }

    @Test
    fun ignoresSameOrOlderRelease() {
        assertNull(pickUpdate(release, "1.2.0"))
        assertNull(pickUpdate(release, "1.3.0"))
    }

    @Test
    fun ignoresReleaseWithoutApk() {
        assertNull(pickUpdate(release.copy(assets = emptyList()), "1.0.0"))
    }
}
