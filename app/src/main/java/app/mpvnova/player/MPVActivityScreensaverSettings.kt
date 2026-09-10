package app.mpvnova.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogScreensaverTimeInputBinding

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
internal const val SCREENSAVER_CUSTOM_ID = "custom"
internal const val SCREENSAVER_MIN_SEC = 5
internal const val SCREENSAVER_MAX_SEC = 7200
internal val SCREENSAVER_CHOICES_SEC = intArrayOf(600, 1800, 3600)

// Shows the nicest whole unit: "30 sec", "10 min", "1 hr", or "1 hr 30 min".
internal fun screensaverTimeoutLabel(context: Context, seconds: Int): String = when {
    seconds < SECONDS_PER_MINUTE -> context.getString(R.string.screensaver_seconds_value, seconds)
    seconds % SECONDS_PER_MINUTE != 0 && seconds < SECONDS_PER_HOUR ->
        context.getString(R.string.screensaver_seconds_value, seconds)
    seconds < SECONDS_PER_HOUR ->
        context.getString(R.string.screensaver_minutes_value, seconds / SECONDS_PER_MINUTE)
    seconds % SECONDS_PER_HOUR == 0 ->
        context.getString(R.string.screensaver_hours_value, seconds / SECONDS_PER_HOUR)
    else -> context.getString(
        R.string.screensaver_hours_minutes_value,
        seconds / SECONDS_PER_HOUR,
        (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE,
    )
}

internal fun MPVActivity.screensaverChoiceLabel(seconds: Int): String =
    screensaverTimeoutLabel(this, seconds)

// Split the current value across the Hours / Minutes / Seconds fields.
// Keep ungrouped ASCII integers for the numeric inputs and screensaverInputToSeconds parser.
@SuppressLint("SetTextI18n")
internal fun initScreensaverTimeInput(binding: DialogScreensaverTimeInputBinding, currentSeconds: Int) {
    binding.hoursInput.setText((currentSeconds / SECONDS_PER_HOUR).toString())
    binding.minutesInput.setText(((currentSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toString())
    binding.secondsInput.setText((currentSeconds % SECONDS_PER_MINUTE).toString())
}

internal fun screensaverInputToSeconds(binding: DialogScreensaverTimeInputBinding): Int {
    val hours = binding.hoursInput.text.toString().toIntOrNull() ?: 0
    val minutes = binding.minutesInput.text.toString().toIntOrNull() ?: 0
    val seconds = binding.secondsInput.text.toString().toIntOrNull() ?: 0
    val total = hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds
    return total.coerceIn(SCREENSAVER_MIN_SEC, SCREENSAVER_MAX_SEC)
}

// Drawer row value stays compact so it never truncates, e.g. "Off" or "Dim · 30s".
internal fun MPVActivity.screensaverDrawerSummary(): String {
    if (screensaverMode == ScreensaverMode.OFF) return getString(R.string.pref_screensaver_mode_off)
    val modeShort = getString(
        if (screensaverMode == ScreensaverMode.DIM) {
            R.string.pref_screensaver_short_dim
        } else {
            R.string.pref_screensaver_short_logo
        }
    )
    return getString(R.string.screensaver_summary_format, modeShort, screensaverCompactTime())
}

private fun MPVActivity.screensaverCompactTime(): String {
    val sec = (screensaverTimeoutMs / MILLIS_PER_SECOND_LONG).toInt()
    return when {
        sec < SECONDS_PER_MINUTE -> "${sec}s"
        sec < SECONDS_PER_HOUR -> "${sec / SECONDS_PER_MINUTE}m"
        else -> "${sec / SECONDS_PER_HOUR}h"
    }
}

// Swap in the user's custom logo when set (shown in its own colours, tint off by
// default), otherwise the built-in mpvNova mark which colour-shifts on each bounce.
internal fun MPVActivity.applyScreensaverLogo(logo: ImageView) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val customUri = prefs.getString(PREF_SCREENSAVER_LOGO_URI, null)?.takeIf { it.isNotBlank() }
    screensaverTintEnabled = prefs.getBoolean(PREF_SCREENSAVER_TINT, customUri == null)

    if (customUri != null) {
        runCatching { logo.setImageURI(Uri.parse(customUri)) }
        if (logo.drawable == null) logo.setImageResource(R.drawable.dvd_logo)
    } else {
        logo.setImageResource(R.drawable.dvd_logo)
    }
    if (!screensaverTintEnabled) logo.imageTintList = null
}

internal fun MPVActivity.setScreensaverTintPref(enabled: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        .putBoolean(PREF_SCREENSAVER_TINT, enabled).apply()
}

internal fun MPVActivity.setScreensaverCustomLogo(uriString: String?, tintDefault: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().apply {
        if (uriString == null) remove(PREF_SCREENSAVER_LOGO_URI) else putString(PREF_SCREENSAVER_LOGO_URI, uriString)
        putBoolean(PREF_SCREENSAVER_TINT, tintDefault)
        apply()
    }
}

internal fun MPVActivity.setScreensaverTimeoutSeconds(seconds: Int) {
    val clamped = seconds.coerceIn(SCREENSAVER_MIN_SEC, SCREENSAVER_MAX_SEC)
    screensaverTimeoutMs = clamped.toLong() * MILLIS_PER_SECOND_LONG
    PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        .putString("screensaver_timeout", clamped.toString()).apply()
    refreshDrawerRowsIfVisible(DrawerTab.PLAYBACK)
    if (screensaverActive) wakeFromScreensaver() else noteScreensaverActivity()
}
