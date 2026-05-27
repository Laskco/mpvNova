package app.mpvnova.player

import android.util.Log

internal fun MPVActivity.handleMpvEvent(eventId: Int) {
    when (eventId) {
        MpvEvent.MPV_EVENT_END_FILE -> handleMpvEndFile()
        MpvEvent.MPV_EVENT_SHUTDOWN -> finishWithResult(
            if (playbackHasStarted) RESULT_OK else RESULT_CANCELED,
            includeTimePos = true,
        )
        MpvEvent.MPV_EVENT_START_FILE -> handleMpvStartFile()
        MpvEvent.MPV_EVENT_FILE_LOADED -> handleMpvFileLoaded()
        MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> handleMpvPlaybackRestart()
    }
    if (eventId in STREAM_LOADING_DONE_EVENTS)
        clearStreamLoading()
}

private fun MPVActivity.handleMpvPlaybackRestart() {
    // First frame from the rebuilt decoder — flush A/V/subs via tiny seek, then unpause.
    if (pendingShieldFallbackResync) {
        pendingShieldFallbackResync = false
        eventUiHandler.post(shieldFallbackResyncRunnable)
    }
}

private fun MPVActivity.handleMpvEndFile() {
    eofWasReached = true
    clearFinishedPositions()
    psc.eof()
    updateMediaSession()
}

private fun MPVActivity.handleMpvStartFile() {
    resetPlaybackResultState()
    audioNormUnderrunHintShown = false
    gpuNextRenderFallbackStage = 0
    gpuNextCopyRetryConfirmed = false
    gpuNextCopyRetryDisplayedFrame = false
    pendingShieldFallbackResync = false
    shieldFallbackResumeAfter = false
    controlsOverlayAutoPaused = false
    cachedChapters = emptyList()
    pendingChapterSeekTime = null
    currentItemTitle = pendingItemTitle
    currentVideoTitle = resolveVlcStyleVideoTitle()
    pendingItemTitle = null
    pendingFileName = null
    streamOpenLoading = isNetworkStreamPath(currentMpvPath())
    streamCacheLoading = false
    eventUiHandler.post {
        refreshLoadingOverlay()
        updateMetadataDisplay()
    }
    applySessionDecoderModeIfNeeded()
    runOnloadCommands()
    applyRememberedVideoAdjustments()
    playbackHasStarted = true
}

private fun MPVActivity.runOnloadCommands() {
    val commands = onloadCommands.toTypedArray()
    onloadCommands.clear()
    for (command in commands)
        mpvCommand(command)
    if (statsLuaMode > 0 && !playbackHasStarted)
        mpvCommand(arrayOf("script-binding", "stats/display-page-${statsLuaMode}-toggle"))
}

private fun MPVActivity.handleMpvFileLoaded() {
    applyRememberedTrack("sub")
    applyRememberedTrack("audio")
    guardNearEndStartPosition()
    showResumeToastIfNeeded()
    refreshAudioFiltersAfterFileLoad()
    maybeApplyContentRefreshRate()
}

private fun MPVActivity.guardNearEndStartPosition() {
    if (pendingStartPositionMs <= 0L)
        return
    val fileDurationMs = mpvGetPropertyDouble("duration/full")
        ?.times(MPV_MILLIS_PER_SECOND_DOUBLE)
        ?.toLong() ?: 0L
    if (fileDurationMs > 0L && pendingStartPositionMs >= fileDurationMs - RESUME_NEAR_END_MS) {
        Log.v(
            MPV_ACTIVITY_TAG,
            "resume: start position ${pendingStartPositionMs}ms is near " +
                "end of ${fileDurationMs}ms, restarting from beginning"
        )
        mpvCommand(arrayOf("seek", "0", "absolute"))
        pendingResumeToastMs = 0L
    }
    pendingStartPositionMs = 0L
}

private fun MPVActivity.showResumeToastIfNeeded() {
    if (pendingResumeToastMs <= 0L)
        return
    val resumedFrom = formatResumeTime(pendingResumeToastMs)
    Log.v(MPV_ACTIVITY_TAG, "resume: showing toast for $resumedFrom")
    pendingResumeToastMs = 0L
    eventUiHandler.post {
        showToast(
            getString(R.string.resume_toast_title),
            getString(R.string.resume_toast_detail, resumedFrom),
            cancel = true,
            durationMs = RESUME_TOAST_DURATION_MS,
        )
    }
}

private fun MPVActivity.anyAudioFilterOn(): Boolean =
    isVoiceBoostOn() || isVolumeBoostOn() || isNightModeOn() || isAudioNormOn()

private fun MPVActivity.refreshAudioFiltersAfterFileLoad() {
    if (persistAudioFilters) {
        rebuildAudioFilters()
        eventUiHandler.post { refreshAllFilterTints() }
    } else if (anyAudioFilterOn()) {
        voiceBoostLevel = 0
        volumeBoostDb = 0
        nightModeLevel = 0
        audioNormLevel = 0
        rebuildAudioFilters()
        eventUiHandler.post { refreshAllFilterTints() }
    }
}

private fun MPVActivity.clearStreamLoading() {
    streamOpenLoading = false
    streamCacheLoading = false
    eventUiHandler.post { refreshLoadingOverlay() }
}

private val STREAM_LOADING_DONE_EVENTS = setOf(
    MpvEvent.MPV_EVENT_PLAYBACK_RESTART,
    MpvEvent.MPV_EVENT_END_FILE,
    MpvEvent.MPV_EVENT_SHUTDOWN,
)
