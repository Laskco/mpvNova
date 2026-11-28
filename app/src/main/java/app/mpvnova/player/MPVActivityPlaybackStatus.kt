package app.mpvnova.player

import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

internal fun MPVActivity.updatePlaybackDuration(durationMs: Long) {
    val duration = (durationMs / MPV_MILLIS_PER_SECOND_FLOAT).roundToInt()
    renderPlayerBarTime(lastDisplayedPlaybackSecond.takeIf { it >= 0 } ?: psc.positionSec, duration)

    val seekbarMax = seekbarProgressFromMillis(durationMs)
    val seekbarMaxChanged = !userIsOperatingSeekbar && binding.playbackSeekbar.max != seekbarMax
    if (seekbarMaxChanged)
        binding.playbackSeekbar.max = seekbarMax
    updatePlaybackBuffer()
    if (duration > 0 && seekbarMaxChanged)
        updateChapterMarkers()
    if (binding.timeInfoPanel.visibility == View.VISIBLE)
        updateClockInfo()
}

internal fun MPVActivity.handlePauseUi(paused: Boolean) {
    updatePlaybackStatus(paused)
    // Unpause + overlay still up + autopause-eligible → hide overlay.
    // Otherwise the autopause condition we just left would immediately
    // re-apply. Property-driven so it covers every unpause path.
    if (!paused &&
        binding.controls.visibility == View.VISIBLE &&
        shouldAutoPauseForControlsOverlay()) {
        controlsOverlayAutoPaused = false
        hideControlsFade()
    }
    onScreensaverPauseChanged(paused)
    if (paused) triggerSilentAppUpdateCheckNow()
}

internal fun MPVActivity.togglePauseFromUser() {
    controlsOverlayAutoPaused = false
    val currentlyPaused = player.paused ?: psc.pause
    if (!currentlyPaused && keepControlsVisibleWhilePaused) {
        // Set pause directly before composing the overlay. Waiting for mpv's
        // property callback made the pause and persistent controls arrive late.
        player.paused = true
        showControls()
    } else {
        player.cyclePause()
    }
}

internal fun MPVActivity.updatePlaybackStatus(paused: Boolean) {
    val r = if (paused) R.drawable.ic_play_arrow_black_24dp else R.drawable.ic_pause_black_24dp
    if (lastPlayButtonIconRes != r) {
        val drawable = if (paused) {
            cachedPlayDrawable ?: ContextCompat.getDrawable(this, r).also { cachedPlayDrawable = it }
        } else {
            cachedPauseDrawable ?: ContextCompat.getDrawable(this, r).also { cachedPauseDrawable = it }
        }
        binding.playBtn.setImageDrawable(drawable)
        lastPlayButtonIconRes = r
    }

    updatePiPParams()
    if (paused) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (keepControlsVisibleWhilePaused && binding.controls.visibility == View.VISIBLE)
            fadeHandler.removeCallbacks(fadeRunnable)
        else if (keepControlsVisibleWhilePaused)
            showControls()
    } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (controlsDisplayTimeoutMs > 0L && binding.controls.visibility == View.VISIBLE) {
            fadeHandler.removeCallbacks(fadeRunnable)
            fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
        }
    }
    refreshTimeInfoPanelVisibility()
    updatePlayerTitleOverlay()
}

internal fun MPVActivity.updateDecoderButton() {
    if (activityIsDestroying || !MpvRuntimeOwnership.isOwnedBy(player)) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        eventUiHandler.post { updateDecoderButton() }
        return
    }
    val mode = currentDecoderUiMode()
    val activeHwdec = when (mode) {
        MPVView.DECODER_MODE_SW, MPVView.DECODER_MODE_MPV_CONF -> null
        else -> mpvGetPropertyString("hwdec-current")
    }
    val requestedHwdec = when {
        mode == MPVView.DECODER_MODE_SW || mode == MPVView.DECODER_MODE_MPV_CONF -> null
        activeHwdec.isNullOrBlank() -> normalizedHwdecOption()
        else -> null
    }
    val hardwareRejected = gpuNextFallbackState.hardwareDecoderRejected
    val decoderText = when (mode) {
        MPVView.DECODER_MODE_AUTO_SAFE ->
            currentAutoSafeDecoderBadge(activeHwdec, hardwareRejected, requestedHwdec)
        MPVView.DECODER_MODE_HW_PLUS -> hardwareDecoderBadge("HW+", activeHwdec, hardwareRejected)
        MPVView.DECODER_MODE_HW -> hardwareDecoderBadge("HW", activeHwdec, hardwareRejected)
        MPVView.DECODER_MODE_GNEXT,
        MPVView.DECODER_MODE_GNEXT_DIRECT,
        MPVView.DECODER_MODE_SHIELD_H10P ->
            gpuNextDecoderBadge(activeHwdec, hardwareRejected, requestedHwdec)
        MPVView.DECODER_MODE_SW -> "SW"
        MPVView.DECODER_MODE_MPV_CONF -> "CFG"
        else -> "HW"
    }
    binding.cycleDecoderBtn.setTextIfChanged(decoderText)
}

