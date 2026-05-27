package app.mpvnova.player

import android.content.SharedPreferences
import kotlin.math.abs

internal fun MPVActivity.readPlaybackSettings(
    prefs: SharedPreferences,
    getString: (String, Int) -> String
) {
    val statsMode = prefs.getString("stats_mode", "") ?: ""
    statsFPS = statsMode == "native_fps"
    statsLuaMode = if (statsMode.startsWith("lua")) {
        statsMode.removePrefix("lua").toIntOrNull() ?: 0
    } else {
        0
    }
    backgroundPlayMode = getString("background_play", R.string.pref_background_play_default)
    noUIPauseMode = getString("no_ui_pause", R.string.pref_no_ui_pause_default)
    shouldSavePosition = prefs.getBoolean("save_position", true)
    controlsAtBottom = prefs.getBoolean("bottom_controls", true)
    showMediaTitle = prefs.getBoolean("display_media_title", true)
    showClockOverlay = prefs.getBoolean("display_clock_overlay", true)
    controlsDisplayTimeoutMs = parseControlsTimeout(
        prefs.getString("player_controls_timeout", DEFAULT_CONTROLS_DISPLAY_TIMEOUT.toString())
    )
    keepControlsVisibleWhilePaused = prefs.getBoolean("keep_controls_visible_paused", false)
    exitWithDoubleBack = prefs.getBoolean("exit_with_double_back", false)
    autoRefreshRateSwitch = prefs.getBoolean("auto_refresh_rate_switch", false)
    dpadUpJumpsToTopControls = prefs.getBoolean("dpad_up_jumps_to_top_controls", false)
    remoteNextChapterKeyCode = remoteButtonKeyCode(prefs.getString(
        PREF_REMOTE_NEXT_CHAPTER_BUTTON,
        REMOTE_BUTTON_DISABLED
    ))
    rememberPlayerScreenBrightness = prefs.getBoolean("remember_player_screen_brightness", false)
    playerScreenBrightnessActive = rememberPlayerScreenBrightness
    playerScreenBrightnessPercent = prefs.getInt(
        "player_screen_brightness_percent",
        DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    ).coerceIn(MIN_PLAYER_SCREEN_BRIGHTNESS_PERCENT, MAX_PLAYER_SCREEN_BRIGHTNESS_PERCENT)
    readVideoAdjustmentSettings(
        getRemember = { key -> prefs.getBoolean(key, false) },
        getValue = { key -> prefs.getInt(key, VIDEO_ADJUSTMENT_DEFAULT_INT) }
    )
    useTimeRemaining = prefs.getBoolean("use_time_remaining", false)
    ignoreAudioFocus = prefs.getBoolean("ignore_audio_focus", false)
    playlistExitWarning = prefs.getBoolean("playlist_exit_warning", true)
    newIntentReplace = prefs.getBoolean("new_intent_replace", false)
    autoDecoderFallback = prefs.getBoolean("decoder_auto_fallback", true)
    shieldDecoderModeEnabled = prefs.getBoolean("shield_decoder_mode", true)
    shieldDecoderFallback = prefs.getString(
        "shield_decoder_fallback",
        MPVView.SHIELD_DECODER_FALLBACK_COPY,
    ).toShieldDecoderFallback()
    preferredDecoderMode = prefs.getString("preferred_decoder_mode", "") ?: ""
    autoPauseControlsOverlayEnabled = prefs.getBoolean("autopause_controls_overlay", false)
    autoPauseShieldHi10pEnabled = prefs.getBoolean("autopause_shield_hi10p", true)
}

internal fun MPVActivity.readAudioFilterSettings(prefs: SharedPreferences) {
    persistAudioFilters = prefs.getBoolean("persist_audio_filters", false)
    voiceBoostLevel = persistedAudioLevel(prefs, "voice_boost_level", "voice_boost_on")
    volumeBoostDb = if (persistAudioFilters) prefs.getInt("volume_boost_db", 0) else 0
    nightModeLevel = persistedAudioLevel(prefs, "night_mode_level", "night_mode_on")
    audioNormLevel = persistedAudioLevel(prefs, "audio_norm_level", "audio_norm_on")
    downmixLevel = persistedAudioLevel(
        prefs,
        levelKey = "downmix_level",
        legacyToggleKey = "downmix_on",
        legacyEnabledLevel = 1
    )
}

internal fun MPVActivity.persistedAudioLevel(
    prefs: SharedPreferences,
    levelKey: String,
    legacyToggleKey: String,
    legacyEnabledLevel: Int = 2
): Int {
    return if (persistAudioFilters) {
        when {
            prefs.contains(levelKey) -> prefs.getInt(levelKey, 0)
            prefs.getBoolean(legacyToggleKey, false) -> legacyEnabledLevel
            else -> 0
        }
    } else {
        0
    }
}

internal fun MPVActivity.readSubFilterSettings(prefs: SharedPreferences) {
    persistSubFilters = prefs.getBoolean("persist_sub_filters", false)
    if (persistSubFilters) {
        subScaleLevel = prefs.getInt("sub_scale_level", DEFAULT_SUB_SCALE_INDEX)
        subPosLevel = nearestSubPositionIndex(
            subPosSteps,
            prefs.getInt("sub_pos_pct", DEFAULT_SUB_POSITION_PERCENT)
        )
        secondaryPosLevel = nearestSubPositionIndex(
            secondaryPosSteps,
            prefs.getInt("secondary_sub_pos_pct", DEFAULT_SECONDARY_SUB_POSITION_PERCENT)
        )
    } else {
        subScaleLevel = DEFAULT_SUB_SCALE_INDEX
        subPosLevel = subPosSteps.indexOf(DEFAULT_SUB_POSITION_PERCENT).coerceAtLeast(0)
        secondaryPosLevel = secondaryPosSteps
            .indexOf(DEFAULT_SECONDARY_SUB_POSITION_PERCENT)
            .coerceAtLeast(0)
    }
}

internal fun nearestSubPositionIndex(steps: IntArray, value: Int): Int {
    return steps.indices.minBy { index -> abs(steps[index] - value) }
}
