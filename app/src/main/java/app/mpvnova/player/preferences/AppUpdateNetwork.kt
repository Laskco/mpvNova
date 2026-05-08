package app.mpvnova.player.preferences

import app.mpvnova.player.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal fun AppUpdateManager.fetchLatestRelease(): ReleaseInfo {
    val json = readText(LATEST_RELEASE_URL)
    val releaseJson = JSONObject(json)
    val tagName = releaseJson.requireReleaseTag()

    val assetsJson = releaseJson.getJSONArray("assets")
    val apkAssets = mutableListOf<JSONObject>()
    for (index in 0 until assetsJson.length()) {
        val asset = assetsJson.getJSONObject(index)
        val name = asset.optString("name")
        if (!name.endsWith(".apk", ignoreCase = true))
            continue
        apkAssets.add(asset)
    }

    val selectedAsset = requireApkAsset(apkAssets)
    val downloadUrl = selectedAsset.requireDownloadUrl()

    return ReleaseInfo(
        tagName = tagName,
        name = releaseJson.optString("name").trim(),
        notes = releaseJson.optString("body").trim(),
        assetName = selectedAsset.optString("name").trim(),
        downloadUrl = downloadUrl
    ).also { recordReleaseHistory(it) }
}

internal fun AppUpdateManager.downloadApk(release: ReleaseInfo): File {
    val updatesDir = prepareUpdatesDir()
    val apkFile = File(updatesDir, release.assetName.safeFilePart())
    val connection = openConnection(release.downloadUrl)
    try {
        val responseCode = connection.responseCode
        requireSuccessfulResponse(responseCode, "Download failed")
        connection.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } finally {
        connection.disconnect()
    }

    requireDownloadedApk(apkFile)
    return apkFile
}

private fun JSONObject.requireReleaseTag(): String {
    return optString("tag_name").trim()
        .takeIf { it.isNotBlank() }
        ?: throw IOException("GitHub did not return a release tag")
}

private fun requireApkAsset(apkAssets: List<JSONObject>): JSONObject {
    return chooseBestApkAsset(apkAssets)
        ?: throw IOException("No APK asset was found on the latest release")
}

private fun JSONObject.requireDownloadUrl(): String {
    return optString("browser_download_url").trim()
        .takeIf { it.isNotBlank() }
        ?: throw IOException("The release APK is missing a download URL")
}

private fun AppUpdateManager.prepareUpdatesDir(): File {
    val updatesDir = File(activity.cacheDir, UPDATE_CACHE_DIR)
    if (!updatesDir.exists() && !updatesDir.mkdirs())
        throw IOException("Could not prepare the update cache")
    return updatesDir
}

private fun requireSuccessfulResponse(responseCode: Int, message: String) {
    if (responseCode !in HTTP_SUCCESS_RANGE)
        throw IOException("$message with HTTP $responseCode")
}

private fun requireDownloadedApk(apkFile: File) {
    if (apkFile.length() <= 0L)
        throw IOException("The downloaded APK was empty")
}

private fun readText(url: String): String {
    val connection = openConnection(url)
    try {
        val responseCode = connection.responseCode
        requireSuccessfulResponse(responseCode, "GitHub returned")
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun openConnection(url: String): HttpURLConnection {
    return (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = HTTP_CONNECT_TIMEOUT_MS
        readTimeout = HTTP_READ_TIMEOUT_MS
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "mpvNova/${BuildConfig.VERSION_NAME}")
    }
}
