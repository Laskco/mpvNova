package app.mpvnova.player

import java.util.Locale

private val NATIVE_ASS_CODECS = setOf("ass", "ssa")

internal fun MPVActivity.applyAssStyleOverrides(styleEnabled: Boolean) {
    val overrides = currentAssStyleOverrides(styleEnabled)
    val baseline = subStyleSavedDefaults?.get("sub-ass-style-overrides").orEmpty()
    val prefix = if (subStylePreservingCompanionTrack) "original:" else "custom:"
    val signature = overrides?.joinToString(prefix = prefix, separator = "\u0000")
        ?: "baseline:$baseline"
    val previous = subStyleAppliedAssOverrides
    if (signature == previous)
        return
    subStyleAppliedAssOverrides = signature
    val wasCustom = previous?.startsWith("custom:") == true
    val wasOriginal = previous?.startsWith("original:") == true

    if (overrides != null) {
        mpvCommand(arrayOf("change-list", "sub-ass-style-overrides", "clr", ""))
        overrides.forEach { value ->
            mpvCommand(arrayOf("change-list", "sub-ass-style-overrides", "append", value))
        }
    } else if (wasCustom || wasOriginal) {
        mpvSetPropertyString("sub-ass-style-overrides", baseline)
    }

    if (subStylePreservingCompanionTrack || wasOriginal) {
        // Unlike sub-reload, reselecting also rebuilds embedded ASS/bitmap subtitle decoders.
        rebuildSelectedSubtitleTracks()
    } else if (overrides != null || wasCustom) {
        mpvCommand(arrayOf("sub-reload"))
    }
}

private fun MPVActivity.isSelectedSubtitleAssTrack(): Boolean {
    val codec = mpvGetPropertyString("current-tracks/sub/codec")?.lowercase(Locale.US)
    return codec in NATIVE_ASS_CODECS
}

private fun MPVActivity.assWidthOverrides(styleEnabled: Boolean): List<String> {
    val applicable = isSubWidthOn() &&
        !subStylePreservingCompanionTrack &&
        !(styleEnabled && subStyleForceAllAss) &&
        isSelectedSubtitleAssTrack()
    if (!applicable) return emptyList()
    val margin = subWidthAssMarginX(subWidthSteps[subWidthLevel])
    return listOf("MarginL=$margin", "MarginR=$margin")
}

// Omitting a style name covers all definitions, including "Main" and "Dialogue".
// Inline tags and positioning remain authored by the subtitle script.
private fun MPVActivity.currentAssStyleOverrides(styleEnabled: Boolean): List<String>? {
    if (subStylePreservingCompanionTrack) return emptyList()
    val base = when {
        styleEnabled && subStyleSelectiveAss -> buildAssStyleOverrides(
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
                outlineOpacity = subStyleExtras.outlineOpacity,
                shadowOpacity = subStyleExtras.shadowOpacity,
            ),
        )
        styleEnabled && subStyleOverrideAss -> buildAssAttributeOverrides(
            bold = subStyleBold,
            italic = subStyleItalic,
        )
        else -> null
    }
    val widthOverrides = assWidthOverrides(styleEnabled)
    return when {
        widthOverrides.isEmpty() -> base
        base != null -> base + widthOverrides
        else -> mergeAssWidthOverrides(subStyleSavedDefaults?.get("sub-ass-style-overrides").orEmpty(), widthOverrides)
    }
}

internal fun mergeAssWidthOverrides(baseline: String, widthOverrides: List<String>): List<String> {
    val kept = baseline.split(',')
        .map(String::trim)
        .filter {
            it.isNotEmpty() &&
                !it.startsWith("MarginL=", ignoreCase = true) &&
                !it.startsWith("MarginR=", ignoreCase = true)
        }
    return kept + widthOverrides
}