private fun MPVActivity.currentAutoSafeDecoderBadge(
    activeHwdec: String?,
    hardwareRejected: Boolean,
    requestedHwdec: String?,
): String {
    if (player.requestedVideoOutput.trim().lowercase().startsWith(MPV_VIEW_VO_GPU_NEXT))
        return gpuNextDecoderBadge(activeHwdec, hardwareRejected, requestedHwdec)

    val badge = when (activeHwdec?.trim()?.lowercase()) {
        MPV_VIEW_HWDEC_MEDIACODEC -> "HW+"
        MPV_VIEW_HWDEC_MEDIACODEC_COPY -> "HW"
        MPV_VIEW_HWDEC_NONE -> "SW"
        else -> "AUTO"
    }
    return hardwareDecoderBadge(badge, activeHwdec, hardwareRejected)
}

private fun MPVActivity.shouldApplyShieldHi10pFallback(currentMode: String): Boolean {
    return autoDecoderFallback && !gpuNextFallbackState.rendererFallbackApplied &&
        isHi10pFallbackDeviceEnabled() &&
        player.isHi10pH264Video() &&
        (
            currentMode == MPVView.DECODER_MODE_HW ||
                currentMode == MPVView.DECODER_MODE_HW_PLUS ||
                currentMode == MPVView.DECODER_MODE_AUTO_SAFE ||
                // gpu-next sessions read as G-NEXT (copy). MediaCodec rejects
                // 10-bit H.264 there too, so Hi10P still needs the tuned
                // fallback — otherwise lavc silently software-decodes with
                // standard tuning and the user's fallback choice never applies.
                currentMode == MPVView.DECODER_MODE_GNEXT ||
                currentMode == MPVView.DECODER_MODE_GNEXT_DIRECT
        )
}

private const val SHIELD_FALLBACK_RESUME_TIMEOUT_MS = 2_500L

internal fun MPVActivity.clearPendingShieldFallbackResync(resumePlayback: Boolean = false) {
    pendingShieldFallbackResync.set(false)
    shieldFallbackSkipResyncSeek = false
    val shouldResume = shieldFallbackResumeAfter
    shieldFallbackResumeAfter = false
    eventUiHandler.removeCallbacks(shieldFallbackResyncRunnable)
    eventUiHandler.removeCallbacks(shieldFallbackResumeTimeoutRunnable)
    if (resumePlayback && shouldResume) {
        mpvSetPropertyBoolean("pause", false)
    }
}

internal fun MPVActivity.handleShieldFallbackResumeTimeout() {
    pendingShieldFallbackResync.set(false)
    shieldFallbackSkipResyncSeek = false
    val shouldResume = shieldFallbackResumeAfter
    shieldFallbackResumeAfter = false
    if (shouldResume && (activityIsForeground || didResumeBackgroundPlayback)) {
        Log.w(MPV_ACTIVITY_TAG, "shield fallback: timed out waiting for restart; resuming")
        mpvSetPropertyBoolean("pause", false)
    }
}

internal fun MPVActivity.handleShieldFallbackResync() {
    eventUiHandler.removeCallbacks(shieldFallbackResumeTimeoutRunnable)
    val skipSeek = shieldFallbackSkipResyncSeek
    shieldFallbackSkipResyncSeek = false
    val shouldResume = shieldFallbackResumeAfter
    shieldFallbackResumeAfter = false
    if (!activityIsForeground && !didResumeBackgroundPlayback) return
    if (!skipSeek) {
        val pos = mpvGetPropertyDouble("time-pos/full")
        if (pos != null) {
            Log.v(MPV_ACTIVITY_TAG, "shield fallback: realigning A/V at $pos")
            mpvCommand(arrayOf("seek", pos.toString(), "absolute+exact"))
        }
    }
    if (shouldResume) {
        mpvSetPropertyBoolean("pause", false)
    }
}

private fun MPVActivity.shieldHi10pFallbackWillReinitVideo(): Boolean {
    val requestedHwdec = normalizedHwdecOption()
    val requestedVo = player.requestedVideoOutput.trim().lowercase()
    return requestedHwdec != MPV_VIEW_HWDEC_NONE ||
        requestedVo != MPV_VIEW_VO_GPU_NEXT ||
        shieldDecoderFallback.toShieldDecoderFallback() == MPVView.SHIELD_DECODER_FALLBACK_COPY
}

