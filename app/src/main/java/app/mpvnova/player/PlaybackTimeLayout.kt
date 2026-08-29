package app.mpvnova.player

import android.widget.TextView
import kotlin.math.ceil

internal fun MPVActivity.updatePlaybackTimeReservedWidths(durationSeconds: Int = psc.durationSec) {
    val safeDuration = durationSeconds.coerceAtLeast(0)
    binding.playbackPositionTxt.reservePlaybackTimeWidth(safeDuration, signed = false)
    binding.playbackDurationTxt.reservePlaybackTimeWidth(safeDuration, signed = true)
}

private fun TextView.reservePlaybackTimeWidth(durationSeconds: Int, signed: Boolean) {
    val widestDigit = ('0'..'9').maxBy { digit -> paint.measureText(digit.toString()) }
    val digit = widestDigit.toString()
    val hours = durationSeconds / SECONDS_PER_HOUR
    val sample = if (hours > 0) {
        "${digit.repeat(hours.toString().length)}:$digit$digit:$digit$digit"
    } else {
        "$digit$digit:$digit$digit"
    }
    val width = ceil(paint.measureText(if (signed) "-$sample" else sample)).toInt()
    if (minWidth != width) minWidth = width
}

private const val SECONDS_PER_HOUR = 3600
