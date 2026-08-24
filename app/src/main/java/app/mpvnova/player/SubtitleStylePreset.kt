package app.mpvnova.player

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class SubtitleStylePreset(
    val name: String,
    val textColorId: String,
    val textOpacity: Int,
    val borderColorId: String,
    val borderSizeIndex: Int,
    val blurIndex: Int,
    val shadowSizeIndex: Int,
    val shadowColorId: String,
    val bgColorId: String,
    val bgOpacity: Int,
    val edge: String,
    val fontFamily: String,
    val bold: Boolean,
    val italic: Boolean,
    val spacingIndex: Int,
    val justify: String,
    val overrideAss: Boolean,
    val selectiveAss: Boolean,
    val forceAll: Boolean,
    val grayImageSubs: Boolean,
    val includeLayout: Boolean,
    val scaleLevel: Int,
    val posPct: Int,
)

private const val SUB_STYLE_PRESETS_KEY = "sub_style_presets"

internal fun loadSubtitleStylePresets(prefs: SharedPreferences): List<SubtitleStylePreset> {
    val raw = prefs.getString(SUB_STYLE_PRESETS_KEY, null)
    val array = raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let(::presetFromJson)
    }
}

internal fun saveSubtitleStylePresets(prefs: SharedPreferences, presets: List<SubtitleStylePreset>) {
    val array = JSONArray()
    presets.forEach { array.put(presetToJson(it)) }
    prefs.edit().putString(SUB_STYLE_PRESETS_KEY, array.toString()).apply()
}

private fun presetToJson(p: SubtitleStylePreset): JSONObject = JSONObject().apply {
    put("name", p.name)
    put("textColorId", p.textColorId)
    put("textOpacity", p.textOpacity)
    put("borderColorId", p.borderColorId)
    put("borderSizeIndex", p.borderSizeIndex)
    put("blurIndex", p.blurIndex)
    put("shadowSizeIndex", p.shadowSizeIndex)
    put("shadowColorId", p.shadowColorId)
    put("bgColorId", p.bgColorId)
    put("bgOpacity", p.bgOpacity)
    put("edge", p.edge)
    put("fontFamily", p.fontFamily)
    put("bold", p.bold)
    put("italic", p.italic)
    put("spacingIndex", p.spacingIndex)
    put("justify", p.justify)
    put("overrideAss", p.overrideAss)
    put("selectiveAss", p.selectiveAss)
    put("forceAll", p.forceAll)
    put("grayImageSubs", p.grayImageSubs)
    put("includeLayout", p.includeLayout)
    put("scaleLevel", p.scaleLevel)
    put("scaleStepsVersion", SUB_SCALE_STEPS_VERSION)
    put("posPct", p.posPct)
}

private fun presetFromJson(o: JSONObject): SubtitleStylePreset? {
    val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
    return SubtitleStylePreset(
        name = name,
        textColorId = o.optString("textColorId", SUBTITLE_TEXT_COLOR_DEFAULT_ID),
        textOpacity = o.optInt("textOpacity", DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT),
        borderColorId = o.optString("borderColorId", SUBTITLE_BORDER_COLOR_DEFAULT_ID),
        borderSizeIndex = o.optInt("borderSizeIndex", DEFAULT_SUBTITLE_BORDER_INDEX),
        blurIndex = o.optInt("blurIndex", DEFAULT_SUBTITLE_BLUR_INDEX),
        shadowSizeIndex = o.optInt("shadowSizeIndex", DEFAULT_SUBTITLE_SHADOW_SIZE_INDEX),
        shadowColorId = o.optString("shadowColorId", SUBTITLE_SHADOW_COLOR_DEFAULT_ID),
        bgColorId = o.optString("bgColorId", SUBTITLE_BG_COLOR_DEFAULT_ID),
        bgOpacity = o.optInt("bgOpacity", DEFAULT_SUBTITLE_BG_OPACITY_PERCENT),
        edge = o.optString("edge", DEFAULT_SUBTITLE_EDGE_STYLE.name),
        fontFamily = o.optString("fontFamily", SUBTITLE_FONT_DEFAULT_FAMILY),
        bold = o.optBoolean("bold", false),
        italic = o.optBoolean("italic", false),
        spacingIndex = o.optInt("spacingIndex", DEFAULT_SUBTITLE_SPACING_INDEX),
        justify = o.optString("justify", DEFAULT_SUBTITLE_JUSTIFY.name),
        overrideAss = o.optBoolean("overrideAss", false),
        selectiveAss = o.optBoolean("selectiveAss", false),
        forceAll = o.optBoolean("forceAll", false),
        grayImageSubs = o.optBoolean("grayImageSubs", false),
        includeLayout = o.optBoolean("includeLayout", false),
        scaleLevel = migrateSubScaleLevel(
            o.optInt("scaleLevel", DEFAULT_SUB_SCALE_INDEX),
            o.optInt("scaleStepsVersion", 1),
        ),
        posPct = o.optInt("posPct", DEFAULT_SUB_POSITION_PERCENT),
    )
}
