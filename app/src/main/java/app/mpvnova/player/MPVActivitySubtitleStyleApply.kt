package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

private val SUB_STYLE_PROPS = listOf(
    "sub-color",
    "sub-border-color",
    "sub-border-size",
    "sub-blur",
    "sub-back-color",
    "sub-border-style",
    "sub-shadow-offset",
    "sub-shadow-color",
    "sub-spacing",
    "sub-justify",
    "sub-ass-justify",
    "sub-font",
    "sub-bold",
    "sub-italic",
    "sub-ass-override",
    "sub-ass-style-overrides",
)

private const val SUB_SHADOW_OFFSET_OFF = 0.0
private const val SUB_TRANSPARENT = 0x000000
private const val FULLY_OPAQUE_PERCENT = 100

internal fun MPVActivity.applyCustomSubtitleStyle() {
    if (customSubStyleEnabled) {
        snapshotSubStyleBaselineIfNeeded()
        writeCustomSubtitleStyle()
    } else {
        restoreSubStyleBaseline()
    }
    applyAssStyleOverrides()
    mpvSetPropertyString("sub-gray", if (subStyleGrayImageSubs) "yes" else "no")
}

// ASS style overrides are parsed as the track loads. Omitting a style name applies the value to
// every style definition, which also covers releases that call dialogue "Main" or "Dialogue"
// instead of "Default". Inline tags and positioning remain authored by the subtitle script.
private fun MPVActivity.applyAssStyleOverrides() {
    val overrides = when {
        customSubStyleEnabled && subStyleSelectiveAss -> buildAssStyleOverrides(
            AssStyleOverrideSpec(
                fontFamily = subStyleFontFamily.ifEmpty { SUBTITLE_FONT_DEFAULT_FAMILY },
                textRgb = SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex].rgb,
                textOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex],
                borderRgb = SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex].rgb,
                borderSize = SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex],
                backgroundRgb = SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex].rgb,
                backgroundOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex],
                shadowRgb = SUBTITLE_COLOR_OPTIONS[subStyleShadowColorIndex].rgb,
                shadowSize = SUBTITLE_SHADOW_SIZE_STEPS[subStyleShadowSizeIndex],
                edge = subStyleEdge,
                bold = subStyleBold,
                italic = subStyleItalic,
                spacing = SUBTITLE_SPACING_STEPS[subStyleSpacingIndex],
                blur = SUBTITLE_BLUR_STEPS[subStyleBlurIndex],
            ),
        )
        customSubStyleEnabled && subStyleOverrideAss -> buildAssAttributeOverrides(
            bold = subStyleBold,
            italic = subStyleItalic,
        )
        else -> null
    }
    val baseline = subStyleSavedDefaults?.get("sub-ass-style-overrides").orEmpty()
    val signature = overrides?.joinToString(prefix = "custom:", separator = "\u0000")
        ?: "baseline:$baseline"
    val previous = subStyleAppliedAssOverrides
    if (signature == previous)
        return
    subStyleAppliedAssOverrides = signature

    if (overrides != null) {
        mpvCommand(arrayOf("change-list", "sub-ass-style-overrides", "clr", ""))
        overrides.forEach { value ->
            mpvCommand(arrayOf("change-list", "sub-ass-style-overrides", "append", value))
        }
    } else if (previous?.startsWith("custom:") == true) {
        mpvSetPropertyString("sub-ass-style-overrides", baseline)
    }

    if (overrides != null || previous?.startsWith("custom:") == true)
        mpvCommand(arrayOf("sub-reload"))
}

// Only carries to the next file when persist is on; the saved design sticks around either way.
internal fun MPVActivity.applyCustomSubtitleStyleOnFileLoad() {
    if (!persistSubFilters && customSubStyleEnabled)
        customSubStyleEnabled = false
    if (!persistSubFilters && subStyleGrayImageSubs)
        subStyleGrayImageSubs = false
    applyCustomSubtitleStyle()
}

private fun MPVActivity.snapshotSubStyleBaselineIfNeeded() {
    if (subStyleSavedDefaults != null) return
    subStyleSavedDefaults = SUB_STYLE_PROPS.associateWith { mpvGetPropertyString(it) }
}

private fun MPVActivity.restoreSubStyleBaseline() {
    val saved = subStyleSavedDefaults ?: return
    for ((key, value) in saved) {
        if (value != null)
            mpvSetPropertyString(key, value)
    }
}

