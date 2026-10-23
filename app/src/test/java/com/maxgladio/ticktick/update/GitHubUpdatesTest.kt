package com.maxgladio.ticktick.update

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GitHubUpdatesTest {
    private fun release(tag: String = "v1.10.0", apk: String = "https://github.com/maxgladio-coder/ticktick/releases/download/$tag/ticktick.apk") = """
        {"tag_name":"$tag","draft":false,"prerelease":false,"body":"更新说明",
         "html_url":"https://github.com/maxgladio-coder/ticktick/releases/tag/$tag",
         "assets":[{"name":"ticktick.apk","state":"uploaded","size":100,"browser_download_url":"$apk"}]}
    """.trimIndent()
    private fun check(body: String, current: String = "1.9.0") =
        GitHubUpdates { ReleaseResponse(200, body) }.check(current)

    @Test fun numericVersionsAndInvalidTags() {
        assertTrue(AppVersion.parse("v1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        listOf("latest", "1.0", "1.2.3-beta", "1.02.3", "999999999999.0.0").forEach {
            assertNull(AppVersion.parse(it))
        }
    }
    @Test fun newerReleaseHasNotesAndApk() {
        val state = check(release()) as UpdateState.Available
        assertEquals("v1.10.0", state.release.tag)
        assertEquals("更新说明", state.release.notes)
        assertTrue(state.release.apkUrl!!.endsWith("ticktick.apk"))
    }
    @Test fun equalOrOlderReleaseNeverOffersDowngrade() {
        assertEquals(UpdateState.Current, check(release(), "1.10.0"))
        assertEquals(UpdateState.Current, check(release(), "2.0.0"))
    }
    @Test fun missingOrUnsafeApkFallsBackToReleasePage() {
        val noAssets = release().replace(Regex("\"assets\":\\[.*]"), "\"assets\":[]")
        assertNull((check(noAssets) as UpdateState.Available).release.apkUrl)
        for (url in listOf("http://github.com/maxgladio-coder/ticktick/releases/download/v1/a.apk",
            "https://evil.example/a.apk", "https://github.com/other/repo/releases/download/v1/a.apk")) {
            assertNull((check(release(apk = url)) as UpdateState.Available).release.apkUrl)
        }
    }
    @Test fun unpublishedOrMalformedMetadataIsNotReportedAsCurrent() {
        for (body in listOf("not json", release("latest"), release().replace("\"draft\":false", "\"draft\":true"),
            release().replace("\"prerelease\":false", "\"prerelease\":true"),
            release().replace("releases/tag/", "issues/"))) {
            assertTrue(check(body) is UpdateState.Error)
        }
    }
    @Test fun failuresReturnActionableErrors() {
        assertTrue((GitHubUpdates { ReleaseResponse(404) }.check("1.0.0") as UpdateState.Error).message.contains("公开"))
        assertTrue((GitHubUpdates { ReleaseResponse(403) }.check("1.0.0") as UpdateState.Error).message.contains("限制"))
        assertTrue(GitHubUpdates { ReleaseResponse(500) }.check("1.0.0") is UpdateState.Error)
        assertTrue((GitHubUpdates { throw IOException() }.check("1.0.0") as UpdateState.Error).message.contains("网络"))
    }
}
