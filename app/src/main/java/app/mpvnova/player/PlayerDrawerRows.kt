package app.mpvnova.player

private const val DRAWER_SECTION_SPACER_DP = 4

internal enum class DrawerTab { VIDEO, PROCESSING, AUDIO, SUBTITLES, PLAYBACK, INTERFACE }

internal enum class PlayerDrawerActionGroup { VIDEO, PROCESSING, AUDIO_SUBTITLE, PLAYBACK, INTERFACE, STATS }

internal enum class PlayerDrawerAction(val group: PlayerDrawerActionGroup) {
    DECODER(PlayerDrawerActionGroup.VIDEO),
    PREFERRED_DECODER(PlayerDrawerActionGroup.VIDEO),
    SHIELD_FALLBACK(PlayerDrawerActionGroup.VIDEO),
    ASPECT(PlayerDrawerActionGroup.VIDEO),
    CONTRAST(PlayerDrawerActionGroup.VIDEO),
    BRIGHTNESS(PlayerDrawerActionGroup.VIDEO),
    GAMMA(PlayerDrawerActionGroup.VIDEO),
    SATURATION(PlayerDrawerActionGroup.VIDEO),
    SCREENSHOT(PlayerDrawerActionGroup.VIDEO),
    UPSCALING_FILTER(PlayerDrawerActionGroup.PROCESSING),
    DOWNSCALING_FILTER(PlayerDrawerActionGroup.PROCESSING),
    DEBANDING(PlayerDrawerActionGroup.PROCESSING),
    INTERPOLATION(PlayerDrawerActionGroup.PROCESSING),
    TEMPORAL_FILTER(PlayerDrawerActionGroup.PROCESSING),
    SHADERS(PlayerDrawerActionGroup.PROCESSING),
    AUDIO_TRACK(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    OPEN_AUDIO(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    AUDIO_DELAY(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    SUB_TRACK(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    OPEN_SUB(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    SUB_DELAY(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    SUB_SEEK_PREV(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    SUB_SEEK_NEXT(PlayerDrawerActionGroup.AUDIO_SUBTITLE),
    BACKGROUND_PLAY(PlayerDrawerActionGroup.PLAYBACK),
    PLAYLIST(PlayerDrawerActionGroup.PLAYBACK),
    PLAYBACK_SPEED(PlayerDrawerActionGroup.PLAYBACK),
    CHAPTER_PICKER(PlayerDrawerActionGroup.PLAYBACK),
    CHAPTER_PREV(PlayerDrawerActionGroup.PLAYBACK),
    CHAPTER_NEXT(PlayerDrawerActionGroup.PLAYBACK),
    SKIP_MODE(PlayerDrawerActionGroup.PLAYBACK),
    SKIP_BUTTON_DISPLAY(PlayerDrawerActionGroup.PLAYBACK),
    SEEK_STEP(PlayerDrawerActionGroup.PLAYBACK),
    UI_FONT(PlayerDrawerActionGroup.INTERFACE),
    TITLE_STYLE(PlayerDrawerActionGroup.INTERFACE),
    SCREENSAVER(PlayerDrawerActionGroup.INTERFACE),
    STATS_TOGGLE(PlayerDrawerActionGroup.STATS),
    STATS_PAGE_1(PlayerDrawerActionGroup.STATS),
    STATS_PAGE_2(PlayerDrawerActionGroup.STATS),
    STATS_PAGE_3(PlayerDrawerActionGroup.STATS),
}

internal enum class PlayerDrawerPreferenceGroup {
    AUTOPAUSE,
    INTERFACE,
    VIDEO,
    PROCESSING,
    PLAYBACK,
    SUBTITLES,
}

internal enum class PlayerDrawerPreference(
    val group: PlayerDrawerPreferenceGroup,
    val titleRes: Int,
    val summaryRes: Int,
    val key: String,
    val defaultValue: Boolean,
    // When this key is on, the row greys out and ignores taps.
    val disabledWhenOnKey: String? = null,
) {
    AUTOPAUSE_CONTROLS(
        PlayerDrawerPreferenceGroup.AUTOPAUSE,
        R.string.pref_autopause_controls_overlay_title,
        R.string.pref_autopause_controls_overlay_summary,
        "autopause_controls_overlay",
        false,
    ),
    AUTOPAUSE_SHIELD(
        PlayerDrawerPreferenceGroup.AUTOPAUSE,
        R.string.pref_autopause_shield_hi10p_title,
        R.string.pref_autopause_shield_hi10p_summary,
        "autopause_shield_hi10p",
        true,
    ),
    KEEP_CONTROLS_VISIBLE(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_keep_controls_visible_paused_title,
        R.string.pref_keep_controls_visible_paused_summary,
        "keep_controls_visible_paused",
        false,
    ),
    SHOW_MEDIA_TITLE(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_display_media_title_title,
        R.string.pref_display_media_title_summary,
        "display_media_title",
        true,
    ),
    SHOW_CLOCK(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_display_clock_overlay_title,
        R.string.pref_display_clock_overlay_summary,
        "display_clock_overlay",
        true,
    ),
    SHOW_CLOCK_DATE(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_display_clock_date_title,
        R.string.pref_display_clock_date_summary,
        "display_clock_date",
        false,
    ),
    SHOW_CLOCK_ON_PAUSE(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_display_clock_on_pause_title,
        R.string.pref_display_clock_on_pause_summary,
        "display_clock_on_pause",
        false,
    ),
    CLOCK_24_HOUR(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_display_clock_24_hour_title,
        R.string.pref_display_clock_24_hour_summary,
        "display_clock_24_hour",
        false,
    ),
    BOTTOM_CONTROLS(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_bottom_controls_title,
        R.string.pref_bottom_controls_summary,
        "bottom_controls",
        true,
    ),
    EXIT_DOUBLE_BACK(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_exit_with_double_back_title,
        R.string.pref_exit_with_double_back_summary,
        "exit_with_double_back",
        false,
    ),
    DPAD_UP_JUMPS_TOP(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_dpad_up_jumps_top_title,
        R.string.pref_dpad_up_jumps_top_summary,
        "dpad_up_jumps_to_top_controls",
        false,
    ),
    HIDE_CONTROLS_WHILE_SEEKING(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_hide_controls_while_seeking_title,
        R.string.pref_hide_controls_while_seeking_summary,
        "hide_controls_while_seeking",
        false,
        disabledWhenOnKey = "minimal_seekbar_while_seeking",
    ),
    MINIMAL_SEEKBAR_WHILE_SEEKING(
        PlayerDrawerPreferenceGroup.INTERFACE,
        R.string.pref_minimal_seekbar_while_seeking_title,
        R.string.pref_minimal_seekbar_while_seeking_summary,
        "minimal_seekbar_while_seeking",
        false,
        disabledWhenOnKey = "hide_controls_while_seeking",
    ),
    DECODER_AUTO_FALLBACK(
        PlayerDrawerPreferenceGroup.VIDEO,
        R.string.pref_decoder_auto_fallback_title,
        R.string.pref_decoder_auto_fallback_summary,
        "decoder_auto_fallback",
        true,
    ),
    SHIELD_DECODER_MODE(
        PlayerDrawerPreferenceGroup.VIDEO,
        R.string.pref_shield_decoder_mode_title,
        R.string.pref_shield_decoder_mode_summary,
        "shield_decoder_mode",
        true,
    ),
    SHIELD_MPEG2_SOFTWARE_FALLBACK(
        PlayerDrawerPreferenceGroup.VIDEO,
        R.string.pref_shield_mpeg2_software_fallback_title,
        R.string.pref_shield_mpeg2_software_fallback_summary,
        PREF_SHIELD_MPEG2_SOFTWARE_FALLBACK,
        true,
    ),
    LOW_QUALITY_DECODING(
        PlayerDrawerPreferenceGroup.PROCESSING,
        R.string.pref_video_fastdecode_title,
        R.string.pref_video_fastdecode_summary,
        "video_fastdecode",
        false,
    ),
    SAVE_POSITION(
        PlayerDrawerPreferenceGroup.PLAYBACK,
        R.string.pref_save_position_title,
        R.string.pref_save_position_summary,
        "save_position",
        true,
    ),
    FAST_SEEK(
        PlayerDrawerPreferenceGroup.PLAYBACK,
        R.string.pref_fast_seek_title,
        R.string.pref_fast_seek_summary,
        "fast_seek_enabled",
        false,
    ),
    SEEK_KEYS_INPUTCONF(
        PlayerDrawerPreferenceGroup.PLAYBACK,
        R.string.pref_seek_keys_inputconf_title,
        R.string.pref_seek_keys_inputconf_summary,
        "seek_keys_use_inputconf",
        false,
    ),
    PLAYLIST_EXIT_WARNING(
        PlayerDrawerPreferenceGroup.PLAYBACK,
        R.string.pref_playlist_exit_warning_title,
        R.string.pref_playlist_exit_warning_summary,
        "playlist_exit_warning",
        true,
    ),
    PREFER_EXTERNAL_FORWARDED_SUBTITLES(
        PlayerDrawerPreferenceGroup.SUBTITLES,
        R.string.pref_prefer_external_forwarded_subtitles_title,
        R.string.pref_prefer_external_forwarded_subtitles_summary,
        PREF_PREFER_EXTERNAL_FORWARDED_SUBTITLES,
        false,
    ),
}

internal data class PlayerDrawerButtonSpec(
    val action: PlayerDrawerAction,
    val textRes: Int,
)

internal enum class PlayerDrawerOption(
    val action: PlayerDrawerAction,
    val titleRes: Int,
    val summaryRes: Int,
) {
    PREFERRED_DECODER(
        PlayerDrawerAction.PREFERRED_DECODER,
        R.string.pref_preferred_decoder_mode_title,
        R.string.pref_preferred_decoder_mode_summary,
    ),
    SHIELD_FALLBACK(
        PlayerDrawerAction.SHIELD_FALLBACK,
        R.string.pref_shield_decoder_fallback_title,
        R.string.pref_shield_decoder_fallback_summary,
    ),
    UPSCALING_FILTER(
        PlayerDrawerAction.UPSCALING_FILTER,
        R.string.pref_video_upscale_title,
        R.string.pref_video_upscale_summary,
    ),
    DOWNSCALING_FILTER(
        PlayerDrawerAction.DOWNSCALING_FILTER,
        R.string.pref_video_downscale_title,
        R.string.pref_video_downscale_summary,
    ),
    DEBANDING(
        PlayerDrawerAction.DEBANDING,
        R.string.pref_video_debanding_title,
        R.string.pref_video_debanding_summary,
    ),
    INTERPOLATION(
        PlayerDrawerAction.INTERPOLATION,
        R.string.pref_video_interpolation_title,
        R.string.pref_video_interpolation_message,
    ),
    TEMPORAL_FILTER(
        PlayerDrawerAction.TEMPORAL_FILTER,
        R.string.pref_video_tscale_title,
        R.string.pref_video_tscale_summary,
    ),
    SHADERS(
        PlayerDrawerAction.SHADERS,
        R.string.shader_manager_title,
        R.string.shader_manager_summary,
    ),
    SKIP_MODE(
        PlayerDrawerAction.SKIP_MODE,
        R.string.pref_skip_segments_mode_title,
        R.string.pref_skip_segments_mode_summary,
    ),
    SKIP_BUTTON_DISPLAY(
        PlayerDrawerAction.SKIP_BUTTON_DISPLAY,
        R.string.pref_skip_button_display_title,
        R.string.pref_skip_button_display_summary,
    ),
    SEEK_STEP(
        PlayerDrawerAction.SEEK_STEP,
        R.string.pref_seek_step_title,
        R.string.pref_seek_step_summary,
    ),
    SCREENSAVER(
        PlayerDrawerAction.SCREENSAVER,
        R.string.pref_screensaver_title,
        R.string.pref_screensaver_summary,
    ),
    UI_FONT(
        PlayerDrawerAction.UI_FONT,
        R.string.appearance_ui_font_title,
        R.string.appearance_ui_font_summary,
    ),
    TITLE_STYLE(
        PlayerDrawerAction.TITLE_STYLE,
        R.string.player_title_style_title,
        R.string.player_title_style_summary,
    ),
}

internal sealed class PlayerDrawerRow {
    data class Button(val button: PlayerDrawerButtonSpec) : PlayerDrawerRow()
    data class ButtonPair(
        val left: PlayerDrawerButtonSpec,
        val right: PlayerDrawerButtonSpec,
    ) : PlayerDrawerRow()
    object Stats : PlayerDrawerRow()
    data class Preference(val preference: PlayerDrawerPreference) : PlayerDrawerRow()
    data class Option(val option: PlayerDrawerOption) : PlayerDrawerRow()
    data class Spacer(val heightDp: Int) : PlayerDrawerRow()
}

internal fun MPVActivity.buildPlayerDrawerRows(tab: DrawerTab): List<PlayerDrawerRow> {
    val rows = mutableListOf<PlayerDrawerRow>()
    when (tab) {
        DrawerTab.VIDEO -> addVideoRows(rows)
        DrawerTab.PROCESSING -> addProcessingRows(rows)
        DrawerTab.AUDIO -> addAudioRows(rows)
        DrawerTab.SUBTITLES -> addSubtitleRows(rows)
        DrawerTab.PLAYBACK -> addPlaybackRows(rows)
        DrawerTab.INTERFACE -> addInterfaceRows(rows)
    }
    return rows
}

private fun addProcessingRows(rows: MutableList<PlayerDrawerRow>) {
    rows.addOption(PlayerDrawerOption.UPSCALING_FILTER)
    rows.addOption(PlayerDrawerOption.DOWNSCALING_FILTER)
    rows.addOption(PlayerDrawerOption.DEBANDING)
    rows.addOption(PlayerDrawerOption.INTERPOLATION)
    rows.addOption(PlayerDrawerOption.TEMPORAL_FILTER)
    rows.addOption(PlayerDrawerOption.SHADERS)
    rows.addPref(PlayerDrawerPreference.LOW_QUALITY_DECODING)
}

private fun MPVActivity.addVideoRows(rows: MutableList<PlayerDrawerRow>) {
    rows.addButton(PlayerDrawerAction.DECODER, R.string.btn_decoder)
    if (player.vid != -1) {
        rows.addButton(PlayerDrawerAction.ASPECT, R.string.aspect_ratio)
        rows.addPair(
            PlayerDrawerAction.CONTRAST to R.string.contrast,
            PlayerDrawerAction.BRIGHTNESS to R.string.btn_brightness,
        )
        rows.addPair(
            PlayerDrawerAction.GAMMA to R.string.gamma,
            PlayerDrawerAction.SATURATION to R.string.saturation,
        )
    }
    rows.addButton(PlayerDrawerAction.SCREENSHOT, R.string.btn_screenshot)
    rows.add(PlayerDrawerRow.Spacer(DRAWER_SECTION_SPACER_DP))
    rows.addPref(PlayerDrawerPreference.DECODER_AUTO_FALLBACK)
    if (!autoDecoderFallback) {
        rows.addOption(PlayerDrawerOption.PREFERRED_DECODER)
    }
    rows.addPref(PlayerDrawerPreference.SHIELD_DECODER_MODE)
    if (shieldDecoderModeEnabled) {
        rows.addOption(PlayerDrawerOption.SHIELD_FALLBACK)
    }
    rows.addPref(PlayerDrawerPreference.SHIELD_MPEG2_SOFTWARE_FALLBACK)
    rows.addPref(PlayerDrawerPreference.AUTOPAUSE_SHIELD)
}

private fun MPVActivity.addAudioRows(rows: MutableList<PlayerDrawerRow>) {
    rows.addButton(PlayerDrawerAction.AUDIO_TRACK, R.string.btn_audio_filters)
    rows.addButton(PlayerDrawerAction.OPEN_AUDIO, R.string.open_external_audio)
    if (player.aid != -1 && player.vid != -1) {
        rows.addButton(PlayerDrawerAction.AUDIO_DELAY, R.string.audio_delay)
    }
}

private fun MPVActivity.addSubtitleRows(rows: MutableList<PlayerDrawerRow>) {
    rows.addButton(PlayerDrawerAction.OPEN_SUB, R.string.open_external_sub)
    rows.addPref(PlayerDrawerPreference.PREFER_EXTERNAL_FORWARDED_SUBTITLES)
    if (player.sid != -1) {
        rows.addButton(PlayerDrawerAction.SUB_DELAY, R.string.sub_delay)
        rows.addButton(PlayerDrawerAction.SUB_TRACK, R.string.btn_sub_track)
        rows.addPair(
            PlayerDrawerAction.SUB_SEEK_PREV to R.string.btn_sub_seek_prev,
            PlayerDrawerAction.SUB_SEEK_NEXT to R.string.btn_sub_seek_next,
        )
    }
}

private fun MPVActivity.addPlaybackRows(rows: MutableList<PlayerDrawerRow>) {
    if (isPlayingAudio) {
        rows.addButton(PlayerDrawerAction.BACKGROUND_PLAY, R.string.resume_bg_playback)
    }
    rows.addButton(PlayerDrawerAction.PLAYLIST, R.string.action_playlist)
    rows.addButton(PlayerDrawerAction.PLAYBACK_SPEED, R.string.btn_play_speed)
    if ((mpvGetPropertyInt("chapter-list/count") ?: 0) > 0) {
        rows.addButton(PlayerDrawerAction.CHAPTER_PICKER, R.string.chapter_button)
        rows.addPair(
            PlayerDrawerAction.CHAPTER_PREV to R.string.btn_chapter_prev,
            PlayerDrawerAction.CHAPTER_NEXT to R.string.btn_chapter_next,
        )
    }
    rows.add(PlayerDrawerRow.Stats)
    rows.add(PlayerDrawerRow.Spacer(DRAWER_SECTION_SPACER_DP))
    rows.addOption(PlayerDrawerOption.SKIP_MODE)
    if (skipSegmentsMode == SkipSegmentsMode.BUTTON) {
        rows.addOption(PlayerDrawerOption.SKIP_BUTTON_DISPLAY)
    }
    rows.addOption(PlayerDrawerOption.SEEK_STEP)
    rows.addPref(PlayerDrawerPreference.FAST_SEEK)
    rows.addPref(PlayerDrawerPreference.SEEK_KEYS_INPUTCONF)
    rows.addPref(PlayerDrawerPreference.SAVE_POSITION)
    rows.addPref(PlayerDrawerPreference.PLAYLIST_EXIT_WARNING)
}

private fun addInterfaceRows(rows: MutableList<PlayerDrawerRow>) {
    rows.addOption(PlayerDrawerOption.UI_FONT)
    rows.addOption(PlayerDrawerOption.TITLE_STYLE)
    rows.addPref(PlayerDrawerPreference.AUTOPAUSE_CONTROLS)
    rows.addPref(PlayerDrawerPreference.KEEP_CONTROLS_VISIBLE)
    rows.addOption(PlayerDrawerOption.SCREENSAVER)
    rows.addPref(PlayerDrawerPreference.SHOW_MEDIA_TITLE)
    rows.addPref(PlayerDrawerPreference.SHOW_CLOCK)
    rows.addPref(PlayerDrawerPreference.SHOW_CLOCK_DATE)
    rows.addPref(PlayerDrawerPreference.SHOW_CLOCK_ON_PAUSE)
    rows.addPref(PlayerDrawerPreference.CLOCK_24_HOUR)
    rows.addPref(PlayerDrawerPreference.BOTTOM_CONTROLS)
    rows.addPref(PlayerDrawerPreference.EXIT_DOUBLE_BACK)
    rows.addPref(PlayerDrawerPreference.DPAD_UP_JUMPS_TOP)
    rows.addPref(PlayerDrawerPreference.HIDE_CONTROLS_WHILE_SEEKING)
    rows.addPref(PlayerDrawerPreference.MINIMAL_SEEKBAR_WHILE_SEEKING)
}

private fun MutableList<PlayerDrawerRow>.addButton(action: PlayerDrawerAction, textRes: Int) {
    add(PlayerDrawerRow.Button(PlayerDrawerButtonSpec(action, textRes)))
}

private fun MutableList<PlayerDrawerRow>.addPair(
    left: Pair<PlayerDrawerAction, Int>,
    right: Pair<PlayerDrawerAction, Int>,
) {
    add(PlayerDrawerRow.ButtonPair(
        left = PlayerDrawerButtonSpec(left.first, left.second),
        right = PlayerDrawerButtonSpec(right.first, right.second),
    ))
}

private fun MutableList<PlayerDrawerRow>.addPref(preference: PlayerDrawerPreference) {
    add(PlayerDrawerRow.Preference(preference))
}

private fun MutableList<PlayerDrawerRow>.addOption(option: PlayerDrawerOption) {
    add(PlayerDrawerRow.Option(option))
}
