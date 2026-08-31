package app.mpvnova.player

import android.view.View
import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.capturePlayerChrome() = PlayerChromeSnapshot(
    controlsVisibility = binding.controls.visibility,
    controlsScrimVisibility = binding.controlsScrim.visibility,
    topControlsVisibility = binding.topControls.visibility,
    titleVisibility = binding.playerTitleOverlay.visibility,
    timeInfoVisibility = binding.timeInfoPanel.visibility,
    statsVisibility = binding.statsTextView.visibility,
    skipVisibility = binding.skipSegmentBtn.visibility,
)

internal fun MPVActivity.applyPlayerDialogChrome(
    chrome: PlayerDialogChrome,
    animate: Boolean = false,
) {
    fadeHandler.removeCallbacks(fadeRunnable)
    val hiddenViews = buildList {
        add(binding.topControls)
        add(binding.statsTextView)
        add(binding.skipSegmentBtn)
        if (chrome != PlayerDialogChrome.CONTROLS_PREVIEW) {
            add(binding.controls)
            add(binding.controlsScrim)
        }
        if (chrome != PlayerDialogChrome.TITLE_AND_CLOCK_PREVIEW) {
            add(binding.playerTitleOverlay)
            add(binding.timeInfoPanel)
        }
    }
    hiddenViews.forEach { view ->
        view.animate().setListener(null).cancel()
        if (animate && view.visibility == View.VISIBLE) {
            view.animate()
                .alpha(0f)
                .setDuration(PLAYER_CHROME_FADE_DURATION_MS)
                .withEndAction {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    updateSubtitleControlsPosition()
                }
                .start()
        } else {
            view.visibility = View.GONE
            view.alpha = 1f
        }
    }
    updateSubtitleControlsPosition()
}

internal fun MPVActivity.onPlayerDialogDetached(dialog: AlertDialog) {
    playerDialogStack.remove(dialog)
    playerDialogStack.removeAll { !it.isShowing }
    topPlayerDialog = playerDialogStack.lastOrNull { it.isShowing }
    if (playerDialogStack.isNotEmpty() || drawerReopenPending || trackPanelChildTransition) return

    val snapshot = playerChromeSnapshot ?: return
    playerChromeSnapshot = null
    binding.controls.visibility = snapshot.controlsVisibility
    binding.controlsScrim.visibility = snapshot.controlsScrimVisibility
    binding.topControls.visibility = snapshot.topControlsVisibility
    binding.playerTitleOverlay.visibility = snapshot.titleVisibility
    binding.timeInfoPanel.visibility = snapshot.timeInfoVisibility
    binding.statsTextView.visibility = snapshot.statsVisibility
    binding.skipSegmentBtn.visibility = snapshot.skipVisibility
    binding.controls.alpha = 1f
    binding.controlsScrim.alpha = 1f
    binding.topControls.alpha = 1f
    binding.playerTitleOverlay.alpha = 1f
    binding.timeInfoPanel.alpha = 1f
    binding.statsTextView.alpha = 1f
    applyPlayerControlOrderAndVisibility()
    parkPlayerFrameworkFocus()
    updateSubtitleControlsPosition()
    refreshSkipButtonVisibility()
    refreshVisibleControlsTimeout()
}

internal fun MPVActivity.reopenDrawerIfNoParentDialog(dialog: AlertDialog) {
    if (playerDialogStack.any { it !== dialog && it.isShowing }) return
    reopenDrawerIfPending()
}
private const val PLAYER_CHROME_FADE_DURATION_MS = 140L
