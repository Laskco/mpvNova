package app.mpvnova.player

import android.view.View

internal fun MPVActivity.markPlaybackEnded() {
    playbackEnded = true
    eventUiHandler.post {
        binding.timeInfoPanel.setVisibilityIfChanged(View.GONE)
        binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
        clockHandler.removeCallbacks(clockRunnable)
    }
}

internal fun MPVActivity.shouldShowClockWhileControlsHidden(): Boolean {
    return !playbackEnded && !isStatsOverlayVisible() && showClockOnPause && psc.pause
}

internal fun MPVActivity.refreshTimeInfoPanelVisibility() {
    if (playerDialogStack.any { it.isShowing } && !playerTextStylePreviewActive) {
        binding.timeInfoPanel.setVisibilityIfChanged(View.GONE)
        clockHandler.removeCallbacks(clockRunnable)
        return
    }
    val shouldShow = shouldShowTimeInfoPanel()
    if (shouldShow) {
        binding.timeInfoPanel.animate().cancel()
        binding.timeInfoPanel.alpha = 1f
        updateClockInfo(force = true)
    }
    binding.timeInfoPanel.setVisibilityIfChanged(if (shouldShow) View.VISIBLE else View.GONE)
    clockHandler.removeCallbacks(clockRunnable)
    if (shouldShow)
        clockHandler.post(clockRunnable)
}

private fun MPVActivity.shouldShowTimeInfoPanel(): Boolean {
    if (playbackEnded || inPictureInPicture() || isStatsOverlayVisible()) return false
    return (binding.controls.visibility == View.VISIBLE && showClockOverlay) ||
        shouldShowClockWhileControlsHidden()
}

internal fun MPVActivity.isStatsOverlayVisible(): Boolean =
    statsFPS || activeStatsPage in STATS_PAGE_FIRST..STATS_PAGE_LAST
