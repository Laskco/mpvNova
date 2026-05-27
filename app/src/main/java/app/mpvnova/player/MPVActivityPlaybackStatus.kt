package app.mpvnova.player

import android.os.Build
import android.os.Looper
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

internal fun MPVActivity.updatePlaybackDuration(durationMs: Long) {
    val duration = (durationMs / MPV_MILLIS_PER_SECOND_FLOAT).roundToInt()
    if (!useTimeRemaining) {
        val durationText = Utils.prettyTime(duration)
        binding.playbackDurationTxt.setTextIfChanged(durationText)
    }

    val seekbarMax = seekbarProgressFromMillis(durationMs)
    val seekbarMaxChanged = !userIsOperatingSeekbar && binding.playbackSeekbar.max != seekbarMax
    if (seekbarMaxChanged)
        binding.playbackSeekbar.max = seekbarMax
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
}

internal fun MPVActivity.updatePlaybackStatus(paused: Boolean) {
    val r = if (paused) R.drawable.ic_play_arrow_black_24dp else R.drawable.ic_pause_black_24dp
    if (lastPlayButtonIconRes != r) {
        binding.playBtn.setImageResource(r)
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
}

internal fun MPVActivity.updateDecoderButton() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        eventUiHandler.post { updateDecoderButton() }
        return
    }
    val decoderText = when (player.currentDecoderMode) {
        MPVView.DECODER_MODE_HW_PLUS -> "HW+"
        MPVView.DECODER_MODE_HW -> "HW"
        MPVView.DECODER_MODE_GNEXT, MPVView.DECODER_MODE_SHIELD_H10P -> currentGpuNextBadge()
        MPVView.DECODER_MODE_SW -> "SW"
        else -> "HW"
    }
    binding.cycleDecoderBtn.setTextIfChanged(decoderText)
}

internal fun MPVActivity.toggleStatsOverlay() {
    mpvCommand(arrayOf("script-binding", "stats/display-stats-toggle"))
}

internal fun MPVActivity.maybeApplyShieldHi10pFallback() {
    val currentMode = player.currentDecoderMode
    val shouldFallback = autoDecoderFallback &&
        shieldDecoderModeEnabled &&
        isNvidiaShieldDevice() &&
        player.isHi10pH264Video() &&
        (currentMode == MPVView.DECODER_MODE_HW || currentMode == MPVView.DECODER_MODE_HW_PLUS)
    if (!shouldFallback) return

    // Pause around the swap so audio can't drain → no underrun → no drift.
    val wasPlaying = player.paused == false
    if (wasPlaying) {
        shieldFallbackResumeAfter = true
        mpvSetPropertyBoolean("pause", true)
    }
    player.applyShieldHi10pFallback(shieldDecoderFallback)
    updateDecoderButton()
    // Wait for playback-restart — fixed delay can't cover Shield's 3+s
    // MediaCodec retry cascade.
    pendingShieldFallbackResync = true
}

internal fun MPVActivity.applySessionDecoderModeIfNeeded() {
    val mode = sessionDecoderMode ?: preferredDecoderMode.takeIf {
        !autoDecoderFallback && it.isNotBlank()
    } ?: return
    if (mode == MPVView.DECODER_MODE_SHIELD_H10P && !shieldDecoderModeEnabled)
        return
    player.applyDecoderMode(mode)
    updateDecoderButton()
}

internal fun MPVActivity.isNvidiaShieldDevice(): Boolean {
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
}
