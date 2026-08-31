package app.mpvnova.player

import android.content.SharedPreferences

internal fun MPVActivity.readPlayerUiSettings(prefs: SharedPreferences) {
    controlsAtBottom = true
    topActionsInPlayerBar = prefs.getBoolean(PREF_TOP_ACTIONS_IN_PLAYERBAR, false)
    showMediaTitle = true
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
    dpadUpJumpsToTopControls = !topActionsInPlayerBar &&
        prefs.getBoolean(PREF_DPAD_UP_JUMPS_TO_TOP_CONTROLS, false)
    if (topActionsInPlayerBar && prefs.getBoolean(PREF_DPAD_UP_JUMPS_TO_TOP_CONTROLS, false)) {
        prefs.edit().putBoolean(PREF_DPAD_UP_JUMPS_TO_TOP_CONTROLS, false).apply()
    }
    hideControlsWhileSeeking = prefs.getBoolean("hide_controls_while_seeking", false)
    minimalSeekbarWhileSeeking = prefs.getBoolean("minimal_seekbar_while_seeking", false)
}

internal fun MPVActivity.migrateRetiredPlayerUiSettings(prefs: SharedPreferences) {
    if (prefs.contains(RETIRED_MEDIA_TITLE_KEY) &&
        !prefs.getBoolean(RETIRED_MEDIA_TITLE_KEY, true)
    ) {
        playerTitleStyle = playerTitleStyle.copy(
            season = playerTitleStyle.season.copy(visible = false),
            episodeNumber = playerTitleStyle.episodeNumber.copy(visible = false),
            title = playerTitleStyle.title.copy(visible = false),
            episodeTitle = playerTitleStyle.episodeTitle.copy(visible = false),
        )
        PlayerTitleStyleStore.write(prefs, playerTitleStyle)
    }
    if (prefs.contains(RETIRED_BOTTOM_CONTROLS_KEY) &&
        !prefs.getBoolean(RETIRED_BOTTOM_CONTROLS_KEY, true)
    ) {
        playerUiCustomization = playerUiCustomization.copy(
            verticalOffsetDp = (
                playerUiCustomization.verticalOffsetDp + FLOATING_CONTROLS_BOTTOM_MARGIN_DP.toInt()
            ).coerceAtMost(MAX_VERTICAL_OFFSET_DP),
        ).normalized()
        PlayerUiCustomizationStore.write(prefs, playerUiCustomization)
    }
    prefs.edit()
        .remove(RETIRED_MEDIA_TITLE_KEY)
        .remove(RETIRED_BOTTOM_CONTROLS_KEY)
        .apply()
}

private const val RETIRED_MEDIA_TITLE_KEY = "display_media_title"
private const val RETIRED_BOTTOM_CONTROLS_KEY = "bottom_controls"
