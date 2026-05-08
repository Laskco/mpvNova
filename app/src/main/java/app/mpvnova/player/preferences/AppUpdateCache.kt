package app.mpvnova.player.preferences

import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun AppUpdateManager.cleanupUpdateCache() {
    val updatesDir = File(activity.cacheDir, UPDATE_CACHE_DIR)
    updatesDir.listFiles()?.forEach { file ->
        if (file.extension.equals("apk", ignoreCase = true))
            file.delete()
    }
}

internal fun AppUpdateManager.rememberPendingUpdate(tagName: String?, apkFile: File) {
    if (tagName.isNullOrBlank())
        return
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putString(PENDING_UPDATE_TAG_KEY, tagName)
        .putString(PENDING_UPDATE_APK_PATH_KEY, apkFile.absolutePath)
        .apply()
}

internal fun AppUpdateManager.cleanupInstalledUpdateIfNeeded() {
    val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    val pendingTag = preferences.getString(PENDING_UPDATE_TAG_KEY, null)?.takeIf { it.isNotBlank() }
        ?: return
    val currentVersion = normalizedVersion(BuildConfig.VERSION_NAME)
    if (!versionsMatch(pendingTag, currentVersion))
        return

    preferences.getString(PENDING_UPDATE_APK_PATH_KEY, null)
        ?.takeIf { it.isNotBlank() }
        ?.let { path -> File(path).delete() }
    cleanupUpdateCache()
    preferences.edit()
        .remove(PENDING_UPDATE_TAG_KEY)
        .remove(PENDING_UPDATE_APK_PATH_KEY)
        .apply()
}

internal fun AppUpdateManager.recordReleaseHistory(release: ReleaseInfo) {
    val existing = releaseHistory()
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

    PreferenceManager.getDefaultSharedPreferences(activity).edit()
        .putString(RELEASE_HISTORY_KEY, result.toString())
        .apply()
}

internal fun AppUpdateManager.releaseHistory(): JSONArray {
    val raw = PreferenceManager.getDefaultSharedPreferences(activity)
        .getString(RELEASE_HISTORY_KEY, null)
        ?: return JSONArray()
    return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
}
