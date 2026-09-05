package app.mpvnova.player.preferences

import android.app.Activity
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference

private val updateApkOwners = mutableMapOf<File, WeakReference<Activity>>()
private val installerUpdateApks = mutableMapOf<File, InstallerApkOwner>()
private const val INSTALLER_ORPHAN_GRACE_MS = 7L * 24 * 60 * 60 * 1000

private data class InstallerApkOwner(val activity: WeakReference<Activity>, var retainedAt: Long)

internal fun AppUpdateManager.retainDownloadedApk(apkFile: File): File {
    synchronized(updateApkOwners) {
        if (activity.isFinishing || activity.isDestroyed) {
            apkFile.delete()
        } else {
            updateApkOwners[apkFile] = WeakReference(activity)
        }
    }
    return apkFile
}

internal fun releaseDownloadedApk(apkFile: File) = synchronized(updateApkOwners) {
    updateApkOwners.remove(apkFile)
    if (apkFile !in installerUpdateApks)
        apkFile.delete()
}

internal fun releaseActivityUpdateApks(activity: Activity) = synchronized(updateApkOwners) {
    val files = updateApkOwners.filterValues { it.get() === activity }.keys.toList()
    files.forEach(::releaseDownloadedApk)
    installerUpdateApks.forEach { (file, owner) ->
        if (owner.activity.get() === activity) {
            owner.activity.clear()
            owner.retainedAt = System.currentTimeMillis()
            file.setLastModified(owner.retainedAt)
        }
    }
}

internal fun AppUpdateManager.retainUpdateInstaller(apkFile: File) = synchronized(updateApkOwners) {
    val now = System.currentTimeMillis()
    installerUpdateApks.forEach { (file, owner) ->
        if (owner.activity.get() === activity) {
            owner.activity.clear()
            owner.retainedAt = now
            file.setLastModified(now)
        }
    }
    installerUpdateApks[apkFile] = InstallerApkOwner(WeakReference(activity), now)
    apkFile.setLastModified(now)
    updateApkOwners.remove(apkFile)
}

internal fun AppUpdateManager.finishUpdateInstaller(apkFile: File) = synchronized(updateApkOwners) {
    installerUpdateApks.remove(apkFile)
    apkFile.delete()
    val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    if (preferences.getString(PENDING_UPDATE_APK_PATH_KEY, null) == apkFile.absolutePath) {
        preferences.edit().remove(PENDING_UPDATE_TAG_KEY).remove(PENDING_UPDATE_APK_PATH_KEY).apply()
    }
}

internal fun AppUpdateManager.cleanupUpdateCache(
    now: Long = System.currentTimeMillis(),
) = synchronized(updateApkOwners) {
    installerUpdateApks.keys.removeAll { !it.exists() }
    updateApkOwners.entries.removeAll { (_, owner) ->
        owner.get()?.let { it.isFinishing || it.isDestroyed } != false
    }
    val updatesDir = File(activity.cacheDir, UPDATE_CACHE_DIR)
    updatesDir.listFiles()?.forEach { file ->
        val isRemovable = file.extension.equals("part", ignoreCase = true) ||
            (file.extension.equals("apk", ignoreCase = true) && !isProtectedInstallerApk(file, now))
        if (isRemovable && file !in updateApkOwners) {
            installerUpdateApks.remove(file)
            file.delete()
        }
    }
}

private fun isProtectedInstallerApk(file: File, now: Long): Boolean {
    val installer = installerUpdateApks[file]
    val activity = installer?.activity?.get()
    if (activity?.isDestroyed == true) {
        installer.activity.clear()
        installer.retainedAt = now
        file.setLastModified(now)
    }
    val hasActiveOwner = activity != null && !activity.isDestroyed
    val retainedAt = installer?.retainedAt ?: file.lastModified()
    return hasActiveOwner || now - retainedAt < INSTALLER_ORPHAN_GRACE_MS
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
        ?.let { path ->
            synchronized(updateApkOwners) {
                val file = File(path)
                if (file !in updateApkOwners && !isProtectedInstallerApk(file, System.currentTimeMillis()))
                    file.delete()
            }
        }
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
