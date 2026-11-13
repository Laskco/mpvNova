package app.mpvnova.player.preferences

import app.mpvnova.player.R
import java.lang.ref.WeakReference

// The installer callback may arrive after Home was pressed. Launch its UI only
// from a resumed activity; Android can silently block a background launch.
private var resumedManager: WeakReference<AppUpdateManager>? = null
private val pendingUpdateUi = ArrayDeque<(AppUpdateManager) -> Unit>()
private var installSessionId: Int? = null

internal fun AppUpdateManager.resumeProfiledUpdateUi() {
    resumedManager = WeakReference(this)
    restoreProfiledInstallProgress()
    deliverProfiledUpdateUi()
}

internal fun hasProfiledInstallProgress(): Boolean = installSessionId != null

internal fun beginProfiledInstallProgress(sessionId: Int) {
    installSessionId = sessionId
    resumedManager?.get()?.restoreProfiledInstallProgress()
}

internal fun finishProfiledInstallProgress(sessionId: Int) {
    if (installSessionId != sessionId) return
    installSessionId = null
    resumedManager?.get()?.hideProfiledInstallProgress()
}

internal fun AppUpdateManager.hideProfiledInstallProgress() {
    installProgressDialog?.dismiss()
    installProgressDialog = null
}

private fun AppUpdateManager.restoreProfiledInstallProgress() {
    val sessionId = installSessionId
    if (sessionId == null) {
        hideProfiledInstallProgress()
        return
    }
    if (activity.packageManager.packageInstaller.getSessionInfo(sessionId) == null) {
        finishProfiledInstallProgress(sessionId)
        return
    }
    if (installProgressDialog?.isShowing != true) {
        // Session confirmation closes before installation finishes. Restore
        // feedback until the terminal callback or Android replaces this process.
        installProgressDialog = showGlassDialog(
            GlassDialogOptions(
                title = activity.getString(R.string.update_available_title),
                releaseTitle = activity.getString(R.string.update_installing),
                showClose = false,
                showProgress = true,
            )
        ).apply { setCancelable(false) }
    }
}

internal fun AppUpdateManager.pauseProfiledUpdateUi() {
    if (resumedManager?.get() === this) resumedManager = null
    hideProfiledInstallProgress()
}

internal fun queueProfiledUpdateUi(action: (AppUpdateManager) -> Unit) {
    pendingUpdateUi.addLast(action)
    deliverProfiledUpdateUi()
}

private fun deliverProfiledUpdateUi() {
    val manager = resumedManager?.get() ?: return
    if (!manager.activity.isFinishing && !manager.activity.isDestroyed) {
        pendingUpdateUi.removeFirstOrNull()?.invoke(manager)
    }
}