private fun MPVActivity.writeCustomSubtitleStyle() {
    val textColor = SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex]
    val textOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex]
    mpvSetPropertyString("sub-color", mpvSubtitleColor(textColor.rgb, textOpacity))

    val borderColor = SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex]
    mpvSetPropertyString("sub-border-color", mpvSubtitleColor(borderColor.rgb, FULLY_OPAQUE_PERCENT))
    mpvSetPropertyDouble("sub-border-size", SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex])
    mpvSetPropertyDouble("sub-blur", SUBTITLE_BLUR_STEPS[subStyleBlurIndex])

    val bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex]
    if (bgOpacity > 0) {
        // Background box and outline/edge are mutually exclusive modes.
        val bgColor = SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex]
        mpvSetPropertyString("sub-border-style", "background-box")
        mpvSetPropertyString("sub-back-color", mpvSubtitleColor(bgColor.rgb, bgOpacity))
        mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_OFF)
    } else {
        mpvSetPropertyString("sub-border-style", "outline-and-shadow")
        mpvSetPropertyString("sub-back-color", mpvSubtitleColor(SUB_TRANSPARENT, 0))
        applySubEdge()
    }

    mpvSetPropertyDouble("sub-spacing", SUBTITLE_SPACING_STEPS[subStyleSpacingIndex])
    mpvSetPropertyString("sub-justify", subStyleJustify.mpvValue)
    mpvSetPropertyString("sub-ass-justify", if (subStyleJustify == SubtitleJustify.AUTO) "no" else "yes")

    applySubFont()
    mpvSetPropertyString("sub-bold", if (subStyleBold) "yes" else "no")
    mpvSetPropertyString("sub-italic", if (subStyleItalic) "yes" else "no")
    // Mutually-exclusive ASS override levels (at most one is on):
    //  "force" = restyle every ASS style, signs included (positioning kept).
    //  "scale" = preserve inline ASS tags/positioning while our style-definition overrides
    //            restyle named styles; also keeps the user's subtitle scale active.
    //  "strip" = remove the script's own styling so our style lands on EVERY line, even releases
    //            that put dialogue on named styles instead of "Default" (also flattens typesetting).
    val assOverride = when {
        subStyleForceAllAss -> "strip"
        subStyleOverrideAss -> "force"
        subStyleSelectiveAss -> "scale"
        else -> "scale"
    }
    mpvSetPropertyString("sub-ass-override", assOverride)
}

private fun MPVActivity.applySubEdge() {
    when (subStyleEdge) {
        SubtitleEdgeStyle.NONE -> {
            mpvSetPropertyDouble("sub-border-size", 0.0)
            mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_OFF)
        }
        SubtitleEdgeStyle.OUTLINE -> {
            mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_OFF)
        }
        SubtitleEdgeStyle.DROP_SHADOW -> {
            val shadowColor = SUBTITLE_COLOR_OPTIONS[subStyleShadowColorIndex]
            mpvSetPropertyString("sub-shadow-color", mpvSubtitleColor(shadowColor.rgb, FULLY_OPAQUE_PERCENT))
            mpvSetPropertyDouble("sub-shadow-offset", SUBTITLE_SHADOW_SIZE_STEPS[subStyleShadowSizeIndex])
        }
    }
}

private fun MPVActivity.applySubFont() {
    mpvSetPropertyString("sub-font", subStyleFontFamily.ifEmpty { SUBTITLE_FONT_DEFAULT_FAMILY })
}

