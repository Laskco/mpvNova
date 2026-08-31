package app.mpvnova.player

import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal fun MPVActivity.controlsShouldBeVisible(): Boolean {
    return userIsOperatingSeekbar
}

internal fun MPVActivity.shouldAutoHideControls(): Boolean {
    return controlsDisplayTimeoutMs > 0L &&
            !controlsShouldBeVisible() &&
            !(keepControlsVisibleWhilePaused && psc.pause)
}

private fun MPVActivity.controlsOverlayIsFullyVisible(): Boolean {
    return binding.controls.visibility == View.VISIBLE &&
        (topActionsInPlayerBar || binding.topControls.visibility == View.VISIBLE) &&
        !fadeRunnable.hasStarted
}

internal fun MPVActivity.showControls() {
    if (playerDialogStack.any { it.isShowing }) return
    val overlayNeedsRestore = !controlsOverlayIsFullyVisible()
    fadeHandler.removeCallbacks(fadeRunnable)
    resetControlsAlphaIfNeeded(overlayNeedsRestore)
    if (overlayNeedsRestore) {
        performFirstShowSetup()
    }
    updateClockInfo(force = overlayNeedsRestore)
    // Defer the dpad selection refresh only when layout visibility changed.
    // Posting for every key event would starve SW Hi10p decode during fast navigation.
    if (overlayNeedsRestore && btnSelected != -1) {
        binding.controls.post {
            if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
                updateSelectedDpadButton()
            }
        }
    }
    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

private fun MPVActivity.resetControlsAlphaIfNeeded(overlayNeedsRestore: Boolean) {
    val needReset = overlayNeedsRestore ||
        binding.controls.alpha < 1f ||
        binding.topControls.alpha < 1f ||
        binding.playerTitleOverlay.alpha < 1f ||
        (playerControlsScrimEnabled() && binding.controlsScrim.alpha < 1f) ||
        binding.timeInfoPanel.alpha < 1f ||
        binding.statsTextView.alpha < 1f
    if (!needReset) return
    // Cancel pending fade animators or they'll keep overwriting our alpha.
    binding.controls.animate().setListener(null).cancel()
    binding.topControls.animate().setListener(null).cancel()
    binding.playerTitleOverlay.animate().setListener(null).cancel()
    binding.controlsScrim.animate().cancel()
    binding.timeInfoPanel.animate().cancel()
    binding.statsTextView.animate().cancel()
    binding.controls.alpha = 1f
    binding.topControls.alpha = 1f
    binding.playerTitleOverlay.alpha = 1f
    binding.controlsScrim.alpha = 1f
    binding.timeInfoPanel.alpha = 1f
    binding.statsTextView.alpha = 1f
    fadeRunnable.hasStarted = false
}

private fun MPVActivity.performFirstShowSetup() {
    // hidden → visible. Autopause first so the decoder gets full CPU/GPU
    // for the overlay composition (Hi10p SW + alpha over SurfaceView drifts).
    hideMinimalSeekOverlay()
    maybeAutoPauseForControlsOverlay()
    binding.controls.setVisibilityIfChanged(View.VISIBLE)
    binding.topControls.setVisibilityIfChanged(
        if (topActionsInPlayerBar) View.GONE else View.VISIBLE,
    )
    // The player panel already supplies its own contrast. A second, full-width
    // translucent scrim forces the Shield to blend a large region over video.
    binding.controlsScrim.setVisibilityIfChanged(
        if (playerControlsScrimEnabled()) View.VISIBLE else View.GONE,
    )
    refreshTimeInfoPanelVisibility()
    updatePlayerTitleOverlay()
    if (statsFPS) {
        updateStats()
        binding.statsTextView.setVisibilityIfChanged(View.VISIBLE)
    }
    // TV has no system bars — the call is semantically a no-op but still
    // triggers a window-decor update → SurfaceFlinger hitch → Hi10p underrun.
    if (!isTvUiMode) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.navigationBars())
    }
    updatePlaybackTimeline(psc.position, forceTextUpdate = true)
    scheduleSubtitleControlsPositionUpdate()
    updatePlayerToastPlacement()
    refreshSkipButtonVisibility()
}

internal fun MPVActivity.refreshVisibleControlsTimeout() {
    fadeHandler.removeCallbacks(fadeRunnable)
    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

internal fun MPVActivity.keepVisibleControlsFresh() {
    val controlsAreOpaque =
        binding.controls.alpha >= 1f &&
        (topActionsInPlayerBar || binding.topControls.alpha >= 1f) &&
        binding.playerTitleOverlay.alpha >= 1f &&
        (!playerControlsScrimEnabled() || binding.controlsScrim.alpha >= 1f)
    if (controlsOverlayIsFullyVisible() && controlsAreOpaque) {
        refreshVisibleControlsTimeout()
    } else {
        showControls()
    }
}

internal fun MPVActivity.hideControls() {
    if (controlsShouldBeVisible())
        return
    // No auto-resume — overlay autopause requires a manual play press.
    controlsOverlayAutoPaused = false
    if (btnSelected != -1) {
        btnSelected = -1
        updateSelectedDpadButton()
    }
    binding.playbackSeekbar.clearFocus()
    // use GONE here instead of INVISIBLE (which makes more sense) because of Android bug with surface views
    // see http://stackoverflow.com/a/12655713/2606891
    binding.controls.setVisibilityIfChanged(View.GONE)
    binding.topControls.setVisibilityIfChanged(View.GONE)
    binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
    binding.controlsScrim.setVisibilityIfChanged(View.GONE)
    binding.statsTextView.setVisibilityIfChanged(View.GONE)
    updateSubtitleControlsPosition()
    refreshTimeInfoPanelVisibility()
    updatePlayerToastPlacement()
    refreshSkipButtonVisibility()

    // Skip on TV — see performFirstShowSetup() for the SurfaceFlinger hitch.
    if (!isTvUiMode) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

internal fun MPVActivity.hideControlsFade() {
    fadeHandler.removeCallbacks(fadeRunnable)
    fadeHandler.post(fadeRunnable)
}

internal fun MPVActivity.toggleControls(): Boolean {
    return if (controlsShouldBeVisible()) {
        true
    } else if (controlsOverlayIsFullyVisible()) {
            hideControlsFade()
            false
    } else {
        showControls()
        true
    }
}
