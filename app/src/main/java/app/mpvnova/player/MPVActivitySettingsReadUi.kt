package app.mpvnova.player

import android.content.SharedPreferences

internal fun MPVActivity.readPlayerUiSettings(prefs: SharedPreferences) {
    controlsAtBottom = prefs.getBoolean("bottom_controls", true)
    showMediaTitle = prefs.getBoolean("display_media_title", true)
    playerTitleStyle = PlayerTitleStyleStore.read(prefs)
    showClockOverlay = prefs.getBoolean("display_clock_overlay", true)
    showClockDate = prefs.getBoolean("display_clock_date", false)
    showClockOnPause = prefs.getBoolean("display_clock_on_pause", false)
    force24HourClock = prefs.getBoolean("display_clock_24_hour", false)
    controlsDisplayTimeoutMs = parseControlsTimeout(
        prefs.getString("player_controls_timeout", DEFAULT_CONTROLS_DISPLAY_TIMEOUT.toString())
    )
    keepControlsVisibleWhilePaused = prefs.getBoolean("keep_controls_visible_paused", false)
    backHidesControlsFirst = prefs.getBoolean(PREF_BACK_HIDES_CONTROLS_FIRST, false)
    exitWithDoubleBack = prefs.getBoolean("exit_with_double_back", true)
    dpadUpJumpsToTopControls = prefs.getBoolean("dpad_up_jumps_to_top_controls", false)
    hideControlsWhileSeeking = prefs.getBoolean("hide_controls_while_seeking", false)
    minimalSeekbarWhileSeeking = prefs.getBoolean("minimal_seekbar_while_seeking", false)
}
