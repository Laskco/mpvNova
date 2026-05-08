package app.mpvnova.player.preferences

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.R
import java.io.File

class AppUpdateManager(internal val activity: Activity) {
    internal var busyDialog: AlertDialog? = null
    internal var pendingInstallApk: File? = null

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

    fun showReleaseHistory() {
        val history = releaseHistory()
        if (history.length() == 0) {
            showEmptyReleaseHistoryDialog()
            return
        }

        val body = buildString {
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                val tag = item.optString("tag")
                    .ifBlank { activity.getString(R.string.update_history_unknown_version) }
                val title = item.optString("name")
                    .takeIf { it.isNotBlank() && it != tag }
                append(tag)
                if (title != null)
                    append(" - ").append(title.cleanMarkdown())
                append("\n\n")
                append(item.optString("notes").ifBlank {
                    activity.getString(R.string.update_notes_empty)
                }.cleanMarkdown())
                if (index != history.length() - 1)
                    append("\n\n---\n\n")
            }
        }

        showGlassDialog(
            GlassDialogOptions(
                title = activity.getString(R.string.update_history_title),
                notes = body,
            )
        )
    }

    private fun showEmptyReleaseHistoryDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_history_title)
            .setMessage(R.string.update_history_empty)
            .setPositiveButton(R.string.update_close, null)
            .show()
    }
}
