package app.mpvnova.player

internal fun MPVActivity.synchronizeSegmentSeek(positionMs: Long) {
    // Replace any queued scrub with the skip destination; the native exact seek is already issued.
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    pendingSeekbarSeekMs = positionMs
    pendingDpadSeekPreviewMs = positionMs
    lastAppliedSeekMs = positionMs
    eventUiHandler.postDelayed(commitSeekbarSeekRunnable, DPAD_SEEK_DEBOUNCE_MS)
    setPlaybackSeekbarProgress(seekbarProgressFromMillis(positionMs))
    updatePlaybackTimeline(positionMs, forceTextUpdate = true)
    updateMinimalSeekOverlay(positionMs)
}

internal fun MPVActivity.applyPlaybackSeek(positionMs: Long) {
    if (positionMs == 0L) {
        // Exact zero can leave some files parked before their first decodable frame.
        // Let mpv resolve the playable start boundary through its normal keyframe seek path.
        mpvCommand(arrayOf("seek", "0", "absolute+keyframes"))
    } else {
        player.timePos = positionMs / MPV_MILLIS_PER_SECOND_DOUBLE
    }
}

internal fun MPVActivity.resetPlaybackSeekState() {
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    userIsOperatingSeekbar = false
    pendingSeekbarSeekMs = null
    pendingDpadSeekPreviewMs = null
    lastDpadSeekApplyMs = 0L
    lastAppliedSeekMs = Long.MIN_VALUE
    firstPlaybackRestartMs = 0L
}