internal fun MPVActivity.readSubtitleStyleSettings(prefs: SharedPreferences) {
    customSubStyleEnabled = persistSubFilters && prefs.getBoolean("custom_sub_style_enabled", false)
    subStyleTextColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_text_color", SUBTITLE_TEXT_COLOR_DEFAULT_ID) ?: SUBTITLE_TEXT_COLOR_DEFAULT_ID
    )
    subStyleTextOpacityIndex = nearestOpacityIndex(
        prefs.getInt("sub_style_text_opacity", DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT)
    )
    subStyleBorderColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_border_color", SUBTITLE_BORDER_COLOR_DEFAULT_ID) ?: SUBTITLE_BORDER_COLOR_DEFAULT_ID
    )
    subStyleBorderSizeIndex = prefs.getInt("sub_style_border_size", DEFAULT_SUBTITLE_BORDER_INDEX)
        .coerceIn(0, SUBTITLE_BORDER_SIZE_STEPS.lastIndex)
    subStyleBlurIndex = prefs.getInt("sub_style_blur", DEFAULT_SUBTITLE_BLUR_INDEX)
        .coerceIn(0, SUBTITLE_BLUR_STEPS.lastIndex)
    subStyleShadowSizeIndex = prefs.getInt("sub_style_shadow_size", DEFAULT_SUBTITLE_SHADOW_SIZE_INDEX)
        .coerceIn(0, SUBTITLE_SHADOW_SIZE_STEPS.lastIndex)
    subStyleShadowColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_shadow_color", SUBTITLE_SHADOW_COLOR_DEFAULT_ID) ?: SUBTITLE_SHADOW_COLOR_DEFAULT_ID
    )
    subStyleSpacingIndex = prefs.getInt("sub_style_spacing", DEFAULT_SUBTITLE_SPACING_INDEX)
        .coerceIn(0, SUBTITLE_SPACING_STEPS.lastIndex)
    subStyleJustify = runCatching {
        SubtitleJustify.valueOf(prefs.getString("sub_style_justify", DEFAULT_SUBTITLE_JUSTIFY.name)!!)
    }.getOrDefault(DEFAULT_SUBTITLE_JUSTIFY)
    subStyleBgColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_bg_color", SUBTITLE_BG_COLOR_DEFAULT_ID) ?: SUBTITLE_BG_COLOR_DEFAULT_ID
    )
    subStyleBgOpacityIndex = nearestOpacityIndex(
        prefs.getInt("sub_style_bg_opacity", DEFAULT_SUBTITLE_BG_OPACITY_PERCENT)
    )
    subStyleEdge = runCatching {
        SubtitleEdgeStyle.valueOf(prefs.getString("sub_style_edge", DEFAULT_SUBTITLE_EDGE_STYLE.name)!!)
    }.getOrDefault(DEFAULT_SUBTITLE_EDGE_STYLE)
    subStyleFontFamily = (prefs.getString("sub_style_font_family", SUBTITLE_FONT_DEFAULT_FAMILY)
        ?: SUBTITLE_FONT_DEFAULT_FAMILY).ifEmpty { SUBTITLE_FONT_DEFAULT_FAMILY }
    subStyleBold = prefs.getBoolean("sub_style_bold", false)
    subStyleItalic = prefs.getBoolean("sub_style_italic", false)
    subStyleGrayImageSubs = persistSubFilters && prefs.getBoolean("sub_style_gray_image_subs", false)
    subStyleOverrideAss = prefs.getBoolean("sub_style_override_ass", false)
    subStyleSelectiveAss = prefs.getBoolean("sub_style_selective_ass", false)
    subStyleForceAllAss = prefs.getBoolean("sub_style_force_all_ass", false)
    normalizeAssOverrideModes()
}

// The three ASS-override modes are mutually exclusive; collapse any legacy/loaded combination
// to a single active mode by precedence (strip > force > selective).
internal fun MPVActivity.normalizeAssOverrideModes() {
    when {
        subStyleForceAllAss -> { subStyleOverrideAss = false; subStyleSelectiveAss = false }
        subStyleOverrideAss -> subStyleSelectiveAss = false
    }
}

internal fun MPVActivity.writeSubtitleStyleSettings() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    with(prefs.edit()) {
        putBoolean("custom_sub_style_enabled", customSubStyleEnabled)
        putString("sub_style_text_color", SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex].id)
        putInt("sub_style_text_opacity", SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex])
        putString("sub_style_border_color", SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex].id)
        putInt("sub_style_border_size", subStyleBorderSizeIndex)
        putInt("sub_style_blur", subStyleBlurIndex)
        putInt("sub_style_shadow_size", subStyleShadowSizeIndex)
        putString("sub_style_shadow_color", SUBTITLE_COLOR_OPTIONS[subStyleShadowColorIndex].id)
        putInt("sub_style_spacing", subStyleSpacingIndex)
        putString("sub_style_justify", subStyleJustify.name)
        putString("sub_style_bg_color", SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex].id)
        putInt("sub_style_bg_opacity", SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex])
        putString("sub_style_edge", subStyleEdge.name)
        putString("sub_style_font_family", subStyleFontFamily)
        putBoolean("sub_style_bold", subStyleBold)
        putBoolean("sub_style_italic", subStyleItalic)
        putBoolean("sub_style_gray_image_subs", subStyleGrayImageSubs)
        putBoolean("sub_style_override_ass", subStyleOverrideAss)
        putBoolean("sub_style_selective_ass", subStyleSelectiveAss)
        putBoolean("sub_style_force_all_ass", subStyleForceAllAss)
        apply()
    }
}
