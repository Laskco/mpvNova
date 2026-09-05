package app.mpvnova.player

import android.os.SystemClock
import android.view.KeyEvent

internal fun MPVActivity.seekbarProgressFromMillis(positionMs: Long): Int {
    val scaled = positionMs.coerceAtLeast(0L) * SEEK_BAR_PRECISION / MILLIS_PER_SECOND_LONG
    return scaled.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal fun MPVActivity.millisFromSeekbarProgress(progress: Int): Long {
    return progress.toLong() * MILLIS_PER_SECOND_LONG / SEEK_BAR_PRECISION
}

internal fun MPVActivity.seekPlaybackFromDpad(deltaMs: Long, baseOnVisibleSeekbar: Boolean = false) {
    val durationMs = psc.duration.coerceAtLeast(0L)
    if (durationMs <= 0L)
        return
    val isNewDpadSeek = pendingDpadSeekPreviewMs == null
    val displayedPositionMs = if (baseOnVisibleSeekbar && binding.playbackSeekbar.max > 0) {
        millisFromSeekbarProgress(binding.playbackSeekbar.progress)
    } else {
        psc.position
    }
    val currentPositionMs = (
        pendingDpadSeekPreviewMs
            ?: pendingSeekbarSeekMs
            ?: displayedPositionMs
    ).coerceAtLeast(0L)
    val newPositionMs = (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
    if (isNewDpadSeek) {
        lastDpadSeekApplyMs = 0L
        lastAppliedSeekMs = Long.MIN_VALUE
    }
    pendingDpadSeekPreviewMs = newPositionMs
    pendingSeekbarSeekMs = newPositionMs
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    eventUiHandler.postDelayed(commitSeekbarSeekRunnable, DPAD_SEEK_DEBOUNCE_MS)
    setPlaybackSeekbarProgress(seekbarProgressFromMillis(newPositionMs))
    updatePlaybackTimeline(newPositionMs, forceTextUpdate = true)

    val now = SystemClock.uptimeMillis()
    val playbackHasSettled = firstPlaybackRestartMs > 0L &&
        now - firstPlaybackRestartMs >= PLAYBACK_START_SEEK_SETTLE_MS
    if (playbackHasSettled && now - lastDpadSeekApplyMs >= DPAD_SEEK_APPLY_INTERVAL_MS) {
        lastDpadSeekApplyMs = now
        if (lastAppliedSeekMs != newPositionMs) {
            lastAppliedSeekMs = newPositionMs
            applyPlaybackSeek(newPositionMs)
        }
    }
}

internal fun MPVActivity.scheduleSeekbarSeek(positionMs: Long) {
    pendingSeekbarSeekMs = positionMs
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    if (userIsOperatingSeekbar) {
        eventUiHandler.postDelayed(commitSeekbarSeekRunnable, SEEKBAR_SEEK_DEBOUNCE_MS)
    } else {
        commitPendingSeekbarSeek()
    }
}

internal fun MPVActivity.commitPendingSeekbarSeek() {
    val positionMs = pendingSeekbarSeekMs ?: return
    pendingSeekbarSeekMs = null
    pendingDpadSeekPreviewMs = null
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    lastDpadSeekApplyMs = 0L
    if (lastAppliedSeekMs != positionMs) {
        lastAppliedSeekMs = positionMs
        applyPlaybackSeek(positionMs)
    }
}

internal fun MPVActivity.seekDeltaFromDpadEvent(ev: KeyEvent): Long {
    val direction = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1L else -1L
    // Single press = the user-configurable seek step; holding ramps to the fast-scrub tiers.
    val magnitudeMs = when {
        ev.repeatCount >= SEEK_FAST_REPEAT_THRESHOLD -> SEEK_FAST_STEP_MS
        ev.repeatCount >= SEEK_MEDIUM_REPEAT_THRESHOLD -> SEEK_MEDIUM_STEP_MS
        ev.repeatCount >= SEEK_SLOW_REPEAT_THRESHOLD -> SEEK_SLOW_STEP_MS
        else -> seekStepMs
    }
    return direction * magnitudeMs
}

internal fun MPVActivity.setPlaybackSeekbarProgress(progress: Int) {
    if (binding.playbackSeekbar.progress != progress)
        binding.playbackSeekbar.progress = progress
    lastSeekbarProgress = progress
    lastSeekbarUiUpdateMs = SystemClock.uptimeMillis()
}

internal fun MPVActivity.updatePlaybackTimeline(positionMs: Long, forceTextUpdate: Boolean = false) {
    if (!userIsOperatingSeekbar) {
        val progress = seekbarProgressFromMillis(positionMs)
        val now = SystemClock.uptimeMillis()
        val shouldUpdateSeekbar = forceTextUpdate ||
                progress == 0 ||
                progress == binding.playbackSeekbar.max ||
                now - lastSeekbarUiUpdateMs >= PLAYER_SEEKBAR_UI_INTERVAL_MS
        if (shouldUpdateSeekbar && progress != lastSeekbarProgress)
            setPlaybackSeekbarProgress(progress)
    }
    updatePlaybackText((positionMs / MILLIS_PER_SECOND_LONG).toInt().coerceAtLeast(0), force = forceTextUpdate)
}

internal fun MPVActivity.updatePlaybackText(position: Int, force: Boolean = false) {
    if (!force && lastDisplayedPlaybackSecond == position)
        return
    lastDisplayedPlaybackSecond = position
    renderPlayerBarTime(position, psc.durationSec)

    // Skip secondary UI work while scrubbing — decoder is busy with the seek.
    // Clock + "Ends at" panel has its own 30s heartbeat.
    if (!userIsOperatingSeekbar && pendingDpadSeekPreviewMs == null)
        updateStats()
}
