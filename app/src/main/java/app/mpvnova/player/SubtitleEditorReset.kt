package app.mpvnova.player

internal fun MPVActivity.resetSubtitleEditorStyle() {
    val hadGray = subStyleGrayImageSubs
    subStyleTextColorIndex = subtitleColorOptionIndex(SUBTITLE_TEXT_COLOR_DEFAULT_ID)
    subStyleTextOpacityIndex = nearestOpacityIndex(DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT)
    subStyleBorderColorIndex = subtitleColorOptionIndex(SUBTITLE_BORDER_COLOR_DEFAULT_ID)
    subStyleBorderSizeIndex = DEFAULT_SUBTITLE_BORDER_INDEX
    subStyleBlurIndex = DEFAULT_SUBTITLE_BLUR_INDEX
    subStyleShadowSizeIndex = DEFAULT_SUBTITLE_SHADOW_SIZE_INDEX
    subStyleShadowColorIndex = subtitleColorOptionIndex(SUBTITLE_SHADOW_COLOR_DEFAULT_ID)
    subStyleSpacingIndex = DEFAULT_SUBTITLE_SPACING_INDEX
    subStyleJustify = DEFAULT_SUBTITLE_JUSTIFY
    subStyleBgColorIndex = subtitleColorOptionIndex(SUBTITLE_BG_COLOR_DEFAULT_ID)
    subStyleBgOpacityIndex = nearestOpacityIndex(DEFAULT_SUBTITLE_BG_OPACITY_PERCENT)
    subStyleEdge = DEFAULT_SUBTITLE_EDGE_STYLE
    subStyleFontFamily = SUBTITLE_FONT_DEFAULT_FAMILY
    subStyleBold = false
    subStyleItalic = false
    subStyleOverrideAss = false
    subStyleSelectiveAss = false
    subStyleForceAllAss = false
    subStyleGrayImageSubs = false
    subStyleExtras = SubtitleStyleExtras()
    editingSubtitleStylePreset = null
    applyCustomSubtitleStyle()
    if (hadGray) rebuildSelectedImageSubtitleTracks()
    writeSubtitleStyleSettings()
}
