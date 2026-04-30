package app.mpvnova.player.preferences

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.R
import app.mpvnova.player.databinding.DialogAppUpdateBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(private val activity: Activity) {
    private var busyDialog: AlertDialog? = null
    private var pendingInstallApk: File? = null

    init {
        cleanupInstalledUpdateIfNeeded()
    }

    fun checkForUpdates(
        showIfCurrent: Boolean = true,
        respectIgnored: Boolean = false,
        showProgress: Boolean = true
    ) {
        if (showProgress)
            showBusy(activity.getString(R.string.update_checking))
        Thread {
            val result = runCatching { fetchLatestRelease() }
            runOnUiThread {
                hideBusy()
                result.fold(
                    onSuccess = { release -> showUpdateResult(release, showIfCurrent, respectIgnored) },
                    onFailure = { error ->
                        if (showProgress)
                            showError(activity.getString(R.string.update_check_failed, error.cleanMessage()))
                    }
                )
            }
        }.start()
    }

    fun resumePendingInstallIfAllowed() {
        val apk = pendingInstallApk ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            pendingInstallApk = null
            val tagName = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(PENDING_UPDATE_TAG_KEY, null)
            installDownloadedApk(tagName, apk)
        }
    }

    private fun showUpdateResult(
        release: ReleaseInfo,
        showIfCurrent: Boolean,
        respectIgnored: Boolean
    ) {
        val currentVersion = normalizedVersion(BuildConfig.VERSION_NAME)
        val remoteNewer = isRemoteNewer(release.tagName, currentVersion)
        if (!remoteNewer) {
            if (!showIfCurrent)
                return
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_current_title)
                .setMessage(activity.getString(R.string.update_current_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        val ignoredTag = PreferenceManager.getDefaultSharedPreferences(activity)
            .getString(IGNORED_UPDATE_TAG_KEY, null)
        if (respectIgnored && ignoredTag == release.tagName)
            return

        showAvailableUpdateDialog(release)
    }

    private fun showAvailableUpdateDialog(release: ReleaseInfo) {
        showGlassDialog(
            title = activity.getString(R.string.update_available_title),
            version = activity.getString(R.string.update_new_version, release.tagName),
            releaseTitle = release.displayTitle(),
            notesHeading = activity.getString(R.string.update_notes_heading),
            notes = release.notes.ifBlank { activity.getString(R.string.update_notes_empty) }.cleanMarkdown(),
            primaryText = activity.getString(R.string.update_download),
            ignoreText = activity.getString(R.string.update_ignore),
            onPrimary = { downloadUpdate(release) },
            onIgnore = {
                PreferenceManager.getDefaultSharedPreferences(activity)
                    .edit()
                    .putString(IGNORED_UPDATE_TAG_KEY, release.tagName)
                    .apply()
            }
        )
    }

    private fun downloadUpdate(release: ReleaseInfo) {
        showBusy(activity.getString(R.string.update_downloading, release.assetName))
        Thread {
            val result = runCatching {
                cleanupUpdateCache()
                downloadApk(release)
            }
            runOnUiThread {
                hideBusy()
                result.fold(
                    onSuccess = { file -> showDownloadedUpdateDialog(release, file) },
                    onFailure = { error -> showError(activity.getString(R.string.update_download_failed, error.cleanMessage())) }
                )
            }
        }.start()
    }

    private fun showDownloadedUpdateDialog(release: ReleaseInfo, apkFile: File) {
        showGlassDialog(
            title = activity.getString(R.string.update_available_title),
            version = activity.getString(R.string.update_new_version, release.tagName),
            releaseTitle = activity.getString(R.string.update_download_complete_title),
            notesHeading = release.displayTitle(),
            notes = activity.getString(
                R.string.update_download_complete,
                release.tagName,
                release.assetName
            ),
            primaryText = activity.getString(R.string.update_install),
            onPrimary = {
                installDownloadedApk(release.tagName, apkFile)
            }
        )
    }

    private fun installDownloadedApk(tagName: String?, apkFile: File) {
        if (!apkFile.exists()) {
            showError(activity.getString(R.string.update_download_missing))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            pendingInstallApk = apkFile
            rememberPendingUpdate(tagName, apkFile)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.update_open_permission_settings) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                .show()
            return
        }

        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val uri = FileProvider.getUriForFile(activity, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            rememberPendingUpdate(tagName, apkFile)
            activity.startActivity(installIntent)
        } catch (error: ActivityNotFoundException) {
            showError(activity.getString(R.string.update_installer_missing, error.cleanMessage()))
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val json = readText(LATEST_RELEASE_URL)
        val releaseJson = JSONObject(json)
        val tagName = releaseJson.optString("tag_name").trim()
        if (tagName.isBlank())
            throw IOException("GitHub did not return a release tag")

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
            ?: throw IOException("No APK asset was found on the latest release")
        val downloadUrl = selectedAsset.optString("browser_download_url").trim()
        if (downloadUrl.isBlank())
            throw IOException("The release APK is missing a download URL")

        return ReleaseInfo(
            tagName = tagName,
            name = releaseJson.optString("name").trim(),
            notes = releaseJson.optString("body").trim(),
            assetName = selectedAsset.optString("name").trim(),
            downloadUrl = downloadUrl
        )
    }

    private fun downloadApk(release: ReleaseInfo): File {
        val updatesDir = File(activity.cacheDir, UPDATE_CACHE_DIR)
        if (!updatesDir.exists() && !updatesDir.mkdirs())
            throw IOException("Could not prepare the update cache")

        val apkFile = File(updatesDir, release.assetName.safeFilePart())
        val connection = openConnection(release.downloadUrl)
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299)
                throw IOException("Download failed with HTTP $responseCode")
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (apkFile.length() <= 0L)
            throw IOException("The downloaded APK was empty")
        return apkFile
    }

    private fun readText(url: String): String {
        val connection = openConnection(url)
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299)
                throw IOException("GitHub returned HTTP $responseCode")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "mpvNova/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun cleanupUpdateCache() {
        val updatesDir = File(activity.cacheDir, UPDATE_CACHE_DIR)
        updatesDir.listFiles()?.forEach { file ->
            if (file.extension.equals("apk", ignoreCase = true))
                file.delete()
        }
    }

    private fun rememberPendingUpdate(tagName: String?, apkFile: File) {
        if (tagName.isNullOrBlank())
            return
        PreferenceManager.getDefaultSharedPreferences(activity)
            .edit()
            .putString(PENDING_UPDATE_TAG_KEY, tagName)
            .putString(PENDING_UPDATE_APK_PATH_KEY, apkFile.absolutePath)
            .apply()
    }

    private fun cleanupInstalledUpdateIfNeeded() {
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

    private fun chooseBestApkAsset(assets: List<JSONObject>): JSONObject? {
        if (assets.isEmpty()) return null
        if (assets.size == 1) return assets.first()

        val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        supportedAbis.forEach { abi ->
            val exactMatch = assets.firstOrNull { asset ->
                asset.optString("name").contains(abi, ignoreCase = true)
            }
            if (exactMatch != null)
                return exactMatch
        }

        val universal = assets.firstOrNull { asset ->
            val name = asset.optString("name").lowercase()
            name.contains("universal") || name.contains("all") || name.contains("universal-release")
        }
        if (universal != null)
            return universal

        return assets.firstOrNull { asset ->
            val name = asset.optString("name")
            KNOWN_ABIS.none { abi -> name.contains(abi, ignoreCase = true) }
        } ?: assets.first()
    }

    private fun showBusy(message: String) {
        hideBusy()
        busyDialog = showGlassDialog(
            title = activity.getString(R.string.update_available_title),
            version = null,
            releaseTitle = message,
            notesHeading = null,
            notes = "",
            primaryText = null,
            onPrimary = null,
            showClose = false
        ).apply {
            setCancelable(false)
        }
    }

    private fun hideBusy() {
        busyDialog?.dismiss()
        busyDialog = null
    }

    private fun showError(message: String) {
        showGlassDialog(
            title = activity.getString(R.string.update_error_title),
            version = null,
            releaseTitle = null,
            notesHeading = null,
            notes = message,
            primaryText = null,
            onPrimary = null
        )
    }

    private fun showGlassDialog(
        title: String,
        version: String?,
        releaseTitle: String?,
        notesHeading: String?,
        notes: String,
        primaryText: String?,
        ignoreText: String? = null,
        onPrimary: (() -> Unit)?,
        onIgnore: (() -> Unit)? = null,
        showClose: Boolean = true
    ): AlertDialog {
        lateinit var dialog: AlertDialog
        val binding = DialogAppUpdateBinding.inflate(activity.layoutInflater)
        binding.updateTitle.text = title
        binding.updateVersion.setTextOrGone(version)
        binding.updateReleaseTitle.setTextOrGone(releaseTitle)
        binding.updateNotesHeading.setTextOrGone(notesHeading)
        binding.updateNotes.text = notes
        binding.updateNotesScroll.visibility = if (notes.isBlank()) View.GONE else View.VISIBLE

        binding.updateCloseButton.visibility = if (showClose) View.VISIBLE else View.GONE
        binding.updateCloseButton.setOnClickListener { dialog.dismiss() }

        if (primaryText == null || onPrimary == null) {
            binding.updatePrimaryButton.visibility = View.GONE
        } else {
            binding.updatePrimaryButton.text = primaryText
            binding.updatePrimaryButton.setOnClickListener {
                dialog.dismiss()
                onPrimary()
            }
        }

        if (ignoreText == null || onIgnore == null) {
            binding.updateIgnoreButton.visibility = View.GONE
        } else {
            binding.updateIgnoreButton.text = ignoreText
            binding.updateIgnoreButton.setOnClickListener {
                onIgnore()
                dialog.dismiss()
            }
        }

        binding.updateActions.visibility =
            if (
                binding.updateCloseButton.visibility == View.VISIBLE ||
                binding.updatePrimaryButton.visibility == View.VISIBLE ||
                binding.updateIgnoreButton.visibility == View.VISIBLE
            ) View.VISIBLE else View.GONE

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val focusTarget = when {
                binding.updatePrimaryButton.visibility == View.VISIBLE -> binding.updatePrimaryButton
                binding.updateCloseButton.visibility == View.VISIBLE -> binding.updateCloseButton
                else -> null
            }
            focusTarget?.requestFocus()
        }
        dialog.show()
        return dialog
    }

    private fun runOnUiThread(block: () -> Unit) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed)
                block()
        }
    }

    private fun normalizedVersion(versionName: String): String {
        return versionName.removeSuffix("-oldapi")
    }

    private fun versionsMatch(first: String?, second: String?): Boolean {
        val normalizedFirst = first?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        val normalizedSecond = second?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        return normalizedFirst.isNotBlank() && normalizedFirst == normalizedSecond
    }

    private fun isRemoteNewer(remote: String?, local: String?): Boolean {
        val remoteParts = remote.versionParts()
        val localParts = local.versionParts()
        if (remoteParts.isEmpty() || localParts.isEmpty()) {
            val remoteNormalized = remote?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
            val localNormalized = local?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
            return remoteNormalized.isNotBlank() && localNormalized.isNotBlank() &&
                remoteNormalized != localNormalized
        }

        val max = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until max) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart)
                return remotePart > localPart
        }
        return false
    }

    private fun Throwable.cleanMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    private fun String.cleanMarkdown(): String {
        return lines().joinToString("\n") { line ->
            line.replace("`", "")
                .replace(Regex("^#{1,6}\\s*"), "")
                .trimEnd()
        }.trim()
    }

    private fun String.safeFilePart(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun String?.versionParts(): List<Int> {
        if (isNullOrBlank())
            return emptyList()
        return Regex("\\d+").findAll(trim().removePrefix("v").removePrefix("V"))
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    private fun ReleaseInfo.displayTitle(): String? {
        return name.takeIf { it.isNotBlank() && it != tagName }?.cleanMarkdown()
    }

    private fun android.widget.TextView.setTextOrGone(value: String?) {
        text = value.orEmpty()
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val notes: String,
        val assetName: String,
        val downloadUrl: String
    )

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Laskco/mpvNova/releases/latest"
        private const val UPDATE_CACHE_DIR = "updates"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val IGNORED_UPDATE_TAG_KEY = "ignored_update_tag"
        private const val PENDING_UPDATE_TAG_KEY = "pending_update_tag"
        private const val PENDING_UPDATE_APK_PATH_KEY = "pending_update_apk_path"
        private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}
