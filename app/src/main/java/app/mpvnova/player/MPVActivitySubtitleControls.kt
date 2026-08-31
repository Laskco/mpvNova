package app.mpvnova.player

import android.view.View
import kotlin.math.roundToInt

internal fun MPVActivity.scheduleSubtitleControlsPositionUpdate() {
    updateSubtitleControlsPosition()
    binding.controls.removeCallbacks(subtitleControlsPositionRunnable)
    binding.controls.postDelayed(
        subtitleControlsPositionRunnable,
        SUBTITLE_POSITION_LAYOUT_DELAY_MS,
    )
}

internal fun MPVActivity.updateSubtitleControlsPosition() {
    if (binding.controls.visibility != View.VISIBLE) {
        applySubtitleControlsOffset(0)
        return
    }

    val playerHeight = binding.outside.height
    val controlsTop = binding.controls.top
    if (playerHeight <= 0 || binding.controls.height <= 0 || controlsTop !in 1 until playerHeight) {
        return
    }

    val clearancePx = Utils.convertDp(activityContext, SUBTITLE_CONTROLS_CLEARANCE_DP)
    val baseMargin = mpvGetPropertyInt("sub-margin-y") ?: DEFAULT_SUBTITLE_MARGIN_SCALED_PX
    val fontSize = mpvGetPropertyDouble("sub-font-size") ?: DEFAULT_SUBTITLE_FONT_SIZE_SCALED_PX
    val borderSize = mpvGetPropertyDouble("sub-border-size") ?: DEFAULT_SUBTITLE_BORDER_SCALED_PX
    val textClearance = (
        fontSize * SUBTITLE_GLYPH_DESCENT_FACTOR + borderSize * SUBTITLE_BORDER_CLEARANCE_FACTOR
    ).roundToInt()
    val offsetPercent = calculateSubtitleControlsOffsetPercent(
        playerHeightPx = playerHeight,
        controlsTopPx = controlsTop,
        clearancePx = clearancePx,
        baseMarginScaledPx = baseMargin,
        textClearanceScaledPx = textClearance,
    )
    applySubtitleControlsOffset(offsetPercent)
}

private fun MPVActivity.applySubtitleControlsOffset(offsetPercent: Int) {
    if (subtitleControlsOffsetPercent == offsetPercent) return
    subtitleControlsOffsetPercent = offsetPercent
    applySubPosProperty()
}

internal fun calculateSubtitleControlsOffsetPercent(
    playerHeightPx: Int,
    controlsTopPx: Int,
    clearancePx: Int,
    baseMarginScaledPx: Int,
    textClearanceScaledPx: Int = 0,
): Int {
    if (playerHeightPx <= 0 || controlsTopPx !in 1 until playerHeightPx) return 0
    val coveredHeightPx = (playerHeightPx - controlsTopPx + clearancePx)
        .coerceIn(0, playerHeightPx)
    val targetMarginScaledPx =
        (coveredHeightPx * SUBTITLE_REFERENCE_HEIGHT / playerHeightPx).roundToInt() +
            textClearanceScaledPx.coerceAtLeast(0)
    val requiredShiftScaledPx = (targetMarginScaledPx - baseMarginScaledPx).coerceAtLeast(0)
    return (requiredShiftScaledPx * PERCENT_SCALE / SUBTITLE_REFERENCE_HEIGHT).roundToInt()
}

private const val SUBTITLE_CONTROLS_CLEARANCE_DP = 8f
private const val SUBTITLE_POSITION_LAYOUT_DELAY_MS = 32L
private const val SUBTITLE_REFERENCE_HEIGHT = 720f
private const val DEFAULT_SUBTITLE_MARGIN_SCALED_PX = 34
private const val DEFAULT_SUBTITLE_FONT_SIZE_SCALED_PX = 55.0
private const val DEFAULT_SUBTITLE_BORDER_SCALED_PX = 3.0
private const val SUBTITLE_GLYPH_DESCENT_FACTOR = 0.35
private const val SUBTITLE_BORDER_CLEARANCE_FACTOR = 2.0
private const val PERCENT_SCALE = 100f
