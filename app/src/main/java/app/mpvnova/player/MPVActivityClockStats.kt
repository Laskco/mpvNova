package app.mpvnova.player

import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ORDINAL_TEEN_MIN = 11
private const val ORDINAL_TEEN_MAX = 13
private const val ORDINAL_ONE = 1
private const val ORDINAL_TWO = 2
private const val ORDINAL_THREE = 3
private const val ORDINAL_TEN = 10
private const val ORDINAL_HUNDRED = 100

/**
 * Clock + "Ends at" + stats overlay updates for the time-info panel and
 * the stats text view in the player UI. Both update once per second on
 * the clock heartbeat (driven by [MPVActivity.clockRunnable]); the
 * tick guard inside [updateClockInfo] makes the call effectively free
 * if it gets invoked more than once per second.
 */

internal fun MPVActivity.updateStats() {
    if (!statsFPS)
        return
    val fps = player.estimatedVfFps ?: return
    val statsText = getString(R.string.ui_fps, fps)
    if (binding.statsTextView.text.toString() != statsText)
        binding.statsTextView.text = statsText
}

internal fun MPVActivity.updateClockInfo(force: Boolean = false) {
    val now = System.currentTimeMillis()
    val tick = now / MILLIS_PER_SECOND_LONG
    if (!force && lastClockInfoTick == tick)
        return
    lastClockInfoTick = tick

    val is24Hour = force24HourClock || android.text.format.DateFormat.is24HourFormat(this)
    if (clockFormatter == null || clockFormatterIs24 != is24Hour) {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        clockFormatter = SimpleDateFormat(pattern, Locale.getDefault())
        clockFormatterIs24 = is24Hour
    }
    val formatter = clockFormatter ?: return
    val nowDate = Date(now)
    updateClockDate(nowDate)
    binding.clockTextView.setTextIfChanged(formatter.format(nowDate))

    val remainingSeconds = (psc.durationSec - psc.positionSec).coerceAtLeast(0)
    if (psc.durationSec > 0 && remainingSeconds > 0) {
        val playbackSpeed = psc.speed.takeIf { it > 0f } ?: 1f
        val wallClockRemainingMs = (remainingSeconds * MILLIS_PER_SECOND_LONG / playbackSpeed).toLong()
        val endTimeMillis = now + wallClockRemainingMs
        val endsAtText = getString(
            R.string.player_ends_at,
            formatter.format(Date(endTimeMillis))
        )
        binding.endsAtTextView.visibility = View.VISIBLE
        if (binding.endsAtTextView.text.toString() != endsAtText)
            binding.endsAtTextView.text = endsAtText
    } else {
        binding.endsAtTextView.visibility = View.GONE
    }
}

private fun MPVActivity.updateClockDate(nowDate: Date) {
    if (!showClockDate) {
        binding.dateTextView.setVisibilityIfChanged(View.GONE)
        return
    }
    binding.dateTextView.setVisibilityIfChanged(View.VISIBLE)
    binding.dateTextView.setTextIfChanged(formatClockDate(nowDate))
}

private fun MPVActivity.formatClockDate(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val year = calendar.get(Calendar.YEAR)
    return "${clockDateFormatter().format(date)} $day${ordinalSuffix(day)}, $year"
}

private fun MPVActivity.clockDateFormatter(): SimpleDateFormat {
    val locale = Locale.getDefault()
    val existing = clockDateFormatter
    if (existing != null && clockDateFormatterLocale == locale)
        return existing
    return SimpleDateFormat("MMMM", locale).also {
        clockDateFormatter = it
        clockDateFormatterLocale = locale
    }
}

private fun ordinalSuffix(day: Int): String {
    val hundredRemainder = day % ORDINAL_HUNDRED
    if (hundredRemainder in ORDINAL_TEEN_MIN..ORDINAL_TEEN_MAX)
        return "th"
    return when (day % ORDINAL_TEN) {
        ORDINAL_ONE -> "st"
        ORDINAL_TWO -> "nd"
        ORDINAL_THREE -> "rd"
        else -> "th"
    }
}
