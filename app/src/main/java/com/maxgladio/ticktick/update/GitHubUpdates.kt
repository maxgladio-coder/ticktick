package com.maxgladio.ticktick.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Release tags use vMAJOR.MINOR.PATCH; numeric comparison avoids 1.10 < 1.9. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        fun parse(value: String): AppVersion? {
            val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(value) ?: return null
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(parts[0], parts[1], parts[2])
        }
    }
}

data class AppRelease(val tag: String, val notes: String, val pageUrl: String, val apkUrl: String?)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class ReleaseResponse(val status: Int, val body: String = "")

class GitHubUpdates(private val fetch: () -> ReleaseResponse = ::fetchLatestRelease) {
    fun check(currentVersion: String): UpdateState = try {
        val response = fetch()
        when (response.status) {
            200 -> parseRelease(response.body, currentVersion)
            404 -> UpdateState.Error("暂未找到公开的正式版本，请确认仓库已公开并发布 Release。")
            403, 429 -> UpdateState.Error("GitHub 暂时限制访问，请稍后重试。")
            else -> UpdateState.Error("GitHub 暂时不可用（${response.status}），请稍后重试。")
        }
    } catch (_: IOException) {
        UpdateState.Error("连接 GitHub 失败，请检查网络后重试。")
    } catch (_: Exception) {
        UpdateState.Error("无法识别版本信息，请稍后重试或查看发布页面。")
    }

    private fun parseRelease(body: String, currentVersion: String): UpdateState {
        val json = JSONObject(body)
        require(!json.getBoolean("draft") && !json.getBoolean("prerelease"))
        val tag = json.getString("tag_name")
        val latest = requireNotNull(AppVersion.parse(tag))
        val current = requireNotNull(AppVersion.parse(currentVersion))
        if (latest <= current) return UpdateState.Current
        val page = json.getString("html_url")
        require(isReleaseUrl(page, "tag"))
        val assets = json.optJSONArray("assets")
        val apks = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) &&
                it.optString("state") == "uploaded" && it.optLong("size") > 0 }
        // Releases contain one universal APK. Ambiguous assets go to the release page.
        val apk = apks.singleOrNull()?.optString("browser_download_url")
            ?.takeIf { isReleaseUrl(it, "download") }
        return UpdateState.Available(AppRelease(tag, json.optString("body").take(8000), page, apk))
    }

    companion object {
        const val RELEASES_URL = "https://github.com/maxgladio-coder/ticktick/releases"
        const val API_URL = "https://api.github.com/repos/maxgladio-coder/ticktick/releases/latest"

        private fun isReleaseUrl(value: String, kind: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
                uri.port == -1 && uri.query == null && uri.fragment == null &&
                uri.path.startsWith("/maxgladio-coder/ticktick/releases/$kind/") &&
                uri.normalize() == uri
        }.getOrDefault(false)
    }
}

private fun fetchLatestRelease(): ReleaseResponse {
    val connection = URL(GitHubUpdates.API_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "TickTick-Android")
        val status = connection.responseCode
        if (status != 200) return ReleaseResponse(status)
        val bytes = connection.inputStream.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > 512 * 1024) throw IOException("Response too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return ReleaseResponse(status, bytes.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}
