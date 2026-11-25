package app.mpvnova.player.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_RELEASE_METADATA_BYTES = 4 * 1024 * 1024
private const val MAX_UPDATE_APK_BYTES = 512L * 1024 * 1024
private const val APK_SIZE_LIMIT_ERROR = "The APK exceeds the maximum download size"
private val releaseHistoryLock = Any()

internal fun fetchLatestReleaseForContext(context: Context): ReleaseInfo {
    val appContext = context.applicationContext
    val json = readReleaseMetadata(LATEST_RELEASE_URL)
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

    val selectedAsset = chooseBestApkAsset(apkAssets)
        ?: throw IOException("No compatible release APK was found for this device on the latest release")
    val downloadUrl = selectedAsset.requireDownloadUrl()

    return ReleaseInfo(
        tagName = tagName,
        name = releaseJson.optString("name").trim(),
        notes = releaseJson.optString("body").trim(),
        assetName = selectedAsset.optString("name").trim(),
        downloadUrl = downloadUrl
    ).also { recordReleaseHistoryForContext(appContext, it) }
}

internal fun AppUpdateManager.fetchLatestRelease(): ReleaseInfo {
    return fetchLatestReleaseForContext(activity.applicationContext).also { release ->
        AppUpdateAvailability.recordRelease(release)
    }
}

internal fun recordReleaseHistoryForContext(context: Context, release: ReleaseInfo) {
    synchronized(releaseHistoryLock) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val raw = preferences.getString(RELEASE_HISTORY_KEY, null)
        val existing = if (raw == null) JSONArray() else runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val newest = existing.optJSONObject(0)
        if (newest != null &&
            newest.optString("tag") == release.tagName &&
            newest.optString("name") == release.name &&
            newest.optString("notes") == release.notes
        ) {
            return
        }
        val result = JSONArray()
        result.put(
            JSONObject()
                .put("tag", release.tagName)
                .put("name", release.name)
                .put("notes", release.notes)
                .put("time", System.currentTimeMillis())
        )

        val historyLimit = RELEASE_HISTORY_LIMIT - 1
        val existingItems = (0 until existing.length()).asSequence()
            .mapNotNull { existing.optJSONObject(it) }
            .filter { it.optString("tag") != release.tagName }
            .take(historyLimit)

        for (item in existingItems) {
            result.put(item)
        }

        preferences.edit()
            .putString(RELEASE_HISTORY_KEY, result.toString())
            .apply()
    }
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
    val downloadLimit = expectedLength ?: MAX_UPDATE_APK_BYTES
    val sizeLimitError = if (expectedLength == null) APK_SIZE_LIMIT_ERROR
        else "The downloaded APK exceeded its expected size"
    inputStream.use { input ->
        apkFile.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0)
                    break
                if (count > downloadLimit - downloaded)
                    throw IOException(sizeLimitError)
                output.write(buffer, 0, count)
                downloaded += count
            }
        }
    }
    verifyDownloadedLength(apkFile.length(), expectedLength)
}

private fun verifyDownloadedLength(actualLength: Long, expectedLength: Long?) {
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
    val length = header.toLongOrNull()?.takeIf { length ->
        length >= 0L && header.all { it in '0'..'9' }
    } ?: throw IOException("The download returned an invalid Content-Length")
    if (length > MAX_UPDATE_APK_BYTES) throw IOException(APK_SIZE_LIMIT_ERROR)
    return length
}

private fun readReleaseMetadata(url: String): String {
    val connection = openConnection(url)
    try {
        val responseCode = connection.responseCode
        requireSuccessfulResponse(responseCode, "GitHub returned")
        val declaredLength = connection.getHeaderField("Content-Length")?.trim()?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_RELEASE_METADATA_BYTES)
            throw IOException("GitHub release metadata exceeds the maximum size")
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > MAX_RELEASE_METADATA_BYTES - output.size())
                    throw IOException("GitHub release metadata exceeds the maximum size")
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
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
