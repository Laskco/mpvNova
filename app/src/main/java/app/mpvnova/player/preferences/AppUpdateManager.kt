package app.mpvnova.player.preferences

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.mpvnova.player.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AppUpdateManager(internal val activity: Activity) {
    internal var busyDialog: AlertDialog? = null
    internal var pendingInstallApk: File? = null
    internal var pendingInstallTag: String? = null
    internal var permissionSettingsOpened = false
    private val installLaunchers = mutableMapOf<File, ActivityResultLauncher<Intent>>()

    init {
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                cancelPendingInstall()
                releaseActivityUpdateApks(activity)
                installLaunchers.values.forEach { it.unregister() }
                installLaunchers.clear()
            }
        })
        if (updateInProgress.compareAndSet(false, true)) {
            try {
                cleanupInstalledUpdateIfNeeded()
                cleanupUpdateCache()
            } finally {
                updateInProgress.set(false)
            }
        }
    }

    fun checkForUpdates(
        showIfCurrent: Boolean = true,
        respectIgnored: Boolean = false,
        showProgress: Boolean = true
    ) {
        runUpdateOperation(
            message = if (showProgress) activity.getString(R.string.update_checking) else null,
            operation = { fetchLatestRelease() }
        ) { result ->
            result.fold(
                onSuccess = { release -> showUpdateResult(release, showIfCurrent, respectIgnored) },
                onFailure = { error ->
                    if (showProgress)
                        showError(activity.getString(R.string.update_check_failed, error.cleanMessage()))
                }
            )
        }
    }

    internal fun <T> runUpdateOperation(
        message: String?,
        operation: () -> T,
        onDiscard: (T) -> Unit = {},
        onResult: (Result<T>) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed || !updateInProgress.compareAndSet(false, true))
            return
        var workerStarted = false
        try {
            if (message != null)
                showBusy(message)
            Thread {
                executeUpdateOperation(operation, onDiscard, onResult)
            }.start()
            workerStarted = true
        } finally {
            if (!workerStarted) {
                try {
                    hideBusy()
                } finally {
                    updateInProgress.set(false)
                }
            }
        }
    }

    private fun <T> executeUpdateOperation(
        operation: () -> T,
        onDiscard: (T) -> Unit,
        onResult: (Result<T>) -> Unit,
    ) {
        val result = runCatching(operation)
        var resultPosted = false
        try {
            activity.runOnUiThread { finishUpdateOperation(result, onDiscard, onResult) }
            resultPosted = true
        } finally {
            if (!resultPosted) {
                try {
                    result.onSuccess(onDiscard)
                } finally {
                    updateInProgress.set(false)
                }
            }
        }
    }

    private fun <T> finishUpdateOperation(
        result: Result<T>,
        onDiscard: (T) -> Unit,
        onResult: (Result<T>) -> Unit,
    ) {
        var delivered = false
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                busyDialog = null
                return
            }
            hideBusy()
            onResult(result)
            delivered = true
        } finally {
            try {
                if (!delivered)
                    result.onSuccess(onDiscard)
            } finally {
                updateInProgress.set(false)
            }
        }
    }

    fun resumePendingInstallIfAllowed() {
        val apk = pendingInstallApk ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            val tagName = pendingInstallTag
            pendingInstallApk = null
            pendingInstallTag = null
            permissionSettingsOpened = false
            installDownloadedApk(tagName, apk)
        } else if (permissionSettingsOpened) {
            cancelPendingInstall()
        }
    }

    internal fun cancelPendingInstall() {
        pendingInstallApk?.let(::releaseDownloadedApk)
        pendingInstallApk = null
        pendingInstallTag = null
        permissionSettingsOpened = false
    }

    internal fun launchApkInstaller(tagName: String?, apkFile: File, intent: Intent) {
        val launcher = (activity as? ComponentActivity)?.activityResultRegistry?.register(
            "AppUpdateManager.install.${apkFile.name}",
            ActivityResultContracts.StartActivityForResult(),
        ) {
            finishUpdateInstaller(apkFile)
            installLaunchers.remove(apkFile)?.unregister()
        }
        if (launcher != null)
            installLaunchers[apkFile] = launcher
        retainUpdateInstaller(apkFile)
        var launched = false
        try {
            rememberPendingUpdate(tagName, apkFile)
            if (launcher != null) launcher.launch(intent) else activity.startActivity(intent)
            launched = true
        } finally {
            if (!launched) {
                finishUpdateInstaller(apkFile)
                installLaunchers.remove(apkFile)?.unregister()
            }
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
        showGlassDialog(
            GlassDialogOptions(
                title = activity.getString(R.string.update_history_title),
                notes = activity.getString(R.string.update_history_empty),
            )
        )
    }

    private companion object {
        val updateInProgress = AtomicBoolean(false)
    }
}
