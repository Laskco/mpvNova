package app.mpvnova.player

import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.TextView

internal fun MPVActivity.updatePlayerTitleWidth() {
    val horizontalMargin = Utils.convertDp(activityContext, PLAYER_TITLE_HORIZONTAL_MARGIN_DP)
    val width = resources.displayMetrics.widthPixels
    val availableWidth = (width - horizontalMargin * 2).coerceAtLeast(1)
    val cappedWidth = minOf(availableWidth, Utils.convertDp(activityContext, PLAYER_TITLE_MAX_WIDTH_DP))
    listOf(
        binding.playerTitleContext,
        binding.playerTitlePrimary,
        binding.playerTitleSecondary,
    ).forEach { textView ->
        if (textView.maxWidth != cappedWidth)
            textView.maxWidth = cappedWidth
    }
    val title = binding.playerTitlePrimary.text.toString()
    val fontScale = resources.configuration.fontScale
    if (
        fittedPlayerTitleText == title &&
        fittedPlayerTitleWidth == cappedWidth &&
        fittedPlayerTitleFontScale == fontScale
    ) {
        return
    }
    binding.playerTitlePrimary.fitPlayerTitleText(cappedWidth)
    fittedPlayerTitleText = title
    fittedPlayerTitleWidth = cappedWidth
    fittedPlayerTitleFontScale = fontScale
}

private fun TextView.fitPlayerTitleText(availableWidth: Int) {
    val value = text ?: return
    if (value.isBlank() || availableWidth <= 0) return

    val originalTextSize = paint.textSize
    val chosenSizeSp = (PLAYER_TITLE_MAX_TEXT_SIZE_SP downTo PLAYER_TITLE_MIN_TEXT_SIZE_SP)
        .firstOrNull { sizeSp ->
            paint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp.toFloat(),
                resources.displayMetrics,
            )
            StaticLayout.Builder.obtain(value, 0, value.length, paint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setBreakStrategy(breakStrategy)
                .setHyphenationFrequency(hyphenationFrequency)
                .setIncludePad(includeFontPadding)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .build()
                .lineCount <= maxLines
        }
        ?: PLAYER_TITLE_MIN_TEXT_SIZE_SP
    paint.textSize = originalTextSize

    val chosenSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        chosenSizeSp.toFloat(),
        resources.displayMetrics,
    )
    if (kotlin.math.abs(textSize - chosenSizePx) >= PLAYER_TITLE_TEXT_SIZE_TOLERANCE_PX) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, chosenSizePx)
    }
}

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
    if (isNewDpadSeek)
        lastDpadSeekApplyMs = 0L
    pendingDpadSeekPreviewMs = newPositionMs
    pendingSeekbarSeekMs = newPositionMs
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    eventUiHandler.postDelayed(commitSeekbarSeekRunnable, DPAD_SEEK_DEBOUNCE_MS)
    setPlaybackSeekbarProgress(seekbarProgressFromMillis(newPositionMs))
    updatePlaybackTimeline(newPositionMs, forceTextUpdate = true)

    val now = SystemClock.uptimeMillis()
    if (now - lastDpadSeekApplyMs >= DPAD_SEEK_APPLY_INTERVAL_MS) {
        lastDpadSeekApplyMs = now
        if (lastAppliedSeekMs != newPositionMs) {
            lastAppliedSeekMs = newPositionMs
            player.timePos = newPositionMs / MPV_MILLIS_PER_SECOND_DOUBLE
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
        player.timePos = positionMs / MPV_MILLIS_PER_SECOND_DOUBLE
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
    binding.playbackPositionTxt.setTextIfChanged(Utils.prettyTime(position))
    if (useTimeRemaining) {
        val diff = psc.durationSec - position
        val durationText = if (diff <= 0)
            "-00:00"
        else
            Utils.prettyTime(-diff, true)
        binding.playbackDurationTxt.setTextIfChanged(durationText)
    }

    // Skip secondary UI work while scrubbing — decoder is busy with the seek.
    // Clock + "Ends at" panel has its own 30s heartbeat.
    if (!userIsOperatingSeekbar && pendingDpadSeekPreviewMs == null)
        updateStats()
}
