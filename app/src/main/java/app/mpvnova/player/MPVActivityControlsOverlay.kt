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

internal fun MPVActivity.showControls() {
    val controlsWereVisible = binding.controls.visibility == View.VISIBLE
    fadeHandler.removeCallbacks(fadeRunnable)
    resetControlsAlphaIfNeeded(controlsWereVisible)
    if (!controlsWereVisible) {
        performFirstShowSetup()
    }
    updateClockInfo(force = !controlsWereVisible)
    // Defer dpad-focus update only on first show — pre-layout. Skipping the
    // post when already visible is critical during fast dpad nav (each
    // ACTION_DOWN/UP would starve SW Hi10p decode).
    if (!controlsWereVisible && btnSelected != -1) {
        binding.controls.post {
            if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
                updateSelectedDpadButton()
            }
        }
    }
    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

private fun MPVActivity.resetControlsAlphaIfNeeded(controlsWereVisible: Boolean) {
    val needReset = !controlsWereVisible ||
        fadeRunnable.hasStarted ||
        binding.controls.alpha < 1f ||
        binding.topControls.alpha < 1f ||
        binding.playerTitleOverlay.alpha < 1f ||
        binding.controlsScrim.alpha < 1f ||
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
    maybeAutoPauseForControlsOverlay()
    binding.controls.setVisibilityIfChanged(View.VISIBLE)
    binding.topControls.setVisibilityIfChanged(View.VISIBLE)
    binding.controlsScrim.setVisibilityIfChanged(View.VISIBLE)
    binding.timeInfoPanel.setVisibilityIfChanged(
        if (showClockOverlay) View.VISIBLE else View.GONE
    )
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
    updatePlayerToastPlacement()
    clockHandler.removeCallbacks(clockRunnable)
    // Don't tick the clock when its overlay is off.
    if (showClockOverlay)
        clockHandler.post(clockRunnable)
}

internal fun MPVActivity.refreshVisibleControlsTimeout() {
    fadeHandler.removeCallbacks(fadeRunnable)
    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

internal fun MPVActivity.keepVisibleControlsFresh() {
    val controlsAreVisible = binding.controls.visibility == View.VISIBLE
    val controlsAreOpaque =
        binding.controls.alpha >= 1f &&
        binding.topControls.alpha >= 1f &&
        binding.playerTitleOverlay.alpha >= 1f &&
        binding.controlsScrim.alpha >= 1f
    if (controlsAreVisible && controlsAreOpaque && !fadeRunnable.hasStarted) {
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
    binding.timeInfoPanel.setVisibilityIfChanged(View.GONE)
    binding.statsTextView.setVisibilityIfChanged(View.GONE)
    updatePlayerToastPlacement()
    clockHandler.removeCallbacks(clockRunnable)

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
    } else if (binding.controls.visibility == View.VISIBLE && !fadeRunnable.hasStarted) {
            hideControlsFade()
            false
    } else {
        showControls()
        true
    }
}
