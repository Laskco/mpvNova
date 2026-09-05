package app.mpvnova.player

import android.content.SharedPreferences
import org.json.JSONObject

internal enum class SubtitleFontHinting(val mpvValue: String?, val label: Int) {
    DEFAULT(null, R.string.sub_editor_default),
    LIGHT("light", R.string.sub_editor_hinting_light),
    NORMAL("normal", R.string.sub_editor_hinting_normal),
    NATIVE("native", R.string.sub_editor_hinting_native),
}

internal data class SubtitleStyleExtras(
    val fontSize: Int? = null,
    val lineSpacing: Int? = null,
    val sideMargin: Int? = null,
    val alignment: SubtitleJustify = SubtitleJustify.AUTO,
    val outlineOpacity: Int = 100,
    val shadowOpacity: Int = 100,
    val fontHinting: SubtitleFontHinting = SubtitleFontHinting.DEFAULT,
) {
    fun normalized() = copy(
        fontSize = fontSize?.coerceIn(SUBTITLE_EDITOR_FONT_RANGE),
        lineSpacing = lineSpacing?.coerceIn(SUBTITLE_EDITOR_LINE_RANGE),
        sideMargin = sideMargin?.coerceIn(SUBTITLE_EDITOR_MARGIN_RANGE),
        outlineOpacity = outlineOpacity.coerceIn(MIN_PERCENT, MAX_PERCENT),
        shadowOpacity = shadowOpacity.coerceIn(MIN_PERCENT, MAX_PERCENT),
    )

    fun toJson() = JSONObject().apply {
        put("fontSize", fontSize)
        put("lineSpacing", lineSpacing)
        put("sideMargin", sideMargin)
        put("alignment", alignment.name)
        put("outlineOpacity", outlineOpacity)
        put("shadowOpacity", shadowOpacity)
        put("fontHinting", fontHinting.name)
    }

    fun mpvOptions(): Map<String, String?> = mapOf(
        "sub-font-size" to fontSize?.toString(),
        "sub-line-spacing" to lineSpacing?.toString(),
        "sub-margin-x" to sideMargin?.toString(),
        "sub-align-x" to alignment.takeUnless { it == SubtitleJustify.AUTO }?.mpvValue,
        "sub-hinting" to fontHinting.mpvValue,
    )

    companion object {
        const val PREF_KEY = "sub_style_extras"

        fun fromJson(json: JSONObject?): SubtitleStyleExtras {
            fun number(key: String): Int? = (json?.opt(key) as? Number)?.toInt()
            return SubtitleStyleExtras(
                fontSize = number("fontSize"),
                lineSpacing = number("lineSpacing"),
                sideMargin = number("sideMargin"),
                alignment = SubtitleJustify.entries.firstOrNull { it.name == json?.optString("alignment") }
                    ?: SubtitleJustify.AUTO,
                outlineOpacity = number("outlineOpacity") ?: 100,
                shadowOpacity = number("shadowOpacity") ?: 100,
                fontHinting = SubtitleFontHinting.entries.firstOrNull { it.name == json?.optString("fontHinting") }
                    ?: SubtitleFontHinting.DEFAULT,
            ).normalized()
        }

        fun read(prefs: SharedPreferences): SubtitleStyleExtras = fromJson(
            (prefs.all[PREF_KEY] as? String)?.let { runCatching { JSONObject(it) }.getOrNull() },
        )
    }
}

internal fun MPVActivity.adjustSubtitleExtras(control: SubtitleStyleDialog.Control, delta: Int) {
    val s = subStyleExtras
    subStyleExtras = when (control) {
        SubtitleStyleDialog.Control.FONT_SIZE -> s.copy(fontSize = stepSubtitleFontSize(s.fontSize, delta))
        SubtitleStyleDialog.Control.LINE_SPACING ->
            s.copy(lineSpacing = stepSubtitleOptional(s.lineSpacing, delta, SUBTITLE_EDITOR_LINE_RANGE, 0))
        SubtitleStyleDialog.Control.SIDE_MARGIN ->
            s.copy(sideMargin = stepSubtitleOptional(
                s.sideMargin, delta, SUBTITLE_EDITOR_MARGIN_RANGE, SUBTITLE_EDITOR_DEFAULT_SIDE_MARGIN,
            ))
        SubtitleStyleDialog.Control.ALIGNMENT -> s.copy(alignment = SubtitleJustify.entries[
            (s.alignment.ordinal + delta + SubtitleJustify.entries.size) % SubtitleJustify.entries.size
        ])
        SubtitleStyleDialog.Control.OUTLINE_OPACITY -> s.copy(outlineOpacity = s.outlineOpacity + delta * 5)
        SubtitleStyleDialog.Control.SHADOW_OPACITY -> s.copy(shadowOpacity = s.shadowOpacity + delta * 5)
        SubtitleStyleDialog.Control.FONT_HINTING -> s.copy(fontHinting = SubtitleFontHinting.entries[
            (s.fontHinting.ordinal + delta).mod(SubtitleFontHinting.entries.size)
        ])
        else -> s
    }.normalized()
}

// Default is a distinct option before the numeric range; returning to it restores mpv.conf's value.
internal fun stepSubtitleOptional(current: Int?, delta: Int, range: IntRange, default: Int): Int? = when {
    current == null -> default.coerceIn(range)
    current == range.first && delta < 0 -> null
    else -> (current + delta).coerceIn(range)
}

internal fun stepSubtitleFontSize(current: Int?, delta: Int): Int? {
    val range = SUBTITLE_EDITOR_FONT_RANGE
    val count = range.last - range.first + 2
    val index = current?.coerceIn(range)?.let { it - range.first + 1 } ?: 0
    val next = (index + delta).mod(count)
    return if (next == 0) null else range.first + next - 1
}

internal const val SUBTITLE_EDITOR_DEFAULT_FONT_SIZE = 38
internal const val SUBTITLE_EDITOR_DEFAULT_SIDE_MARGIN = 19
internal val SUBTITLE_EDITOR_FONT_RANGE = 16..80
internal val SUBTITLE_EDITOR_LINE_RANGE = -5..30
internal val SUBTITLE_EDITOR_MARGIN_RANGE = 0..200
