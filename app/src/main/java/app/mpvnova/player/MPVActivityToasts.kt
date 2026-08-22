package app.mpvnova.player

import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams

/**
 * Player-overlay toast: a single floating chip view ([PlayerBinding.playerToast])
 * that fades in, holds for a duration scaled to the content length, then
 * fades back out. Driven by a Handler-scheduled hide runnable on the
 * fadeHandler so the toast lifecycle integrates with the controls fade.
 */

internal fun MPVActivity.showToast(msg: String, cancel: Boolean = false, durationMs: Long = TOAST_UNTITLED_BASE_MS) {
    showToastInternal(null, msg, cancel, durationMs)
}

internal fun MPVActivity.showToast(
    title: String,
    detail: String,
    cancel: Boolean = true,
    durationMs: Long = TOAST_UNTITLED_BASE_MS
) {
    showToastInternal(title, detail, cancel, durationMs)
}

internal fun MPVActivity.showToastInternal(
    title: String?,
    detail: String,
    cancel: Boolean,
    durationMs: Long
) {
    val effectiveDurationMs = resolvedToastDuration(title, detail, durationMs)
    val state = PlayerToastState(
        title = title,
        detail = detail,
        hideAtMs = currentToastUptimeMs() + effectiveDurationMs,
        token = ++playerToastToken,
    )
    playerToastState = state
    renderPlayerToast(state, cancel, animate = true)
    schedulePlayerToastHide(state)
}

internal fun MPVActivity.resolvedToastDuration(
    title: String?,
    detail: String,
    requestedDurationMs: Long
): Long {
    val textLength = (title?.length ?: 0) + detail.length
    return if (!title.isNullOrBlank()) {
        val adaptiveDuration = TOAST_TITLED_BASE_MS +
            (textLength.coerceAtMost(TOAST_TITLED_MAX_CHARS) * TOAST_TITLED_PER_CHAR_MS)
        maxOf(requestedDurationMs, adaptiveDuration.coerceAtMost(TOAST_TITLED_MAX_MS))
    } else {
        val adaptiveDuration = TOAST_UNTITLED_BASE_MS +
            (textLength.coerceAtMost(TOAST_UNTITLED_MAX_CHARS) * TOAST_UNTITLED_PER_CHAR_MS)
        maxOf(requestedDurationMs, adaptiveDuration.coerceAtMost(TOAST_UNTITLED_MAX_MS))
    }
}

internal fun MPVActivity.updatePlayerToastPlacement() {
    val topMarginPx = if (binding.playerTitleOverlay.isVisible && binding.playerTitleOverlay.height > 0) {
        binding.playerTitleOverlay.bottom + Utils.convertDp(activityContext, TOAST_TITLE_GAP_DP)
    } else if (binding.playerTitleOverlay.isVisible) {
        Utils.convertDp(activityContext, TOAST_TOP_WITH_TITLE_DP)
    } else {
        Utils.convertDp(activityContext, TOAST_TOP_NO_TITLE_DP)
    }
    if ((binding.playerToast.layoutParams as? MarginLayoutParams)?.topMargin == topMarginPx)
        return
    binding.playerToast.updateLayoutParams<MarginLayoutParams> { topMargin = topMarginPx }
}
