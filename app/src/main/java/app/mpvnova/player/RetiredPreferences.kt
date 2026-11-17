package app.mpvnova.player

import android.content.SharedPreferences

private const val OLD_MEDIA_TITLE = "display_media_title"
private const val OLD_BOTTOM_CONTROLS = "bottom_controls"
private const val OLD_SKIP_MODE = "skip_segments_mode"
private const val OLD_AUTO_SKIP = "auto_skip_segments"
private const val BAR_VERTICAL_OFFSET = "player_ui_custom_vertical_offset"

// Explicitly retired keys only. Unknown keys can belong to a newer build.
private val retiredKeys = setOf(
    "auto_refresh_rate_switch", "auto_resolution_switch", "auto_rotation",
    "gesture_horiz", "gesture_tap_center", "gesture_tap_left", "gesture_tap_right",
    "gesture_vert_left", "gesture_vert_right", "seek_gesture_smooth",
    "dolby_vision_fel", "dolby_vision_processing",
    OLD_MEDIA_TITLE, OLD_BOTTOM_CONTROLS, OLD_SKIP_MODE, OLD_AUTO_SKIP,
) + legacyDeviceCompatibilityKeys

internal fun SharedPreferences.migrateRetiredPreferences() = synchronized(this) {
    val removals = retiredKeys.filter(::contains)
    if (removals.isEmpty()) return@synchronized
    val replacements = retiredPreferenceReplacements(all, isNvidiaShieldDevice())
    edit().apply {
        removals.forEach(::remove)
        replacements.forEach { (key, value) ->
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }
    }.apply()
}

internal fun MutableMap<String, Any>.migrateRetiredPreferences(isShield: Boolean = isNvidiaShieldDevice()) {
    if (retiredKeys.none(::containsKey)) return
    putAll(retiredPreferenceReplacements(this, isShield))
    keys.removeAll(retiredKeys)
}

private fun retiredPreferenceReplacements(values: Map<String, *>, isShield: Boolean): Map<String, Any> =
    buildMap {
        if (legacyDeviceCompatibilityKeys.any(values::containsKey)) {
            putAll(deviceCompatibilityUpdates(values, isShield))
        }
        if (values.containsKey(OLD_SKIP_MODE) || values.containsKey(OLD_AUTO_SKIP)) {
            val modes = readSegmentSkipModes(values)
            listOf(SkipSegmentKind.INTRO, SkipSegmentKind.OUTRO, SkipSegmentKind.RECAP).forEach { kind ->
                put(kind.preferenceKey, modes.getValue(kind).prefValue)
            }
        }
        if (values[OLD_MEDIA_TITLE] == false) {
            listOf("season", "episode_number", "title", "episode_title").forEach { part ->
                val key = "player_title_style_${part}_visible"
                if (!values.containsKey(key)) put(key, false)
            }
        }
        if (values[OLD_BOTTOM_CONTROLS] == false && !values.containsKey(BAR_VERTICAL_OFFSET)) {
            put(BAR_VERTICAL_OFFSET, FLOATING_CONTROLS_BOTTOM_MARGIN_DP.toInt())
        }
    }
