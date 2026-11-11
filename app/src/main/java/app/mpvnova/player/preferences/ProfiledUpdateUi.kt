package app.mpvnova.player.preferences

import java.lang.ref.WeakReference

// The installer callback may arrive after Home was pressed. Launch its UI only
// from a resumed activity; Android can silently block a background launch.
private var resumedManager: WeakReference<AppUpdateManager>? = null
private val pendingUpdateUi = ArrayDeque<(AppUpdateManager) -> Unit>()

internal fun AppUpdateManager.resumeProfiledUpdateUi() {
    resumedManager = WeakReference(this)
    deliverProfiledUpdateUi()
}

internal fun AppUpdateManager.pauseProfiledUpdateUi() {
    if (resumedManager?.get() === this) resumedManager = null
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
