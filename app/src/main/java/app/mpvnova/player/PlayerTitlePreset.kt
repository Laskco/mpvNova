package app.mpvnova.player

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class PlayerTitleCustomPreset(
    val name: String,
    val style: PlayerTitleStyle,
)

internal object PlayerTitleCustomPresetStore {
    private const val KEY = "player_title_custom_presets"
    private const val MAX_PRESETS = 24

    fun read(prefs: SharedPreferences): List<PlayerTitleCustomPreset> {
        val array = prefs.getString(KEY, null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_PRESETS)) {
                val preset = array.optJSONObject(index)?.toPreset() ?: continue
                if (none { it.name.equals(preset.name, ignoreCase = true) }) add(preset)
            }
        }
    }

    fun write(prefs: SharedPreferences, presets: List<PlayerTitleCustomPreset>) {
        val unique = presets.fold(mutableListOf<PlayerTitleCustomPreset>()) { result, preset ->
            val name = normalizedCustomPresetName(preset.name)
            if (name.isNotBlank()) {
                result.removeAll { it.name.equals(name, ignoreCase = true) }
                result += PlayerTitleCustomPreset(name, preset.style.normalized())
            }
            result
        }.takeLast(MAX_PRESETS)
        prefs.edit().putString(
            KEY,
            JSONArray().apply { unique.forEach { put(it.toJson()) } }.toString(),
        ).apply()
    }

    private fun PlayerTitleCustomPreset.toJson() = JSONObject().apply {
        put("name", name)
        put("style", style.toJson())
    }

    private fun JSONObject.toPreset(): PlayerTitleCustomPreset? {
        val name = normalizedCustomPresetName(optString("name"))
        val style = optJSONObject("style")?.toPlayerTitleStyle() ?: return null
        return name.takeIf(String::isNotBlank)?.let { PlayerTitleCustomPreset(it, style) }
    }
}

internal fun PlayerTitleStyle.normalized(): PlayerTitleStyle = copy(
    season = season.normalized(PlayerTitleStyle.DEFAULT.season),
    episodeNumber = episodeNumber.normalized(PlayerTitleStyle.DEFAULT.episodeNumber),
    title = title.normalized(PlayerTitleStyle.DEFAULT.title),
    episodeTitle = episodeTitle.normalized(PlayerTitleStyle.DEFAULT.episodeTitle),
    date = date.normalized(PlayerTitleStyle.DEFAULT.date),
    clock = clock.normalized(PlayerTitleStyle.DEFAULT.clock),
    endsAt = endsAt.normalized(PlayerTitleStyle.DEFAULT.endsAt),
    titleOrder = titleOrder.validOrderOr(PlayerTitleUnit.entries),
    clockOrder = clockOrder.validOrderOr(PlayerClockUnit.entries),
    titlePanel = titlePanel.normalized(),
    clockPanel = clockPanel.normalized(),
)

private fun PlayerTitleTextStyle.normalized(defaults: PlayerTitleTextStyle) = copy(
    font = font.takeIf { it == PlayerTitleStyle.INHERIT_FONT || UiFont.hasChoice(it) } ?: defaults.font,
    sizeSp = sizeSp.coerceIn(PLAYER_TITLE_MIN_CUSTOM_SIZE_SP, PLAYER_TITLE_MAX_CUSTOM_SIZE_SP),
    letterSpacing = letterSpacing.coerceIn(
        PLAYER_TITLE_MIN_LETTER_SPACING,
        PLAYER_TITLE_MAX_LETTER_SPACING,
    ),
    opacityPercent = opacityPercent.coerceIn(
        PLAYER_TITLE_MIN_OPACITY_PERCENT,
        PLAYER_TITLE_MAX_OPACITY_PERCENT,
    ),
    outlineWidthDp = outlineWidthDp.coerceIn(
        PLAYER_TITLE_MIN_OUTLINE_WIDTH_DP,
        PLAYER_TITLE_MAX_OUTLINE_WIDTH_DP,
    ),
    shadowStrengthPercent = shadowStrengthPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
    backgroundStrengthPercent = backgroundStrengthPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
)

private fun <T> List<T>.validOrderOr(defaults: List<T>): List<T> =
    takeIf { size == defaults.size && toSet() == defaults.toSet() } ?: defaults

private fun PlayerTitleStyle.toJson() = JSONObject().apply {
    put("season", season.toJson())
    put("episodeNumber", episodeNumber.toJson())
    put("title", title.toJson())
    put("episodeTitle", episodeTitle.toJson())
    put("date", date.toJson())
    put("clock", clock.toJson())
    put("endsAt", endsAt.toJson())
    put("separator", separator.name)
    put("titleOrder", JSONArray(titleOrder.map(PlayerTitleUnit::name)))
    put("clockOrder", JSONArray(clockOrder.map(PlayerClockUnit::name)))
    put("titlePanel", titlePanel.toJson())
    put("clockPanel", clockPanel.toJson())
}

private fun PlayerTitleTextStyle.toJson() = JSONObject().apply {
    put("font", font)
    put("size", sizeSp.toDouble())
    put("weight", weight.name)
    put("spacing", letterSpacing.toDouble())
    put("color", color.name)
    put("shadow", shadow.name)
    put("italic", italic)
    put("opacity", opacityPercent)
    put("visible", visible)
    put("textCase", textCase.name)
    put("outlineWidth", outlineWidthDp.toDouble())
    put("outlineColor", outlineColor.name)
    put("shadowStrength", shadowStrengthPercent)
    put("backgroundEnabled", backgroundEnabled)
    put("backgroundStrength", backgroundStrengthPercent)
}

