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
    val partialFile = File.createTempFile("update-", ".part", updatesDir)
    val apkFile = File(updatesDir, "${partialFile.nameWithoutExtension}.apk")
    try {
        val connection = openConnection(release.downloadUrl)
        try {
            connection.setRequestProperty("Accept-Encoding", "identity")
            val responseCode = connection.responseCode
            val encoding = connection.getHeaderField("Content-Encoding")
            val responseError = when {
                responseCode != HttpURLConnection.HTTP_OK -> "Download failed with HTTP $responseCode"
                encoding != null && !encoding.trim().equals("identity", ignoreCase = true) ->
                    "The download returned an unsupported Content-Encoding"
                else -> null
            }
            if (responseError != null)
                throw IOException(responseError)
            val expectedLength = connection.expectedDownloadLength()
            connection.writeDownloadedApk(partialFile, expectedLength)
        } finally {
            connection.disconnect()
        }

        if (apkFile.exists() || !partialFile.renameTo(apkFile))
            throw IOException("Could not save the downloaded APK")
        return retainDownloadedApk(apkFile)
    } finally {
        partialFile.delete()
    }
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
    if (!updatesDir.isDirectory && !updatesDir.mkdirs())
        throw IOException("Could not prepare the update cache")
    return updatesDir
}

private fun requireSuccessfulResponse(responseCode: Int, message: String) {
    if (responseCode !in HTTP_SUCCESS_RANGE)
        throw IOException("$message with HTTP $responseCode")
}

private fun HttpURLConnection.writeDownloadedApk(apkFile: File, expectedLength: Long?) {
    inputStream.use { input ->
        apkFile.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0)
                    break
                if (expectedLength != null && count.toLong() > expectedLength - downloaded)
                    throw IOException("The downloaded APK exceeded its expected size")
                output.write(buffer, 0, count)
                downloaded += count
            }
        }
    }
    val actualLength = apkFile.length()
    val sizeError = when {
        actualLength <= 0L -> "The downloaded APK was empty"
        expectedLength != null && actualLength != expectedLength ->
            "The downloaded APK size did not match " +
                "(expected $expectedLength bytes, received $actualLength)"
        else -> null
    }
    if (sizeError != null)
        throw IOException(sizeError)
}

private fun HttpURLConnection.expectedDownloadLength(): Long? {
    val header = getHeaderField("Content-Length")?.trim() ?: return null
    return header.toLongOrNull()?.takeIf { length ->
        length >= 0L && header.all { it in '0'..'9' }
    } ?: throw IOException("The download returned an invalid Content-Length")
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
