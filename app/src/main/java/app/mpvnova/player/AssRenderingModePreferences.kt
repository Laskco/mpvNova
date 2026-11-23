package app.mpvnova.player

import android.content.SharedPreferences
import androidx.annotation.StringRes

internal const val PREF_ASS_RENDERING_MODE = "ass_rendering_mode"
internal const val LEGACY_PREF_EXPERIMENTAL_ASS_EDGES = "experimental_ass_edges"

internal const val ASS_RENDERING_MODE_OFF = "0"
internal const val ASS_RENDERING_MODE_PERFORMANCE = "1"
internal const val ASS_RENDERING_MODE_ULTRA = "2"

internal data class AssRenderingModeOption(
    val value: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

internal fun assRenderingModeOptions(): List<AssRenderingModeOption> = listOf(
    AssRenderingModeOption(
        value = ASS_RENDERING_MODE_OFF,
        titleRes = R.string.pref_ass_rendering_mode_off,
        descriptionRes = R.string.pref_ass_rendering_mode_off_desc,
    ),
    AssRenderingModeOption(
        value = ASS_RENDERING_MODE_PERFORMANCE,
        titleRes = R.string.pref_ass_rendering_mode_performance,
        descriptionRes = R.string.pref_ass_rendering_mode_performance_desc,
    ),
    AssRenderingModeOption(
        value = ASS_RENDERING_MODE_ULTRA,
        titleRes = R.string.pref_ass_rendering_mode_ultra,
        descriptionRes = R.string.pref_ass_rendering_mode_ultra_desc,
    ),
)

internal fun normalizedAssRenderingMode(raw: String?): String = when (raw) {
    ASS_RENDERING_MODE_OFF,
    ASS_RENDERING_MODE_PERFORMANCE,
    ASS_RENDERING_MODE_ULTRA -> raw
    else -> ASS_RENDERING_MODE_PERFORMANCE
}

internal fun assRenderingModeFromValues(values: Map<String, *>): String {
    val current = values[PREF_ASS_RENDERING_MODE] as? String
    if (current != null) return normalizedAssRenderingMode(current)
    val legacy = values[LEGACY_PREF_EXPERIMENTAL_ASS_EDGES] as? Boolean
    return if (legacy == false) ASS_RENDERING_MODE_OFF else ASS_RENDERING_MODE_PERFORMANCE
}

@StringRes
internal fun assRenderingModeDescriptionRes(mode: String?): Int = when (normalizedAssRenderingMode(mode)) {
    ASS_RENDERING_MODE_OFF -> R.string.pref_ass_rendering_mode_off_desc
    ASS_RENDERING_MODE_ULTRA -> R.string.pref_ass_rendering_mode_ultra_desc
    else -> R.string.pref_ass_rendering_mode_performance_desc
}

internal fun SharedPreferences.assRenderingMode(): String =
    assRenderingModeFromValues(all)
