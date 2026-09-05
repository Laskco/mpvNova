package app.mpvnova.player

private const val ASS_BORDER_STYLE_OUTLINE = 1
private const val ASS_BORDER_STYLE_OPAQUE_BOX = 3
private const val FULL_OPACITY_PERCENT = 100
private const val TRANSPARENT_RGB = 0x000000

internal data class AssStyleOverrideSpec(
    val fontFamily: String,
    val textRgb: Int,
    val textOpacity: Int,
    val borderRgb: Int,
    val borderSize: Double,
    val backgroundRgb: Int,
    val backgroundOpacity: Int,
    val shadowRgb: Int,
    val shadowSize: Double,
    val edge: SubtitleEdgeStyle,
    val bold: Boolean,
    val italic: Boolean,
    val spacing: Double,
    val blur: Double,
    val outlineOpacity: Int = FULL_OPACITY_PERCENT,
    val shadowOpacity: Int = FULL_OPACITY_PERCENT,
)

internal fun buildAssStyleOverrides(spec: AssStyleOverrideSpec): List<String> {
    val backgroundEnabled = spec.backgroundOpacity > 0
    val outlineSize = if (spec.edge == SubtitleEdgeStyle.NONE && !backgroundEnabled) {
        0.0
    } else {
        spec.borderSize
    }
    val shadowSize = if (!backgroundEnabled && spec.edge == SubtitleEdgeStyle.DROP_SHADOW) {
        spec.shadowSize
    } else {
        0.0
    }
    val backColor = when {
        backgroundEnabled -> assSubtitleColor(spec.backgroundRgb, spec.backgroundOpacity)
        shadowSize > 0.0 -> assSubtitleColor(spec.shadowRgb, spec.shadowOpacity)
        else -> assSubtitleColor(TRANSPARENT_RGB, 0)
    }

    return listOf(
        "FontName=${spec.fontFamily}",
        "PrimaryColour=${assSubtitleColor(spec.textRgb, spec.textOpacity)}",
        "OutlineColour=${assSubtitleColor(spec.borderRgb, spec.outlineOpacity)}",
        "BackColour=$backColor",
        "Bold=${if (spec.bold) -1 else 0}",
        "Italic=${if (spec.italic) -1 else 0}",
        "Spacing=${spec.spacing}",
        "BorderStyle=${if (backgroundEnabled) ASS_BORDER_STYLE_OPAQUE_BOX else ASS_BORDER_STYLE_OUTLINE}",
        "Outline=$outlineSize",
        "Shadow=$shadowSize",
        "Blur=${spec.blur}",
    )
}

internal fun buildAssAttributeOverrides(bold: Boolean, italic: Boolean): List<String> = listOf(
    "Bold=${if (bold) -1 else 0}",
    "Italic=${if (italic) -1 else 0}",
)
