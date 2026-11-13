package app.mpvnova.player.preferences

import app.mpvnova.player.UiScale
import java.io.IOException

/** Storage contracts from preference XML and programmatic readers, not the current device's values. */
internal object BackupPreferenceCompatibility {
    fun migrate(key: String, value: Any): Any {
        val type = if (key.startsWith("resume:")) Type.STRING else types[key]
        // Unknown keys have no reader in this version. Retain them for older/newer backups.
        val migrated = when {
            type == null -> value
            key == UiScale.PREF_KEY && (value is Int || value is Long) -> migrateInt(value)?.toString()
            else -> migrateType(type, value)
        }
        return migrated ?: invalid(key)
    }

    private fun migrateType(type: Type, value: Any): Any? = when (type) {
        Type.STRING -> value as? String
        Type.BOOLEAN -> value as? Boolean
        Type.INT -> migrateInt(value)
        Type.LONG -> when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }
        // numericInt/numericFloat deliberately accept legacy int/float representations.
        Type.NUMBER -> (value as? Number)?.takeIf { it.toDouble().isFinite() }
        Type.STRING_SET -> (value as? Set<*>)?.takeIf { set -> set.all { it is String } }
    }

    private fun migrateInt(value: Any): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Float -> value.toDouble().takeIf {
            it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() && it == value.toInt().toDouble()
        }?.toInt()
        else -> null
    }

    private fun invalid(key: String): Nothing = throw IOException("Incompatible preference type for $key")

    private enum class Type { STRING, BOOLEAN, INT, LONG, NUMBER, STRING_SET }

    private fun MutableMap<String, Type>.keys(type: Type, vararg names: String) {
        names.forEach { put(it, type) }
    }

    private val types = buildMap {
        keys(Type.STRING,
            "default_file_manager_path", "default_audio_language", "default_subtitle_language",
            "background_play", "no_ui_pause", "seek_step_seconds", "stats_mode", "preferred_decoder_mode",
            "video_edge_cleanup", "shield_decoder_fallback", "screensaver_mode", "screensaver_timeout",
            "screensaver_logo_uri", "intro_skip_mode", "outro_skip_mode", "recap_skip_mode",
            "movie_credits_mode", "post_credits_mode", "skip_button_display", UiScale.PREF_KEY,
            "player_controls_timeout", "remote_next_chapter_button", "video_filter_preset",
            "video_debanding", "video_sync", "ui_color_theme", "ui_font_family", "opensubtitles_language",
            "shader_manager_entries", "series_track_selections_v1", "sub_style_extras", "sub_style_presets",
            "player_title_custom_presets", "player_ui_custom_presets", "last_user_sub_title",
            "last_user_sub_lang", "last_user_sub_series", "last_user_audio_title", "last_user_audio_lang",
            "MainScreenFragment_remember", "MainScreenFragment_remember_data", "ignored_update_tag",
            "pending_update_tag", "pending_update_apk_path", "release_history",
        )
        keys(Type.BOOLEAN,
            "gpu_next", "hardware_decoding", "decoder_auto_fallback", "hi10p_fallback",
            "mpeg2_software_fallback", "autopause_hi10p", "experimental_ass_edges", "gpudebug",
            "ignore_audio_focus", "prefer_external_forwarded_subtitles", "limit_preferred_language_subtitles",
            "preserve_companion_subtitle_style", "local_auto_next", "save_position", "new_intent_replace",
            "playlist_exit_warning", "fast_seek_enabled", "seek_keys_use_inputconf", "opensubtitles_enabled",
            "screensaver_tint", "keep_controls_visible_paused", "autopause_controls_overlay",
            "top_actions_in_playerbar", "dpad_up_jumps_to_top_controls", "back_hides_controls_first",
            "exit_with_double_back", "display_clock_overlay", "display_clock_date", "display_clock_on_pause",
            "display_title_on_pause", "display_clock_24_hour", "tmdb_title_lookup", "hide_controls_while_seeking",
            "minimal_seekbar_while_seeking", "video_fastdecode", "video_interpolation", "material_you_theming",
            "ui_amoled_mode", "ui_pure_black_surfaces", "remember_series_aspect_ratio", "shader_manager_enabled",
            "remember_player_screen_brightness", "use_time_remaining", "persist_audio_filters", "persist_sub_filters",
            "custom_sub_style_enabled", "last_user_sub_off", "last_user_sub_forced", "MainActivity_filter_state",
            "preferred_subtitles_opt_in_reset_v1", "display_media_title", "bottom_controls",
            "shield_decoder_mode", "hi10p_fallback_other_devices", "shield_mpeg2_software_fallback",
            "mpeg2_software_fallback_other_devices", "autopause_shield_hi10p",
        )
        keys(Type.INT,
            "player_screen_brightness_percent", "volume_boost_db", "sub_scale_level", "sub_scale_steps_version",
            "sub_pos_pct", "secondary_sub_pos_pct", "last_user_sub_duplicate_index", "last_user_sub_duplicate_count",
        )
        keys(Type.LONG, "audio_delay_ms", "sub_delay_ms", "secondary_sub_delay_ms", "bluetooth_audio_delay_ms")
        keys(Type.STRING_SET, "shader_manager_folder_uris", "player_ui_custom_hidden_controls")
        listOf("brightness", "contrast", "gamma", "saturation", "hue").forEach {
            put("remember_video_$it", Type.BOOLEAN)
            put("video_$it", Type.INT)
        }
        listOf("voice_boost", "night_mode", "audio_norm", "downmix", "center_boost").forEach {
            put("${it}_on", Type.BOOLEAN)
            put("${it}_level", Type.INT)
        }
        listOf("video_scale", "video_downscale", "video_tscale").forEach {
            keys(Type.STRING, it, "${it}_param1", "${it}_param2")
        }
        listOf("preset", "forward", "backward", "target", "pause", "wait", "readahead", "stream", "timeout")
            .forEach { put("network_$it", Type.STRING) }
        keys(Type.STRING,
            "sub_style_text_color", "sub_style_border_color", "sub_style_shadow_color", "sub_style_justify",
            "sub_style_bg_color", "sub_style_edge", "sub_style_font_family",
        )
        keys(Type.INT,
            "sub_style_text_opacity", "sub_style_border_size", "sub_style_blur", "sub_style_shadow_size",
            "sub_style_spacing", "sub_style_bg_opacity",
        )
        keys(Type.BOOLEAN,
            "sub_style_bold", "sub_style_italic", "sub_style_gray_image_subs", "sub_style_override_ass",
            "sub_style_selective_ass", "sub_style_force_all_ass",
        )
        putAll(playerUiTypes())
        putAll(playerTitleTypes())
    }

    private fun playerUiTypes(): Map<String, Type> = buildMap {
        keys(Type.STRING,
            "surface", "density", "seekbar_size", "seekbar_position", "seekbar_thumb_size", "seekbar_thumb_shape",
            "seekbar_thumb_color", "time_position", "time_presentation", "control_alignment", "control_size",
            "button_treatment", "control_order", "seekbarPlayedColor", "seekbarBufferedColor", "seekbarUnplayedColor",
            "chapterMarkerColor", "chapterMarkerShape", "timeMode",
        )
        keys(Type.INT,
            "panel_opacity", "scrim_strength", "panel_outline_width", "corner_radius", "width", "vertical_offset",
            "horizontal_padding", "top_padding", "bottom_padding", "row_spacing", "panel_elevation", "seekbar_inset",
            "time_text_size", "time_control_gap", "control_spacing", "chapterMarkerSizePercent",
            "buttonFocusOutlineWidthDp", "buttonFocusHighlightOpacityPercent", "buttonFocusEnlargementPercent",
            "primaryPlayIconSizePercent", "otherIconSizePercent",
        )
        keys(Type.BOOLEAN,
            "gradient", "panel_outline", "seekbar_thumb_glow", "seekbar_visible", "chapter_markers_visible",
            "time_visible", "icon_text_outline", "currentChapterEmphasis",
        )
    }.mapKeys { (key, _) -> "player_ui_custom_$key" }

    private fun playerTitleTypes(): Map<String, Type> = buildMap {
        keys(Type.STRING, "separator", "metadata_format", "title_order", "clock_order")
        put("combined_panels", Type.BOOLEAN)
        listOf("season", "episode_number", "title", "episode_title", "date", "clock", "ends_at").forEach { part ->
            val fields = buildMap {
                keys(Type.STRING, "font", "color", "shadow", "text_case", "outline_color", "long_text")
                keys(Type.BOOLEAN, "italic", "visible", "uppercase", "background_enabled")
                keys(Type.INT, "weight", "opacity", "shadow_strength", "background_strength")
                keys(Type.NUMBER, "size", "spacing", "text_outline_width", "outline_width",
                    "max_lines", "line_spacing", "text_offset_x", "text_offset_y")
            }
            fields.forEach { (key, type) -> put("${part}_$key", type) }
        }
        listOf("title", "clock").forEach { panel ->
            val fields = buildMap {
                keys(Type.STRING, "surface", "alignment", "content_alignment")
                keys(Type.BOOLEAN, "gradient", "outline", "manual_position")
                keys(Type.NUMBER, "panel_opacity", "accent_strength", "outline_width", "corner_radius", "elevation",
                    "horizontal_padding", "vertical_padding", "content_spacing", "width", "vertical_offset",
                    "horizontal_offset")
            }
            fields.forEach { (key, type) -> put("${panel}_$key", type) }
        }
    }.mapKeys { (key, _) -> "player_title_style_$key" }
}
