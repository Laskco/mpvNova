package app.mpvnova.player

import android.os.SystemClock
import android.util.Log

internal fun MPVActivity.handleMpvEvent(eventId: Int) {
    when (eventId) {
        MpvEvent.MPV_EVENT_END_FILE -> handleMpvEndFile()
        MpvEvent.MPV_EVENT_SHUTDOWN -> {
            markPlaybackEnded()
            finishWithResult(
                if (playbackHasStarted) RESULT_OK else RESULT_CANCELED,
                includeTimePos = true,
            )
        }
        MpvEvent.MPV_EVENT_START_FILE -> handleMpvStartFile()
        MpvEvent.MPV_EVENT_FILE_LOADED -> handleMpvFileLoaded()
        MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> handleMpvPlaybackRestart()
    }
    if (eventId in STREAM_LOADING_DONE_EVENTS &&
        !(eventId == MpvEvent.MPV_EVENT_END_FILE && suppressEndFileFinishForReplace)
    )
        clearStreamLoading()
}

private fun MPVActivity.handleMpvPlaybackRestart() {
    if (firstPlaybackRestartMs == 0L) {
        firstPlaybackRestartMs = SystemClock.uptimeMillis()
    }
    // First frame from the rebuilt decoder — flush A/V/subs via tiny seek, then unpause.
    if (pendingShieldFallbackResync) {
        pendingShieldFallbackResync = false
        eventUiHandler.post(shieldFallbackResyncRunnable)
    }
}

private fun MPVActivity.handleMpvEndFile() {
    // A new external intent that replaced the file stops the outgoing one with an
    // END_FILE. Its progress was saved before switching to the incoming identity.
    val replacedOutgoingFile = synchronized(fileReplacementLock) {
        val replaced = suppressEndFileFinishForReplace
        if (!replaced) {
            onloadCommands.clear()
            capturePlaybackResultSnapshot(updateCompletion = true)
            if (playbackCompletionReached) {
                clearFinishedPositions()
            } else {
                saveResumePosition(resultPositionMs, resultDurationMs)
            }
        }
        psc.eof()
        replaced
    }
    updateMediaSession()
    if (!replacedOutgoingFile) markPlaybackEnded()
    if (!replacedOutgoingFile && shouldFinishExternalPlaybackOnEndFile()) {
        Log.v(
            MPV_ACTIVITY_TAG,
            "external-result: finishing on end-file position=$resultPositionMs " +
                "duration=$resultDurationMs completion=$playbackCompletionReached"
        )
        finishWithResult(RESULT_OK, includeTimePos = true)
    }
}

private fun MPVActivity.handleMpvStartFile() {
    playbackEnded = false
    resetPlaybackResultState()
    audioNormUnderrunHintShown = false
    gpuNextRenderFallbackStage = 0
    gpuNextCopyRetryConfirmed = false
    gpuNextCopyRetryDisplayedFrame = false
    pendingShieldFallbackResync = false
    shieldFallbackResumeAfter = false
    audioFiltersAwaitingPostLoadReconcile = true
    controlsOverlayAutoPaused = false
    resetPlaybackSeekState()
    clearFireTvVideoEdgeCrop()
    cachedChapters = emptyList()
    pendingChapterSeekTime = null
    currentPlayerTitleSource = pendingPlayerTitleSource
    currentItemTitle = pendingItemTitle
    currentFileName = pendingFileName ?: VlcTitleResolver.fileNameFromPathLike(currentMpvPath())
    currentVideoTitle = resolveVlcStyleVideoTitle()
    pendingPlayerTitleSource = null
    pendingItemTitle = null
    pendingFileName = null
    streamOpenLoading = isNetworkStreamPath(currentMpvPath())
    streamCacheLoading = false
    eventUiHandler.post {
        refreshLoadingOverlay()
        updateMetadataDisplay()
        refreshTimeInfoPanelVisibility()
        updatePlayerTitleOverlay()
    }
    applySessionDecoderModeIfNeeded()
    runOnloadCommands()
    applyRememberedVideoAdjustments()
    playbackHasStarted = true
}

private fun MPVActivity.runOnloadCommands() {
    val commands = onloadCommands.toTypedArray()
    // Keep launch options through playlist redirects; mpv resets file-local
    // options between the redirecting entry and its actual media target.
    for (command in commands) {
        // A remote subtitle must not block event delivery or replacement intents.
        // mpv cancels these asynchronous sub-add jobs when their file ends.
        mpvCommand(if (command.firstOrNull() == "sub-add") arrayOf("async", *command) else command)
    }
    if (statsLuaMode > 0 && !playbackHasStarted)
        showConfiguredStatsPage()
}

private fun MPVActivity.handleMpvFileLoaded() {
    onloadCommands.clear()
    applyFireTvVideoEdgeCropIfNeeded()
    applyRememberedTrack("sub")
    applyRememberedTrack("audio")
    guardNearEndStartPosition()
    showResumeToastIfNeeded()
    refreshAudioFiltersAfterFileLoad()
    applyCustomSubtitleStyleOnFileLoad()
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
    isVoiceBoostOn() || isVolumeBoostOn() || isNightModeOn() || isAudioNormOn() ||
        isDownmixOn() || isCenterBoostOn()

private fun MPVActivity.refreshAudioFiltersAfterFileLoad() {
    val filterStateChanged = !persistAudioFilters && anyAudioFilterOn()
    if (!persistAudioFilters) {
        voiceBoostLevel = 0
        volumeBoostDb = 0
        nightModeLevel = 0
        audioNormLevel = 0
        downmixLevel = 0
        centerBoostLevel = 0
    }
    rebuildAudioFilters(force = true)
    if (persistAudioFilters || filterStateChanged) {
        eventUiHandler.post { refreshAllFilterTints() }
    }
    audioFiltersAwaitingPostLoadReconcile = false
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
