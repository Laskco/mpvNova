package app.mpvnova.player

import app.mpvnova.player.preferences.AppUpdateAvailability
import app.mpvnova.player.preferences.ReleaseInfo

private const val IN_PLAYER_UPDATE_CHECK_DELAY_MS = 20_000L

internal fun MPVActivity.scheduleSilentAppUpdateCheck() {
    eventUiHandler.removeCallbacks(silentUpdateCheckRunnable)
    eventUiHandler.postDelayed(silentUpdateCheckRunnable, IN_PLAYER_UPDATE_CHECK_DELAY_MS)
}

internal fun MPVActivity.triggerSilentAppUpdateCheckNow() {
    if (activityIsDestroying || isFinishing || isDestroyed) return
    eventUiHandler.removeCallbacks(silentUpdateCheckRunnable)
    AppUpdateAvailability.checkSilentlyIfStale(applicationContext)
}

internal fun MPVActivity.registerAppUpdateAvailabilityObserver() {
    if (appUpdateListenerRegistered) return
    appUpdateListenerRegistered = true
    AppUpdateAvailability.addListener(appUpdateAvailabilityListener)
    refreshInPlayerUpdateUi()
}

internal fun MPVActivity.unregisterAppUpdateAvailabilityObserver() {
    if (!appUpdateListenerRegistered) return
    appUpdateListenerRegistered = false
    AppUpdateAvailability.removeListener(appUpdateAvailabilityListener)
}

internal fun MPVActivity.refreshInPlayerUpdateUi() {
    if (activityIsDestroying || isDestroyed || !isBindingInitialized) return
    refreshDrawerUpdateState()
}

internal fun MPVActivity.showInPlayerAppUpdatePanel(release: ReleaseInfo) {
    if (activityIsDestroying || isFinishing || isDestroyed) return
    inPlayerUpdateManager.showInPlayerUpdateDialog(release)
}

internal fun MPVActivity.onAppUpdateDialogHostStateChanged(showing: Boolean) {
    if (showing) onAppUpdateDialogShown() else onAppUpdateDialogHidden()
}

private fun MPVActivity.onAppUpdateDialogShown() {
    if (activityIsDestroying || isFinishing || isDestroyed) return
    if (appUpdateDialogRestore == null) {
        appUpdateDialogRestore = pauseForDialog()
    }
    if (playerDialogStack.isEmpty() && playerChromeSnapshot == null && isBindingInitialized) {
        playerChromeSnapshot = capturePlayerChrome()
        applyPlayerDialogChrome(PlayerDialogChrome.HIDE_ALL, animate = false)
    }
}

private fun MPVActivity.onAppUpdateDialogHidden() {
    if (activityIsDestroying || isDestroyed || isFinishing || !activityIsForeground) {
        appUpdateDialogRestore = null
        drawerReopenPending = false
        return
    }
    appUpdateDialogRestore?.let { restore ->
        appUpdateDialogRestore = null
        restore()
    }
    if (drawerReopenPending) {
        reopenDrawerIfPending()
        return
    }
    if (playerDialogStack.isEmpty()) {
        restorePlayerChromeSnapshotIfIdle()
    }
    refreshVisibleControlsTimeout()
}
