package app.mpvnova.player.preferences

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

internal fun AppUpdateManager.showUpdateResult(
    release: ReleaseInfo,
    showIfCurrent: Boolean,
    respectIgnored: Boolean
) {
    val currentVersion = normalizedVersion(BuildConfig.VERSION_NAME)
    val remoteNewer = isRemoteNewer(release.tagName, currentVersion)
    if (remoteNewer) {
        val ignoredTag = PreferenceManager.getDefaultSharedPreferences(activity)
            .getString(IGNORED_UPDATE_TAG_KEY, null)
        if (!respectIgnored || ignoredTag != release.tagName)
            showAvailableUpdateDialog(release)
    } else if (showIfCurrent) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_current_title)
            .setMessage(activity.getString(R.string.update_current_message, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }
}

internal fun AppUpdateManager.downloadUpdate(release: ReleaseInfo) {
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
                onFailure = { error ->
                    showError(
                        activity.getString(
                            R.string.update_download_failed,
                            error.cleanMessage(),
                        )
                    )
                }
            )
        }
    }.start()
}

internal fun AppUpdateManager.installDownloadedApk(tagName: String?, apkFile: File) {
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

internal fun AppUpdateManager.runOnUiThread(block: () -> Unit) {
    activity.runOnUiThread {
        if (!activity.isFinishing && !activity.isDestroyed)
            block()
    }
}
