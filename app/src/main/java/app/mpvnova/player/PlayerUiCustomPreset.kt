package app.mpvnova.player

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class PlayerUiCustomPreset(
    val name: String,
    val style: PlayerUiCustomization,
)

internal object PlayerUiCustomPresetStore {
    private const val KEY = "player_ui_custom_presets"
    private const val MAX_PRESETS = 24

    fun read(prefs: SharedPreferences): List<PlayerUiCustomPreset> {
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

    fun write(prefs: SharedPreferences, presets: List<PlayerUiCustomPreset>) {
        val unique = presets.fold(mutableListOf<PlayerUiCustomPreset>()) { result, preset ->
            val name = normalizedCustomPresetName(preset.name)
            if (name.isNotBlank()) {
                result.removeAll { it.name.equals(name, ignoreCase = true) }
                result += PlayerUiCustomPreset(name, preset.style.normalized())
            }
            result
        }.takeLast(MAX_PRESETS)
        prefs.edit().putString(
            KEY,
            JSONArray().apply { unique.forEach { put(it.toJson()) } }.toString(),
        ).apply()
    }

    private fun PlayerUiCustomPreset.toJson() = JSONObject().apply {
        put("name", name)
        put("style", style.toJson())
    }

    private fun JSONObject.toPreset(): PlayerUiCustomPreset? {
        val name = normalizedCustomPresetName(optString("name"))
        val style = optJSONObject("style")?.toPlayerUiStyle() ?: return null
        return name.takeIf(String::isNotBlank)?.let { PlayerUiCustomPreset(it, style) }
    }
}

private fun PlayerUiCustomization.toJson() = JSONObject().apply {
    put("surface", surface.prefValue)
    put("panelOpacity", panelOpacityPercent)
    put("scrimStrength", scrimStrengthPercent)
    put("gradient", gradientEnabled)
    put("panelOutline", panelOutlineEnabled)
    put("panelOutlineWidth", panelOutlineWidthDp)
    put("cornerRadius", cornerRadiusDp)
    put("width", widthPercent)
    put("density", density.prefValue)
    put("verticalOffset", verticalOffsetDp)
    put("horizontalPadding", horizontalPaddingDp)
    put("topPadding", topPaddingDp)
    put("bottomPadding", bottomPaddingDp)
    put("rowSpacing", rowSpacingDp)
    put("panelElevation", panelElevationDp)
    put("seekbarSize", seekbarSize.prefValue)
    put("seekbarPosition", seekbarPosition.prefValue)
    put("seekbarInset", seekbarInsetDp)
    put("thumbSize", seekbarThumbSize.prefValue)
    put("thumbShape", seekbarThumbShape.prefValue)
    put("thumbGlow", seekbarThumbGlowEnabled)
    put("thumbColor", seekbarThumbColor.name)
    put("seekbarVisible", seekbarVisible)
    put("chapterMarkers", chapterMarkersVisible)
    put("timeVisible", timeVisible)
    put("timeTextSize", timeTextSizeSp)
    put("timePosition", timePosition.prefValue)
    put("timePresentation", timePresentation.prefValue)
    put("timeControlGap", timeControlGapDp)
    put("controlAlignment", controlAlignment.prefValue)
    put("controlSize", controlSize.prefValue)
    put("controlSpacing", controlSpacingDp)
    put("buttonTreatment", buttonTreatment.prefValue)
    put("iconTextOutline", iconTextOutlineEnabled)
    put("hiddenControls", JSONArray(hiddenControls.map(PlayerBarControl::prefValue)))
    put("controlOrder", JSONArray(controlOrder.map(PlayerBarControl::prefValue)))
}

private fun JSONObject.toPlayerUiStyle(): PlayerUiCustomization = PlayerUiCustomization(
    surface = PlayerPanelSurface.fromPref(nullableString("surface")),
    panelOpacityPercent = optInt("panelOpacity", PlayerUiCustomization.DEFAULT.panelOpacityPercent),
    scrimStrengthPercent = optInt("scrimStrength", PlayerUiCustomization.DEFAULT.scrimStrengthPercent),
    gradientEnabled = optBoolean("gradient", PlayerUiCustomization.DEFAULT.gradientEnabled),
    panelOutlineEnabled = optBoolean("panelOutline", PlayerUiCustomization.DEFAULT.panelOutlineEnabled),
    panelOutlineWidthDp = optInt("panelOutlineWidth", PlayerUiCustomization.DEFAULT.panelOutlineWidthDp),
    cornerRadiusDp = optInt("cornerRadius", PlayerUiCustomization.DEFAULT.cornerRadiusDp),
    widthPercent = optInt("width", PlayerUiCustomization.DEFAULT.widthPercent),
    density = PlayerPanelDensity.fromPref(nullableString("density")),
    verticalOffsetDp = optInt("verticalOffset", PlayerUiCustomization.DEFAULT.verticalOffsetDp),
    horizontalPaddingDp = optInt("horizontalPadding", PlayerUiCustomization.DEFAULT.horizontalPaddingDp),
    topPaddingDp = optInt("topPadding", PlayerUiCustomization.DEFAULT.topPaddingDp),
    bottomPaddingDp = optInt("bottomPadding", PlayerUiCustomization.DEFAULT.bottomPaddingDp),
    rowSpacingDp = optInt("rowSpacing", PlayerUiCustomization.DEFAULT.rowSpacingDp),
    panelElevationDp = optInt("panelElevation", PlayerUiCustomization.DEFAULT.panelElevationDp),
    seekbarSize = PlayerSeekbarSize.fromPref(nullableString("seekbarSize")),
    seekbarPosition = PlayerSeekbarPosition.fromPref(nullableString("seekbarPosition")),
    seekbarInsetDp = optInt("seekbarInset", PlayerUiCustomization.DEFAULT.seekbarInsetDp),
    seekbarThumbSize = PlayerSeekbarThumbSize.fromPref(nullableString("thumbSize")),
    seekbarThumbShape = PlayerSeekbarThumbShape.fromPref(nullableString("thumbShape")),
    seekbarThumbGlowEnabled = optBoolean("thumbGlow", PlayerUiCustomization.DEFAULT.seekbarThumbGlowEnabled),
    seekbarThumbColor = enumValueOrDefault(
        nullableString("thumbColor"),
        PlayerUiCustomization.DEFAULT.seekbarThumbColor,
    ),
    seekbarVisible = optBoolean("seekbarVisible", PlayerUiCustomization.DEFAULT.seekbarVisible),
    chapterMarkersVisible = optBoolean("chapterMarkers", PlayerUiCustomization.DEFAULT.chapterMarkersVisible),
    timeVisible = optBoolean("timeVisible", PlayerUiCustomization.DEFAULT.timeVisible),
    timeTextSizeSp = optInt("timeTextSize", PlayerUiCustomization.DEFAULT.timeTextSizeSp),
    timePosition = PlayerTimePosition.fromPref(nullableString("timePosition")),
    timePresentation = PlayerTimePresentation.fromPref(nullableString("timePresentation")),
    timeControlGapDp = optInt("timeControlGap", PlayerUiCustomization.DEFAULT.timeControlGapDp),
    controlAlignment = PlayerControlAlignment.fromPref(nullableString("controlAlignment")),
    controlSize = PlayerControlSize.fromPref(nullableString("controlSize")),
    controlSpacingDp = optInt("controlSpacing", PlayerUiCustomization.DEFAULT.controlSpacingDp),
    buttonTreatment = PlayerButtonTreatment.fromPref(nullableString("buttonTreatment")),
    iconTextOutlineEnabled = optBoolean("iconTextOutline", PlayerUiCustomization.DEFAULT.iconTextOutlineEnabled),
    hiddenControls = readPlayerUiPresetHiddenControls(),
    controlOrder = optJSONArray("controlOrder")?.playerControls()
        ?.takeIf(List<PlayerBarControl>::isNotEmpty)
        ?: PlayerUiCustomization.DEFAULT.controlOrder,
).normalized()

private fun JSONObject.readPlayerUiPresetHiddenControls(): Set<PlayerBarControl> {
    val hidden = optJSONArray("hiddenControls")?.playerControls()
        ?.filterTo(mutableSetOf()) { it.canHide }
        ?: return PlayerUiCustomization.DEFAULT.hiddenControls
    val savedOrder = optJSONArray("controlOrder")?.playerControls().orEmpty()
    if (savedOrder.isNotEmpty() && PlayerBarControl.PICTURE_IN_PICTURE !in savedOrder) {
        hidden += PlayerBarControl.PICTURE_IN_PICTURE
    }
    return hidden
}

private fun JSONArray.playerControls(): List<PlayerBarControl> {
    return (0 until length()).mapNotNull { PlayerBarControl.fromPref(optString(it)) }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
