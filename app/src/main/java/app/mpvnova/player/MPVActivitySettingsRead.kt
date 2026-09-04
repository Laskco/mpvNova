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
    skipSegmentsMode = readSkipSegmentsMode(prefs)
    skipButtonDisplayMode = SkipButtonDisplayMode.fromPref(prefs.getString("skip_button_display", "segment"))
    seekStepMs = readSeekStepSeconds(prefs) * MILLIS_PER_SECOND_LONG
    seekKeysUseInputConf = prefs.getBoolean("seek_keys_use_inputconf", false)
    preferExternalForwardedSubtitles = prefs.getBoolean(PREF_PREFER_EXTERNAL_FORWARDED_SUBTITLES, false)
    readDelayDefaults(prefs)
    fastSeekEnabled = prefs.getBoolean("fast_seek_enabled", false)
    readScreensaverSettings(prefs)
    readPlayerUiSettings(prefs)
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
    val storedVideoFilterPreset = prefs.getString(
        PREF_VIDEO_FILTER_PRESET,
        VideoFilterPreset.NONE.prefValue,
    ) ?: VideoFilterPreset.NONE.prefValue
    videoFilterPresetId = when {
        !prefs.contains(PREF_VIDEO_FILTER_PRESET) &&
            VIDEO_FILTER_ADJUSTMENTS.any { prefs.getBoolean(it.rememberKey, false) } ->
            VIDEO_FILTER_PRESET_CUSTOM
        VideoFilterPreset.fromPref(storedVideoFilterPreset) != null -> storedVideoFilterPreset
        storedVideoFilterPreset == VIDEO_FILTER_PRESET_CUSTOM -> VIDEO_FILTER_PRESET_CUSTOM
        else -> VideoFilterPreset.NONE.prefValue
    }
    useTimeRemaining = prefs.getBoolean("use_time_remaining", false)
    ignoreAudioFocus = prefs.getBoolean("ignore_audio_focus", false)
    playlistExitWarning = prefs.getBoolean("playlist_exit_warning", true)
    newIntentReplace = prefs.getBoolean("new_intent_replace", false)
    readDecoderSettings(prefs)
    autoPauseControlsOverlayEnabled = prefs.getBoolean("autopause_controls_overlay", false)
    autoPauseShieldHi10pEnabled = prefs.getBoolean("autopause_shield_hi10p", true)
}

private fun MPVActivity.readDecoderSettings(prefs: SharedPreferences) {
    autoDecoderFallback = prefs.getBoolean("decoder_auto_fallback", true)
    shieldDecoderModeEnabled = prefs.getBoolean("shield_decoder_mode", true)
    hi10pFallbackOnOtherDevicesEnabled = prefs.getBoolean(
        PREF_HI10P_FALLBACK_OTHER_DEVICES,
        false,
    )
    shieldDecoderFallback = prefs.getString(
        "shield_decoder_fallback",
        MPVView.SHIELD_DECODER_FALLBACK_DEFAULT,
    ).toShieldDecoderFallback()
    shieldMpeg2SoftwareFallbackEnabled = prefs.getBoolean(
        PREF_SHIELD_MPEG2_SOFTWARE_FALLBACK,
        true,
    )
    mpeg2SoftwareFallbackOnOtherDevicesEnabled = prefs.getBoolean(
        PREF_MPEG2_SOFTWARE_FALLBACK_OTHER_DEVICES,
        false,
    )
    preferredDecoderMode = prefs.getString("preferred_decoder_mode", "") ?: ""
}

private fun MPVActivity.readDelayDefaults(prefs: SharedPreferences) {
    savedAudioDelayMs = prefs.getLong(PREF_AUDIO_DELAY_MS, 0L)
    savedSubDelayMs = prefs.getLong(PREF_SUB_DELAY_MS, 0L)
    savedSecondarySubDelayMs = prefs.getLong(PREF_SECONDARY_SUB_DELAY_MS, 0L)
    bluetoothAudioDelayMs = prefs.getLong(PREF_BLUETOOTH_AUDIO_DELAY_MS, 0L)
}

/** Skip mode, migrating the old `auto_skip_segments` boolean (true -> auto, false -> off). */
private fun readSkipSegmentsMode(prefs: SharedPreferences): SkipSegmentsMode {
    val value = when {
        prefs.contains("skip_segments_mode") -> prefs.getString("skip_segments_mode", "auto")
        prefs.contains("auto_skip_segments") ->
            if (prefs.getBoolean("auto_skip_segments", true)) "auto" else "off"
        else -> "auto"
    }
    return SkipSegmentsMode.fromPref(value)
}

private fun readSeekStepSeconds(prefs: SharedPreferences): Long {
    return (
        prefs.getString("seek_step_seconds", SEEK_STEP_DEFAULT_SEC.toString())?.toLongOrNull()
            ?: SEEK_STEP_DEFAULT_SEC.toLong()
        ).coerceIn(SEEK_STEP_MIN_SEC.toLong(), SEEK_STEP_MAX_SEC.toLong())
}

private const val SCREENSAVER_DEFAULT_SECONDS = "600"

private fun readScreensaverTimeoutMs(prefs: SharedPreferences): Long {
    val seconds = prefs.getString("screensaver_timeout", SCREENSAVER_DEFAULT_SECONDS)?.toLongOrNull()
        ?: SCREENSAVER_DEFAULT_SECONDS.toLong()
    return seconds.coerceAtLeast(0L) * MILLIS_PER_SECOND_LONG
}

private fun MPVActivity.readScreensaverSettings(prefs: SharedPreferences) {
    screensaverTimeoutMs = readScreensaverTimeoutMs(prefs)
    screensaverMode = ScreensaverMode.fromPref(prefs.getString("screensaver_mode", "dim"))
}

internal fun MPVActivity.readAudioFilterSettings(prefs: SharedPreferences) {
    persistAudioFilters = prefs.getBoolean("persist_audio_filters", false)
    voiceBoostLevel = persistedAudioLevel(prefs, "voice_boost_level", "voice_boost_on")
    volumeBoostDb = if (persistAudioFilters) prefs.getInt("volume_boost_db", 0) else 0
    nightModeLevel = persistedAudioLevel(
        prefs,
        levelKey = "night_mode_level",
        legacyToggleKey = "night_mode_on",
        legacyEnabledLevel = NIGHT_MODE_DRC_LEVEL
    )
    audioNormLevel = persistedAudioLevel(prefs, "audio_norm_level", "audio_norm_on")
    downmixLevel = persistedAudioLevel(
        prefs,
        levelKey = "downmix_level",
        legacyToggleKey = "downmix_on",
        legacyEnabledLevel = 1
    )
    centerBoostLevel = persistedAudioLevel(
        prefs,
        levelKey = "center_boost_level",
        legacyToggleKey = "center_boost_on",
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
    val storedScaleVersion = prefs.getInt("sub_scale_steps_version", 1)
    val storedScaleLevel = prefs.getInt("sub_scale_level", DEFAULT_SUB_SCALE_INDEX)
    val migratedScaleLevel = migrateSubScaleLevel(storedScaleLevel, storedScaleVersion)
    if (storedScaleVersion < SUB_SCALE_STEPS_VERSION) {
        prefs.edit()
            .putInt("sub_scale_level", migratedScaleLevel)
            .putInt("sub_scale_steps_version", SUB_SCALE_STEPS_VERSION)
            .apply()
    }
    persistSubFilters = prefs.getBoolean("persist_sub_filters", false)
    if (persistSubFilters) {
        subScaleLevel = migratedScaleLevel
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