internal fun MPVActivity.maybeApplyShieldHi10pFallback() {
    if (!autoDecoderFallback ||
        gpuNextFallbackState.rendererFallbackApplied || !isHi10pFallbackDeviceEnabled()
    ) {
        return
    }
    if (sessionDecoderMode == MPVView.DECODER_MODE_MPV_CONF ||
        sessionDecoderMode == MPVView.DECODER_MODE_SW
    ) {
        return
    }
    if (player.isHi10pFallbackModeActive()) {
        sessionDecoderMode = null
        updateDecoderButton()
        return
    }
    if (!shouldApplyShieldHi10pFallback(player.currentDecoderMode)) return

    // If libmpv already fell back to software decode on gpu-next and default
    // tuning is active, writing hwdec=no mid-playback only triggers UPDATE_HWDEC
    // (VDCTRL_REINIT) mid-GOP without seeking when video_pts is still unset.
    if (player.isAlreadyRunningHi10pDefaultSoftwareFallback(shieldDecoderFallback)) {
        player.implicitHi10pDefaultSoftwareActive = true
        player.applyNativeDecoderFallbackPolicy(MPVView.DECODER_MODE_SHIELD_H10P)
        sessionDecoderMode = null
        updateDecoderButton()
        return
    }

    applyShieldHi10pFallbackWithResync()
}

private fun MPVActivity.applyShieldHi10pFallbackWithResync() {
    val willReinitVideo = shieldHi10pFallbackWillReinitVideo()
    val canSeek = willReinitVideo && mpvGetPropertyBoolean("seekable") != false
    // seeking == false iff playback_initialized && restart_complete (real frame rendered).
    val hasRenderedFrame = canSeek && mpvGetPropertyBoolean("seeking") == false
    val posBeforeFallback = if (canSeek && !hasRenderedFrame) {
        mpvGetPropertyDouble("time-pos/full")
    } else {
        null
    }
    clearPendingShieldFallbackResync()
    // Pause around the swap on seekable streams so audio can't drain → no underrun → no drift.
    if (canSeek && player.paused == false) {
        shieldFallbackResumeAfter = true
        mpvSetPropertyBoolean("pause", true)
        eventUiHandler.postDelayed(shieldFallbackResumeTimeoutRunnable, SHIELD_FALLBACK_RESUME_TIMEOUT_MS)
    }
    val restartsBefore = playbackRestartCount
    player.applyShieldHi10pFallback(shieldDecoderFallback)
    sessionDecoderMode = null
    automaticHi10pFallbackPath = currentMpvPath() ?: ""
    updateDecoderButton()
    if (!canSeek) return

    scheduleShieldFallbackResyncAfterSwap(hasRenderedFrame, posBeforeFallback, restartsBefore)
}

private fun MPVActivity.scheduleShieldFallbackResyncAfterSwap(
    hasRenderedFrame: Boolean,
    posBeforeFallback: Double?,
    restartsBefore: Long,
) {
    if (!hasRenderedFrame) {
        // If UPDATE_HWDEC reinitialized the decoder before frame 0 set video_pts,
        // libmpv skips its internal queue_seek; seek explicitly so the recreated
        // decoder does not wait until the next keyframe.
        shieldFallbackSkipResyncSeek = true
        pendingShieldFallbackResync.set(true)
        val seekTarget = posBeforeFallback ?: 0.0
        mpvCommand(arrayOf("seek", seekTarget.toString(), "absolute+exact"))
    } else if (playbackRestartCount != restartsBefore) {
        eventUiHandler.post(shieldFallbackResyncRunnable)
    } else {
        pendingShieldFallbackResync.set(true)
        if (playbackRestartCount != restartsBefore &&
            pendingShieldFallbackResync.compareAndSet(true, false)
        ) {
            eventUiHandler.post(shieldFallbackResyncRunnable)
        }
    }
}

internal fun MPVActivity.applySessionDecoderModeIfNeeded() {
    val sessionMode = sessionDecoderMode
    val mode = sessionMode ?: preferredDecoderMode.takeIf {
        !autoDecoderFallback && it.isNotBlank()
    }
    if (mode == null) {
        return
    } else if (mode == MPVView.DECODER_MODE_MPV_CONF && sessionMode == null) {
        updateDecoderButton()
    } else {
        player.applyDecoderMode(mode)
        updateDecoderButton()
    }
}

// Top-level (not an MPVActivity extension): MPVView's startup options need it too.
internal fun isNvidiaShieldDevice(): Boolean {
    return Build.MANUFACTURER.contains("NVIDIA", ignoreCase = true) ||
            Build.MODEL.contains("SHIELD", ignoreCase = true) ||
            Build.PRODUCT.contains("shield", ignoreCase = true)
}

internal fun MPVActivity.updateSpeedButton() {
    val speed = psc.speed
    if (speed == lastDisplayedSpeed)
        return
    lastDisplayedSpeed = speed
    binding.cycleSpeedBtn.setTextIfChanged(getString(R.string.ui_speed, speed))
    if (binding.timeInfoPanel.visibility == View.VISIBLE)
        updateClockInfo(force = true)
}
