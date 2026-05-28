package app.mpvnova.player

private const val DRAWER_SECTION_SPACER_DP = 4

internal enum class DrawerTab { VIDEO, AUDIO, SUBTITLES, PLAYBACK, INTERFACE }

internal enum class PlayerDrawerActionGroup { VIDEO, AUDIO_SUBTITLE, PLAYBACK, STATS }

internal enum class PlayerDrawerAction(val group: PlayerDrawerActionGroup) {
    DECODER(PlayerDrawerActionGroup.VIDEO),
    ASPECT(PlayerDrawerActionGroup.VIDEO),
    CONTRAST(PlayerDrawerActionGroup.VIDEO),
    BRIGHTNESS(PlayerDrawerActionGroup.VIDEO),
    GAMMA(PlayerDrawerActionGroup.VIDEO),
    SATURATION(PlayerDrawerActionGroup.VIDEO),
    SCREENSHOT(PlayerDrawerActionGroup.VIDEO),
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
    STATS_TOGGLE(PlayerDrawerActionGroup.STATS),
    STATS_PAGE_1(PlayerDrawerActionGroup.STATS),
    STATS_PAGE_2(PlayerDrawerActionGroup.STATS),
    STATS_PAGE_3(PlayerDrawerActionGroup.STATS),
}

internal enum class PlayerDrawerPreferenceGroup { AUTOPAUSE, INTERFACE, VIDEO, PLAYBACK }

internal enum class PlayerDrawerPreference(
    val group: PlayerDrawerPreferenceGroup,
    val titleRes: Int,
    val summaryRes: Int,
    val key: String,
    val defaultValue: Boolean,
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
    AUTO_REFRESH_RATE(
        PlayerDrawerPreferenceGroup.VIDEO,
        R.string.pref_auto_refresh_rate_title,
        R.string.pref_auto_refresh_rate_summary,
        "auto_refresh_rate_switch",
        false,
    ),
    DECODER_AUTO_FALLBACK(
        PlayerDrawerPreferenceGroup.VIDEO,
        R.string.pref_decoder_auto_fallback_title,
        R.string.pref_decoder_auto_fallback_summary,
        "decoder_auto_fallback",
        true,
    ),
    SAVE_POSITION(
        PlayerDrawerPreferenceGroup.PLAYBACK,
        R.string.pref_save_position_title,
        R.string.pref_save_position_summary,
        "save_position",
        true,
    ),
    PLAYLIST_EXIT_WARNING(
        PlayerDrawerPreferenceGroup.PLAYBACK,
        R.string.pref_playlist_exit_warning_title,
        R.string.pref_playlist_exit_warning_summary,
        "playlist_exit_warning",
        true,
    ),
}

internal data class PlayerDrawerButtonSpec(
    val action: PlayerDrawerAction,
    val textRes: Int,
)

internal sealed class PlayerDrawerRow {
    data class Button(val button: PlayerDrawerButtonSpec) : PlayerDrawerRow()
    data class ButtonPair(
        val left: PlayerDrawerButtonSpec,
        val right: PlayerDrawerButtonSpec,
    ) : PlayerDrawerRow()
    object Stats : PlayerDrawerRow()
    data class Preference(val preference: PlayerDrawerPreference) : PlayerDrawerRow()
    data class Spacer(val heightDp: Int) : PlayerDrawerRow()
}

internal fun MPVActivity.buildPlayerDrawerRows(tab: DrawerTab): List<PlayerDrawerRow> {
    val rows = mutableListOf<PlayerDrawerRow>()
    when (tab) {
        DrawerTab.VIDEO -> addVideoRows(rows)
        DrawerTab.AUDIO -> addAudioRows(rows)
        DrawerTab.SUBTITLES -> addSubtitleRows(rows)
        DrawerTab.PLAYBACK -> addPlaybackRows(rows)
        DrawerTab.INTERFACE -> addInterfaceRows(rows)
    }
    return rows
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
    rows.addPref(PlayerDrawerPreference.AUTO_REFRESH_RATE)
    rows.addPref(PlayerDrawerPreference.DECODER_AUTO_FALLBACK)
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
    rows.addPref(PlayerDrawerPreference.SAVE_POSITION)
    rows.addPref(PlayerDrawerPreference.PLAYLIST_EXIT_WARNING)
}

private fun addInterfaceRows(rows: MutableList<PlayerDrawerRow>) {
    rows.addPref(PlayerDrawerPreference.AUTOPAUSE_CONTROLS)
    rows.addPref(PlayerDrawerPreference.AUTOPAUSE_SHIELD)
    rows.addPref(PlayerDrawerPreference.KEEP_CONTROLS_VISIBLE)
    rows.addPref(PlayerDrawerPreference.SHOW_MEDIA_TITLE)
    rows.addPref(PlayerDrawerPreference.SHOW_CLOCK)
    rows.addPref(PlayerDrawerPreference.BOTTOM_CONTROLS)
    rows.addPref(PlayerDrawerPreference.EXIT_DOUBLE_BACK)
    rows.addPref(PlayerDrawerPreference.DPAD_UP_JUMPS_TOP)
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
