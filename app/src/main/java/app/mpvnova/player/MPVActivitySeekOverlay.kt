package app.mpvnova.player

import android.view.KeyEvent
import android.view.View

// ◀/▶ from the hidden state. Minimal-seekbar shows just the slim bar, hide-controls
// shows nothing, and the default opens the full controls.
internal fun MPVActivity.seekFromHiddenControls(ev: KeyEvent) {
    when {
        !playerUiCustomization.seekbarVisible || minimalSeekbarWhileSeeking -> {
            seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
            showMinimalSeekOverlay()
        }
        hideControlsWhileSeeking -> seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
        else -> {
            showControls()
            btnSelected = 0
            // No requestFocus — setDpadSelected drives the highlight; framework
            // focus would trigger window-wide traversal.
            updateSelectedDpadButton()
            seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
        }
    }
}

internal fun MPVActivity.showMinimalSeekOverlay() {
    val durationMs = psc.duration.coerceAtLeast(0L)
    if (durationMs <= 0L)
        return
    val positionMs = (pendingDpadSeekPreviewMs ?: psc.position).coerceIn(0L, durationMs)
    binding.seekOverlayBar.setChapterGapMode(true)
    binding.seekOverlayBar.progress = (positionMs * SEEK_OVERLAY_BAR_MAX / durationMs).toInt()
    binding.seekOverlayTime.setTextIfChanged(
        "${Utils.prettyTime((positionMs / MILLIS_PER_SECOND_LONG).toInt())} / " +
            Utils.prettyTime(psc.durationSec)
    )
    fadeHandler.removeCallbacks(seekOverlayHideRunnable)
    binding.seekOverlay.animate().cancel()
    binding.seekOverlay.setVisibilityIfChanged(View.VISIBLE)
    binding.seekOverlay.alpha = 1f
    fadeHandler.postDelayed(seekOverlayHideRunnable, SEEK_OVERLAY_VISIBLE_MS)
}

internal fun MPVActivity.hideMinimalSeekOverlay() {
    fadeHandler.removeCallbacks(seekOverlayHideRunnable)
    binding.seekOverlay.animate().cancel()
    binding.seekOverlay.setVisibilityIfChanged(View.GONE)
    binding.seekOverlay.alpha = 0f
}
