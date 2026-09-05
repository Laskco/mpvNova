package app.mpvnova.player

internal fun MPVActivity.adjustSubtitleStyle(
    control: SubtitleStyleDialog.Control,
    delta: Int,
): SubtitleStyleDialog.State {
    if (!adjustSubtitleColorControl(control, delta) && !adjustSubtitleToggleControl(control))
        adjustSubtitleValueControl(control, delta)
    applyCustomSubtitleStyle()
    if (control == SubtitleStyleDialog.Control.IMAGE_SUB_GRAYSCALE)
        rebuildSelectedImageSubtitleTracks()
    writeSubtitleStyleSettings()
    return subtitleStyleState()
}

// mpv applies sub-gray while decoding each bitmap subtitle palette. The option does not request a
// decoder rebuild by itself, and sub-reload only reloads external subtitle files. Re-selecting the
// active tracks rebuilds embedded PGS/DVD subtitle decoders without changing the user's choices.
internal fun MPVActivity.rebuildSelectedImageSubtitleTracks() {
    val primarySid = player.sid
    val secondarySid = player.secondarySid
    if (primarySid == -1 && secondarySid == -1)
        return

    if (secondarySid != -1)
        player.secondarySid = -1
    if (primarySid != -1)
        player.sid = -1

    if (primarySid != -1)
        player.sid = primarySid
    if (secondarySid != -1)
        player.secondarySid = secondarySid
}

// Colors and the cycling enums (edge, justify). Returns false for anything else.
private fun MPVActivity.adjustSubtitleColorControl(
    control: SubtitleStyleDialog.Control,
    delta: Int,
): Boolean {
    when (control) {
        SubtitleStyleDialog.Control.TEXT_COLOR ->
            subStyleTextColorIndex = wrapIndex(subStyleTextColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.OUTLINE_COLOR ->
            subStyleBorderColorIndex = wrapIndex(subStyleBorderColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.SHADOW_COLOR ->
            subStyleShadowColorIndex = wrapIndex(subStyleShadowColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.BG_COLOR ->
            subStyleBgColorIndex = wrapIndex(subStyleBgColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.EDGE ->
            subStyleEdge = SubtitleEdgeStyle.entries[
                wrapIndex(subStyleEdge.ordinal, delta, SubtitleEdgeStyle.entries.size)
            ]
        SubtitleStyleDialog.Control.JUSTIFY ->
            subStyleJustify = SubtitleJustify.entries[
                wrapIndex(subStyleJustify.ordinal, delta, SubtitleJustify.entries.size)
            ]
        else -> return false
    }
    return true
}

// Plain on/off toggles, including the mutually-exclusive ASS-override modes. False for the rest.
private fun MPVActivity.adjustSubtitleToggleControl(control: SubtitleStyleDialog.Control): Boolean {
    when (control) {
        SubtitleStyleDialog.Control.MASTER -> customSubStyleEnabled = !customSubStyleEnabled
        SubtitleStyleDialog.Control.IMAGE_SUB_GRAYSCALE -> subStyleGrayImageSubs = !subStyleGrayImageSubs
        SubtitleStyleDialog.Control.BOLD -> subStyleBold = !subStyleBold
        SubtitleStyleDialog.Control.ITALIC -> subStyleItalic = !subStyleItalic
        SubtitleStyleDialog.Control.OVERRIDE_ASS -> setAssOverrideMode(AssOverrideMode.OVERRIDE)
        SubtitleStyleDialog.Control.SELECTIVE_ASS -> setAssOverrideMode(AssOverrideMode.SELECTIVE)
        SubtitleStyleDialog.Control.FORCE_ALL_ASS -> setAssOverrideMode(AssOverrideMode.FORCE_ALL)
        else -> return false
    }
    return true
}

private fun MPVActivity.adjustSubtitleValueControl(
    control: SubtitleStyleDialog.Control,
    delta: Int,
) {
    when (control) {
        SubtitleStyleDialog.Control.TEXT_OPACITY ->
            subStyleTextOpacityIndex = clampIndex(subStyleTextOpacityIndex, delta, SUBTITLE_OPACITY_PERCENT_STEPS.size)
        SubtitleStyleDialog.Control.OUTLINE_SIZE ->
            subStyleBorderSizeIndex = clampIndex(subStyleBorderSizeIndex, delta, SUBTITLE_BORDER_SIZE_STEPS.size)
        SubtitleStyleDialog.Control.BLUR ->
            subStyleBlurIndex = clampIndex(subStyleBlurIndex, delta, SUBTITLE_BLUR_STEPS.size)
        SubtitleStyleDialog.Control.SHADOW_SIZE ->
            subStyleShadowSizeIndex = clampIndex(subStyleShadowSizeIndex, delta, SUBTITLE_SHADOW_SIZE_STEPS.size)
        SubtitleStyleDialog.Control.BG_OPACITY ->
            subStyleBgOpacityIndex = clampIndex(subStyleBgOpacityIndex, delta, SUBTITLE_OPACITY_PERCENT_STEPS.size)
        SubtitleStyleDialog.Control.SPACING ->
            subStyleSpacingIndex = clampIndex(subStyleSpacingIndex, delta, SUBTITLE_SPACING_STEPS.size)
        SubtitleStyleDialog.Control.FONT -> adjustSubtitleFont(delta)
        SubtitleStyleDialog.Control.SCALE -> adjustSubScale(delta)
        SubtitleStyleDialog.Control.POSITION -> adjustSubPos(delta)
        else -> adjustSubtitleExtras(control, delta)
    }
}

private enum class AssOverrideMode { OVERRIDE, SELECTIVE, FORCE_ALL }

// Toggle one ASS-override mode. The three are mutually exclusive, so turning one on clears the
// other two; clicking the active mode turns it off (back to "scale").
private fun MPVActivity.setAssOverrideMode(mode: AssOverrideMode) {
    val turningOn = when (mode) {
        AssOverrideMode.OVERRIDE -> !subStyleOverrideAss
        AssOverrideMode.SELECTIVE -> !subStyleSelectiveAss
        AssOverrideMode.FORCE_ALL -> !subStyleForceAllAss
    }
    subStyleOverrideAss = turningOn && mode == AssOverrideMode.OVERRIDE
    subStyleSelectiveAss = turningOn && mode == AssOverrideMode.SELECTIVE
    subStyleForceAllAss = turningOn && mode == AssOverrideMode.FORCE_ALL
}

private fun MPVActivity.adjustSubtitleFont(delta: Int) {
    val choices = subtitleFontChoices()
    val current = choices.indexOfFirst { it.family == subStyleFontFamily }.coerceAtLeast(0)
    subStyleFontFamily = choices[wrapIndex(current, delta, choices.size)].family
}

private fun wrapIndex(current: Int, delta: Int, size: Int): Int = ((current + delta) % size + size) % size

private fun clampIndex(current: Int, delta: Int, size: Int): Int = (current + delta).coerceIn(0, size - 1)
