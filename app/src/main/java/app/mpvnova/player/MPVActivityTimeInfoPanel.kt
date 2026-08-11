package app.mpvnova.player

import android.view.View

internal fun MPVActivity.shouldShowClockWhileControlsHidden(): Boolean {
    return !isStatsOverlayVisible() && showClockOnPause && psc.pause
}

internal fun MPVActivity.refreshTimeInfoPanelVisibility() {
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
    if (inPictureInPicture() || isStatsOverlayVisible()) return false
    return (binding.controls.visibility == View.VISIBLE && showClockOverlay) ||
        shouldShowClockWhileControlsHidden()
}

private fun MPVActivity.isStatsOverlayVisible(): Boolean {
    return activeStatsPage in STATS_PAGE_FIRST..STATS_PAGE_LAST
}
