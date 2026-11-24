package app.mpvnova.player

import android.content.res.Resources
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val ORDINAL_TEEN_MIN = 11
private const val ORDINAL_TEEN_MAX = 13
private const val ORDINAL_ONE = 1
private const val ORDINAL_TWO = 2
private const val ORDINAL_THREE = 3
private const val ORDINAL_TEN = 10
private const val ORDINAL_HUNDRED = 100
private const val CLOCK_MINUTE_MS = 60_000L
private const val CLOCK_DAY_MS = 86_400_000L

@Suppress("MatchingDeclarationName")
internal class ClockLabels(val locale: Locale, val timeZone: TimeZone) {
    private val clock = MinuteLabel()
    private val endsAt = MinuteLabel()
    private val monthFormatter by lazy(LazyThreadSafetyMode.NONE) { formatter("MMMM") }
    private var dateDay = Long.MIN_VALUE
    private var dateText = ""

    fun timeFormatter(is24Hour: Boolean): SimpleDateFormat =
        formatter(if (is24Hour) "HH:mm" else "h:mm a")

    fun now(format: SimpleDateFormat, millis: Long): String = clock.format(format, millis)

    fun end(format: SimpleDateFormat, millis: Long, resources: Resources): String =
        endsAt.format(format, millis, resources)

    fun date(millis: Long): String {
        val dayKey = localBucket(millis, CLOCK_DAY_MS)
        if (dateDay != dayKey) {
            val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val year = calendar.get(Calendar.YEAR)
            dateText = "${monthFormatter.format(Date(millis))} $day${ordinalSuffix(day)}, $year"
            dateDay = dayKey
        }
        return dateText
    }

    private fun formatter(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, locale).apply { timeZone = this@ClockLabels.timeZone }

    private fun localBucket(millis: Long, size: Long): Long {
        // Use the offset at this instant, before flooring to a local minute or day.
        val remainder = millis % size + timeZone.getOffset(millis)
        val bucketOffset = if (remainder < 0) (remainder + 1) / size - 1 else remainder / size
        return millis / size + bucketOffset
    }

    private inner class MinuteLabel {
        private var formatter: SimpleDateFormat? = null
        private var minute = Long.MIN_VALUE
        private var endsAtPrefix: String? = null
        private var text = ""

        fun format(format: SimpleDateFormat, millis: Long, resources: Resources? = null): String {
            val minuteKey = localBucket(millis, CLOCK_MINUTE_MS)
            if (formatter !== format || minute != minuteKey || (resources != null && endsAtPrefix == null)) {
                val time = format.format(Date(millis))
                text = if (resources != null) {
                    val template = endsAtPrefix ?: resources.getString(R.string.player_ends_at).also {
                        endsAtPrefix = it
                    }
                    String.format(locale, template, time)
                } else {
                    time
                }
                formatter = format
                minute = minuteKey
            }
            return text
        }
    }
}

/**
 * Clock + "Ends at" + stats overlay updates for the time-info panel and
 * the stats text view in the player UI. Both update once per second on
 * the clock heartbeat (driven by [MPVActivity.clockRunnable]); the
 * tick guard inside [updateClockInfo] makes the call effectively free
 * if it gets invoked more than once per second.
 */

internal fun MPVActivity.updateStats(force: Boolean = false) {
    if (!statsFPS)
        return
    if (!force && binding.statsTextView.visibility != View.VISIBLE)
        return
    val fps = player.estimatedVfFps ?: return
    val statsText = getString(R.string.ui_fps, fps)
    binding.statsTextView.setTextIfChanged(statsText)
}

internal fun MPVActivity.updateClockInfo(force: Boolean = false) {
    val now = System.currentTimeMillis()
    val tick = now / MILLIS_PER_SECOND_LONG
    if (!force && lastClockInfoTick == tick)
        return
    lastClockInfoTick = tick

    val locale = Locale.getDefault()
    val timeZone = TimeZone.getDefault()
    val labels = clockLabels?.takeIf { it.locale == locale && it.timeZone == timeZone }
        ?: ClockLabels(locale, timeZone).also {
            clockLabels = it
            clockFormatter = null
        }
    val is24Hour = force24HourClock || (
        systemIs24HourFormat ?: android.text.format.DateFormat.is24HourFormat(this).also {
            systemIs24HourFormat = it
        }
    )
    val formatter = clockFormatter?.takeIf { clockFormatterIs24 == is24Hour }
        ?: labels.timeFormatter(is24Hour).also {
            clockFormatter = it
            clockFormatterIs24 = is24Hour
        }
    updateClockDate(labels, now)
    val clockText = labels.now(formatter, now)
    binding.clockTextView.setTextIfChanged(clockText)
    binding.clockTextView.setVisibilityIfChanged(
        if (playerTitleStyle.clock.visible && clockText.isNotBlank()) View.VISIBLE else View.GONE,
    )
    updateEndsAtText(labels, formatter, now)
    if (appliedPlayerTitleStyle != playerTitleStyle) {
        applyPlayerTitleStyle()
    }
}

private fun MPVActivity.updateEndsAtText(labels: ClockLabels, formatter: SimpleDateFormat, now: Long) {
    val remainingSeconds = (psc.durationSec - psc.positionSec).coerceAtLeast(0)
    if (psc.durationSec <= 0 || remainingSeconds <= 0) {
        binding.endsAtTextView.setVisibilityIfChanged(View.GONE)
        return
    }
    val playbackSpeed = psc.speed.takeIf { it > 0f } ?: 1f
    val wallClockRemainingMs = (remainingSeconds * MILLIS_PER_SECOND_LONG / playbackSpeed).toLong()
    val endsAtText = labels.end(formatter, now + wallClockRemainingMs, resources)
    binding.endsAtTextView.setTextIfChanged(endsAtText)
    binding.endsAtTextView.setVisibilityIfChanged(
        if (playerTitleStyle.endsAt.visible && endsAtText.isNotBlank()) View.VISIBLE else View.GONE,
    )
}

private fun MPVActivity.updateClockDate(labels: ClockLabels, now: Long) {
    val showDate = playerTitleStyle.date.visible && (playerTextStylePreviewActive || showClockDate)
    if (!showDate) {
        binding.dateTextView.setVisibilityIfChanged(View.GONE)
        return
    }
    val dateText = labels.date(now)
    binding.dateTextView.setTextIfChanged(dateText)
    binding.dateTextView.setVisibilityIfChanged(if (dateText.isNotBlank()) View.VISIBLE else View.GONE)
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
