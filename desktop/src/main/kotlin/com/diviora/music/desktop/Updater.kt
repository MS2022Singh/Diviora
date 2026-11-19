package com.diviora.music.desktop

import com.diviora.music.core.catalog.UpdateInfo
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Desktop update check: reads version.json from the latest release; the user downloads the new installer. */
object Updater {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build()
    val installedCode: Int get() = System.getProperty("diviora.versionCode")?.toIntOrNull() ?: 0

    fun check(url: String = System.getProperty("diviora.updateUrl")?.ifBlank { null } ?: UpdateInfo.DEFAULT_URL): UpdateInfo? = runCatching {
        if (installedCode == 0) return null // running from source: no updates
        val res = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).header("Cache-Control", "no-cache").build(),
            HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) return null
        UpdateInfo.parse(res.body())?.takeIf { it.isNewerThan(installedCode) }
    }.getOrNull()
}
