package app.mpvnova.player

import android.widget.TextView
import android.view.View
import android.os.Build
import java.util.WeakHashMap
import kotlin.math.ceil

internal fun MPVActivity.updatePlaybackTimeReservedWidths(durationSeconds: Int = psc.durationSec) {
    val safeDuration = durationSeconds.coerceAtLeast(0)
    binding.playbackPositionTxt.reservePlaybackTimeWidth(safeDuration, signed = false)
    binding.playbackDurationTxt.reservePlaybackTimeWidth(safeDuration, signed = true)
}

internal fun MPVActivity.renderPlayerBarTime(positionSeconds: Int, durationSeconds: Int) {
    val mode = playerUiCustomization.timeMode
    val remainingOnly = mode == PlayerTimeMode.REMAINING_ONLY
    val showRemaining = mode == PlayerTimeMode.ELAPSED_REMAINING || remainingOnly ||
        (mode == PlayerTimeMode.PLAYER_DEFAULT && useTimeRemaining)
    val duration = durationSeconds.coerceAtLeast(0)
    val position = positionSeconds.coerceAtLeast(0)
    val remaining = (duration.toLong() - position).coerceAtLeast(0).toInt()
    binding.playbackPositionTxt.setTextIfChanged(Utils.prettyTime(position))
    binding.playbackDurationTxt.setTextIfChanged(
        if (showRemaining) {
            if (remaining == 0) "-00:00" else Utils.prettyTime(-remaining, true)
        } else {
            Utils.prettyTime(duration)
        },
    )
    val group = binding.playbackTimeGroup
    for (index in 0 until group.childCount) {
        val child = group.getChildAt(index)
        child.setVisibilityIfChanged(
            if (remainingOnly && child !== binding.playbackDurationTxt) View.GONE else View.VISIBLE,
        )
    }
    // Reserve the signed width in every mode, including across the existing tap-to-toggle action.
    updatePlaybackTimeReservedWidths(maxOf(duration, position))
}

private fun TextView.reservePlaybackTimeWidth(durationSeconds: Int, signed: Boolean) {
    val hours = durationSeconds / SECONDS_PER_HOUR
    var hourDigits = 0
    var remainingHours = hours
    while (remainingHours > 0) {
        hourDigits++
        remainingHours /= DECIMAL_RADIX
    }
    val cached = reservedPlaybackTimeWidths[this]
    if (cached != null && cached.matches(this, hourDigits, signed)) {
        if (minWidth != cached.width) minWidth = cached.width
        return
    }
    val widestDigit = ('0'..'9').maxBy { digit -> paint.measureText(digit.toString()) }
    val digit = widestDigit.toString()
    val sample = if (hours > 0) {
        "${digit.repeat(hourDigits)}:$digit$digit:$digit$digit"
    } else {
        "$digit$digit:$digit$digit"
    }
    val width = ceil(paint.measureText(if (signed) "-$sample" else sample)).toInt()
    reservedPlaybackTimeWidths[this] = PlaybackTimeWidthReservation(this, hourDigits, signed, width)
    if (minWidth != width) minWidth = width
}

// Weak keys avoid retaining an Activity; the value contains only paint attributes, never the view.
private val reservedPlaybackTimeWidths = WeakHashMap<TextView, PlaybackTimeWidthReservation>()

private class PlaybackTimeWidthReservation(
    view: TextView,
    private val hourDigits: Int,
    private val signed: Boolean,
    val width: Int,
) {
    private val typeface = view.paint.typeface
    private val textSize = view.paint.textSize
    private val scaleX = view.paint.textScaleX
    private val skewX = view.paint.textSkewX
    private val flags = view.paint.flags
    private val locale = view.paint.textLocale
    private val letterSpacing = view.letterSpacing
    private val features = view.fontFeatureSettings
    private val variations = view.playbackFontVariations()

    fun matches(view: TextView, hourDigits: Int, signed: Boolean): Boolean =
        this.hourDigits == hourDigits && this.signed == signed &&
            typeface === view.paint.typeface && textSize == view.paint.textSize &&
            scaleX == view.paint.textScaleX && skewX == view.paint.textSkewX &&
            flags == view.paint.flags && locale == view.paint.textLocale &&
            letterSpacing == view.letterSpacing && features == view.fontFeatureSettings &&
            variations == view.playbackFontVariations()
}

private fun TextView.playbackFontVariations(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) fontVariationSettings else null

private const val SECONDS_PER_HOUR = 3600
private const val DECIMAL_RADIX = 10
