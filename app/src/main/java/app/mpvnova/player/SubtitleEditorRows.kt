package app.mpvnova.player

import app.mpvnova.player.SubtitleStyleDialog.Control as C

internal val SUBTITLE_EDITOR_TOGGLES = setOf(
    C.BOLD, C.ITALIC, C.IMAGE_SUB_GRAYSCALE, C.OVERRIDE_ASS, C.SELECTIVE_ASS, C.FORCE_ALL_ASS,
)

internal fun subtitleEditorControls(tab: SubtitleEditorTab): List<C> = when (tab) {
    SubtitleEditorTab.TEXT -> listOf(
        C.FONT, C.FONT_SIZE, C.TEXT_COLOR, C.TEXT_OPACITY, C.SPACING, C.FONT_HINTING, C.BOLD, C.ITALIC,
    )
    SubtitleEditorTab.EDGES -> listOf(
        C.EDGE, C.OUTLINE_SIZE, C.OUTLINE_COLOR, C.OUTLINE_OPACITY, C.BLUR,
        C.SHADOW_SIZE, C.SHADOW_COLOR, C.SHADOW_OPACITY, C.BG_COLOR, C.BG_OPACITY,
    )
    SubtitleEditorTab.LAYOUT -> listOf(C.SCALE, C.POSITION, C.ALIGNMENT, C.JUSTIFY, C.SIDE_MARGIN, C.LINE_SPACING)
    SubtitleEditorTab.ADVANCED -> listOf(C.OVERRIDE_ASS, C.SELECTIVE_ASS, C.FORCE_ALL_ASS, C.IMAGE_SUB_GRAYSCALE)
}

private val SUBTITLE_EDITOR_LABELS = mapOf(
    C.MASTER to R.string.sub_style_use_custom,
    C.IMAGE_SUB_GRAYSCALE to R.string.sub_style_gray_image_subs,
    C.TEXT_COLOR to R.string.sub_style_text_color,
    C.TEXT_OPACITY to R.string.sub_style_text_opacity,
    C.EDGE to R.string.sub_style_edge,
    C.OUTLINE_COLOR to R.string.sub_style_outline_color,
    C.OUTLINE_SIZE to R.string.sub_style_outline_size,
    C.BLUR to R.string.sub_style_blur,
    C.SHADOW_SIZE to R.string.sub_style_shadow_size,
    C.SHADOW_COLOR to R.string.sub_style_shadow_color,
    C.BG_OPACITY to R.string.sub_style_bg_opacity,
    C.BG_COLOR to R.string.sub_style_bg_color,
    C.FONT to R.string.sub_style_font,
    C.SPACING to R.string.sub_style_spacing,
    C.JUSTIFY to R.string.sub_style_justify,
    C.BOLD to R.string.sub_style_bold,
    C.ITALIC to R.string.sub_style_italic,
    C.OVERRIDE_ASS to R.string.sub_style_override_ass,
    C.SELECTIVE_ASS to R.string.sub_style_selective_ass,
    C.FORCE_ALL_ASS to R.string.sub_style_force_all_ass,
    C.FONT_SIZE to R.string.sub_editor_size,
    C.FONT_HINTING to R.string.sub_editor_hinting,
    C.LINE_SPACING to R.string.sub_editor_line_spacing,
    C.SIDE_MARGIN to R.string.sub_editor_side_margin,
    C.ALIGNMENT to R.string.sub_editor_alignment,
    C.OUTLINE_OPACITY to R.string.sub_editor_outline_opacity,
    C.SHADOW_OPACITY to R.string.sub_editor_shadow_opacity,
    C.SCALE to R.string.btn_sub_scale,
    C.POSITION to R.string.btn_sub_pos,
)

internal fun subtitleEditorLabel(control: C): Int = SUBTITLE_EDITOR_LABELS.getValue(control)

@Suppress("CyclomaticComplexMethod") // Direct routing from the existing dialog state.
internal fun SubtitleStyleDialog.State.editorRow(control: C): SubtitleStyleDialog.Row = when (control) {
    C.TEXT_COLOR -> textColor
    C.TEXT_OPACITY -> textOpacity
    C.EDGE -> edge
    C.OUTLINE_COLOR -> outlineColor
    C.OUTLINE_SIZE -> outlineSize
    C.BLUR -> blur
    C.SHADOW_SIZE -> shadowSize
    C.SHADOW_COLOR -> shadowColor
    C.BG_OPACITY -> bgOpacity
    C.BG_COLOR -> bgColor
    C.FONT -> font
    C.SPACING -> spacing
    C.JUSTIFY -> justify
    else -> extraRows.getValue(control)
}

internal fun SubtitleStyleDialog.State.editorToggle(control: C): Pair<Boolean, Boolean> = when (control) {
    C.BOLD -> boldOn to masterOn
    C.ITALIC -> italicOn to masterOn
    C.IMAGE_SUB_GRAYSCALE -> imageSubtitleGrayOn to true
    C.OVERRIDE_ASS -> overrideOn to overrideEnabled
    C.SELECTIVE_ASS -> selectiveOn to selectiveEnabled
    C.FORCE_ALL_ASS -> forceAllOn to forceAllEnabled
    else -> false to false
}

internal fun MPVActivity.subtitleExtraRows(
    on: Boolean, edges: Boolean, shadow: Boolean,
): Map<C, SubtitleStyleDialog.Row> {
    fun pixels(value: Int?) = value?.let { getString(R.string.sub_editor_pixels, it) }
        ?: getString(R.string.sub_editor_default)
    return mapOf(
        C.FONT_SIZE to SubtitleStyleDialog.Row(pixels(subStyleExtras.fontSize), on),
        C.FONT_HINTING to SubtitleStyleDialog.Row(getString(subStyleExtras.fontHinting.label), on),
        C.LINE_SPACING to SubtitleStyleDialog.Row(pixels(subStyleExtras.lineSpacing), on),
        C.SIDE_MARGIN to SubtitleStyleDialog.Row(pixels(subStyleExtras.sideMargin), on),
        C.ALIGNMENT to SubtitleStyleDialog.Row(getString(when (subStyleExtras.alignment) {
            SubtitleJustify.AUTO -> R.string.sub_editor_default
            SubtitleJustify.LEFT -> R.string.sub_style_justify_left
            SubtitleJustify.CENTER -> R.string.sub_style_justify_center
            SubtitleJustify.RIGHT -> R.string.sub_style_justify_right
        }), on),
        C.OUTLINE_OPACITY to SubtitleStyleDialog.Row(
            "${subStyleExtras.outlineOpacity}%", edges && subStyleEdge != SubtitleEdgeStyle.NONE,
        ),
        C.SHADOW_OPACITY to SubtitleStyleDialog.Row("${subStyleExtras.shadowOpacity}%", shadow),
        C.SCALE to SubtitleStyleDialog.Row(getSubScaleLabel()),
        C.POSITION to SubtitleStyleDialog.Row(getSubPosLabel()),
    )
}
