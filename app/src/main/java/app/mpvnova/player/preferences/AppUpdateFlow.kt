package app.mpvnova.player.preferences

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.R
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
        showGlassDialog(
            GlassDialogOptions(
                title = activity.getString(R.string.update_current_title),
                notes = activity.getString(R.string.update_current_message, BuildConfig.VERSION_NAME),
                compactContent = true,
            )
        )
    }
}

internal fun AppUpdateManager.downloadUpdate(release: ReleaseInfo) {
    runUpdateOperation(
        message = activity.getString(R.string.update_downloading, release.assetName),
        operation = {
            cleanupUpdateCache()
            downloadApk(release)
        },
        onDiscard = ::releaseDownloadedApk,
    ) { result ->
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
}

@Suppress("DEPRECATION")
internal fun AppUpdateManager.installDownloadedApk(tagName: String?, apkFile: File) {
    if (!apkFile.exists()) {
        showError(activity.getString(R.string.update_download_missing))
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
        showInstallPermissionDialog(tagName, apkFile)
        return
    }

    try {
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val uri = FileProvider.getUriForFile(activity, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, APK_MIME_TYPE)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launchApkInstaller(tagName, apkFile, installIntent)
    } catch (error: ActivityNotFoundException) {
        showError(activity.getString(R.string.update_installer_missing, error.cleanMessage()))
    } finally {
        releaseDownloadedApk(apkFile)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun AppUpdateManager.showInstallPermissionDialog(tagName: String?, apkFile: File) {
    cancelPendingInstall()
    pendingInstallApk = apkFile
    pendingInstallTag = tagName
    val options = GlassDialogOptions(
        title = activity.getString(R.string.update_install_permission_title),
        notes = activity.getString(R.string.update_install_permission_message),
        primaryText = activity.getString(R.string.update_open_permission_settings),
        onPrimary = {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            permissionSettingsOpened = true
        },
    )
    var shown = false
    try {
        showGlassDialog(options).setOnDismissListener {
            Handler(Looper.getMainLooper()).post {
                if (!permissionSettingsOpened && pendingInstallApk == apkFile)
                    cancelPendingInstall()
            }
        }
        shown = true
    } finally {
        if (!shown)
            cancelPendingInstall()
    }
}