private fun PlayerTitlePanelStyle.toJson() = JSONObject().apply {
    put("surface", surface.prefValue)
    put("opacity", opacityPercent)
    put("accentStrength", accentStrengthPercent)
    put("gradient", gradientEnabled)
    put("outline", outlineEnabled)
    put("outlineWidth", outlineWidthDp)
    put("cornerRadius", cornerRadiusDp)
    put("elevation", elevationDp)
    put("horizontalPadding", horizontalPaddingDp)
    put("verticalPadding", verticalPaddingDp)
    put("contentSpacing", contentSpacingDp)
    put("alignment", alignment.name)
    put("contentAlignment", contentAlignment.name)
    put("width", widthPercent)
    put("verticalOffset", verticalOffsetDp)
    put("manualPosition", manualPosition)
    put("horizontalOffset", horizontalOffsetDp)
}

private fun JSONObject.toPlayerTitleStyle(): PlayerTitleStyle {
    val defaults = PlayerTitleStyle.DEFAULT
    return PlayerTitleStyle(
        season = optJSONObject("season").toTextStyle(defaults.season),
        episodeNumber = optJSONObject("episodeNumber").toTextStyle(defaults.episodeNumber),
        title = optJSONObject("title").toTextStyle(defaults.title),
        episodeTitle = optJSONObject("episodeTitle").toTextStyle(defaults.episodeTitle),
        date = optJSONObject("date").toTextStyle(defaults.date),
        clock = optJSONObject("clock").toTextStyle(defaults.clock),
        endsAt = optJSONObject("endsAt").toTextStyle(defaults.endsAt),
        separator = enumValueOrDefault(nullableString("separator"), defaults.separator),
        titleOrder = optJSONArray("titleOrder").enumList<PlayerTitleUnit>()
            .validOrderOr(defaults.titleOrder),
        clockOrder = optJSONArray("clockOrder").enumList<PlayerClockUnit>()
            .validOrderOr(defaults.clockOrder),
        titlePanel = optJSONObject("titlePanel").toPanelStyle(defaults.titlePanel),
        clockPanel = optJSONObject("clockPanel").toPanelStyle(defaults.clockPanel),
    ).normalized()
}

private fun JSONObject?.toTextStyle(defaults: PlayerTitleTextStyle): PlayerTitleTextStyle {
    if (this == null) return defaults
    return PlayerTitleTextStyle(
        font = optString("font", defaults.font),
        sizeSp = optDouble("size", defaults.sizeSp.toDouble()).toFloat(),
        weight = enumValueOrDefault(nullableString("weight"), defaults.weight),
        letterSpacing = optDouble("spacing", defaults.letterSpacing.toDouble()).toFloat(),
        color = enumValueOrDefault(nullableString("color"), defaults.color),
        shadow = enumValueOrDefault(nullableString("shadow"), defaults.shadow),
        italic = optBoolean("italic", defaults.italic),
        opacityPercent = optInt("opacity", defaults.opacityPercent),
        visible = optBoolean("visible", defaults.visible),
        textCase = enumValueOrDefault(nullableString("textCase"), defaults.textCase),
        outlineWidthDp = optDouble("outlineWidth", defaults.outlineWidthDp.toDouble()).toFloat(),
        outlineColor = enumValueOrDefault(nullableString("outlineColor"), defaults.outlineColor),
        shadowStrengthPercent = optInt("shadowStrength", defaults.shadowStrengthPercent),
        backgroundEnabled = optBoolean("backgroundEnabled", defaults.backgroundEnabled),
        backgroundStrengthPercent = optInt("backgroundStrength", defaults.backgroundStrengthPercent),
    ).normalized(defaults)
}

private fun JSONObject?.toPanelStyle(defaults: PlayerTitlePanelStyle): PlayerTitlePanelStyle {
    if (this == null) return defaults
    return PlayerTitlePanelStyle(
        surface = PlayerPanelSurface.fromPref(nullableString("surface")),
        opacityPercent = optInt("opacity", defaults.opacityPercent),
        accentStrengthPercent = optInt("accentStrength", defaults.accentStrengthPercent),
        gradientEnabled = optBoolean("gradient", defaults.gradientEnabled),
        outlineEnabled = optBoolean("outline", defaults.outlineEnabled),
        outlineWidthDp = optInt("outlineWidth", defaults.outlineWidthDp),
        cornerRadiusDp = optInt("cornerRadius", defaults.cornerRadiusDp),
        elevationDp = optInt("elevation", defaults.elevationDp),
        horizontalPaddingDp = optInt("horizontalPadding", defaults.horizontalPaddingDp),
        verticalPaddingDp = optInt("verticalPadding", defaults.verticalPaddingDp),
        contentSpacingDp = optInt("contentSpacing", defaults.contentSpacingDp),
        alignment = enumValueOrDefault(nullableString("alignment"), defaults.alignment),
        contentAlignment = enumValueOrDefault(
            nullableString("contentAlignment"),
            defaults.contentAlignment,
        ),
        widthPercent = optInt("width", defaults.widthPercent),
        verticalOffsetDp = optInt("verticalOffset", defaults.verticalOffsetDp),
        manualPosition = optBoolean("manualPosition", defaults.manualPosition),
        horizontalOffsetDp = optInt("horizontalOffset", defaults.horizontalOffsetDp),
    ).normalized()
}

private inline fun <reified T : Enum<T>> JSONArray?.enumList(): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        enumValues<T>().firstOrNull { it.name == optString(index) }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
