package app.mpvnova.player

import android.view.View
import kotlin.math.roundToInt

internal fun MPVActivity.scheduleSubtitleControlsPositionUpdate() {
    updateSubtitleControlsPosition()
    binding.controls.post {
        updateSubtitleControlsPosition()
    }
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

    val coveredHeight = playerHeight - controlsTop
    val offsetPercent = (coveredHeight * 100f / playerHeight)
        .roundToInt()
        .coerceAtLeast(0)
    applySubtitleControlsOffset(offsetPercent)
}

private fun MPVActivity.applySubtitleControlsOffset(offsetPercent: Int) {
    if (subtitleControlsOffsetPercent == offsetPercent) return
    subtitleControlsOffsetPercent = offsetPercent
    applySubPosProperty()
}
