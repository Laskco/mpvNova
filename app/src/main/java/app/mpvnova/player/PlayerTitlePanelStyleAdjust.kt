package app.mpvnova.player

internal fun adjustPlayerTitlePanelStyle(
    style: PlayerTitlePanelStyle,
    control: PlayerTitleStyleControl,
    delta: Int,
): PlayerTitlePanelStyle {
    val adjusted = when (control) {
        PlayerTitleStyleControl.PANEL_SURFACE,
        PlayerTitleStyleControl.PANEL_OPACITY,
        PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH,
        PlayerTitleStyleControl.PANEL_GRADIENT,
        PlayerTitleStyleControl.PANEL_OUTLINE,
        PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH,
        PlayerTitleStyleControl.PANEL_CORNER_RADIUS,
        PlayerTitleStyleControl.PANEL_ELEVATION,
        -> adjustPanelAppearance(style, control, delta)
        else -> adjustPanelLayout(style, control, delta)
    }
    return adjusted.normalized()
}

private fun adjustPanelAppearance(
    style: PlayerTitlePanelStyle,
    control: PlayerTitleStyleControl,
    delta: Int,
): PlayerTitlePanelStyle = when (control) {
    PlayerTitleStyleControl.PANEL_SURFACE -> style.copy(
        surface = cyclePlayerTitleValue(PlayerPanelSurface.entries, style.surface, delta),
    )
    PlayerTitleStyleControl.PANEL_OPACITY -> style.copy(
        opacityPercent = stepPlayerUiValue(
            style.opacityPercent,
            delta,
            PLAYER_TITLE_PANEL_OPACITY_STEP_PERCENT,
        ),
    )
    PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH -> style.copy(
        accentStrengthPercent = stepPlayerUiValue(
            style.accentStrengthPercent,
            delta,
            PLAYER_TITLE_PANEL_OPACITY_STEP_PERCENT,
        ),
    )
    PlayerTitleStyleControl.PANEL_GRADIENT -> style.copy(gradientEnabled = !style.gradientEnabled)
    PlayerTitleStyleControl.PANEL_OUTLINE -> style.copy(outlineEnabled = !style.outlineEnabled)
    PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH -> style.copy(
        outlineWidthDp = style.outlineWidthDp + delta,
    )
    PlayerTitleStyleControl.PANEL_CORNER_RADIUS -> style.copy(
        cornerRadiusDp = stepPlayerUiValue(style.cornerRadiusDp, delta, PANEL_RADIUS_STEP_DP),
    )
    PlayerTitleStyleControl.PANEL_ELEVATION -> style.copy(
        elevationDp = stepPlayerUiValue(style.elevationDp, delta, PANEL_ELEVATION_STEP_DP),
    )
    else -> style
}

private fun adjustPanelLayout(
    style: PlayerTitlePanelStyle,
    control: PlayerTitleStyleControl,
    delta: Int,
): PlayerTitlePanelStyle = when (control) {
    PlayerTitleStyleControl.PANEL_HORIZONTAL_PADDING -> style.copy(
        horizontalPaddingDp = stepPlayerUiValue(style.horizontalPaddingDp, delta, PANEL_PADDING_STEP_DP),
    )
    PlayerTitleStyleControl.PANEL_VERTICAL_PADDING -> style.copy(
        verticalPaddingDp = stepPlayerUiValue(style.verticalPaddingDp, delta, PANEL_PADDING_STEP_DP),
    )
    PlayerTitleStyleControl.PANEL_CONTENT_SPACING -> style.copy(
        contentSpacingDp = stepPlayerUiValue(style.contentSpacingDp, delta, PANEL_CONTENT_SPACING_STEP_DP),
    )
    PlayerTitleStyleControl.PANEL_ALIGNMENT -> style.copy(
        alignment = cyclePlayerTitleValue(PlayerTitlePanelAlignment.entries, style.alignment, delta),
        manualPosition = false,
    )
    PlayerTitleStyleControl.PANEL_CONTENT_ALIGNMENT -> style.copy(
        contentAlignment = cyclePlayerTitleValue(
            PlayerTitlePanelAlignment.entries,
            style.contentAlignment,
            delta,
        ),
    )
    PlayerTitleStyleControl.PANEL_WIDTH -> style.copy(
        widthPercent = stepPanelWidth(style.widthPercent, delta),
    )
    PlayerTitleStyleControl.PANEL_VERTICAL_OFFSET -> style.copy(
        verticalOffsetDp = stepPlayerUiValue(style.verticalOffsetDp, delta, PANEL_VERTICAL_OFFSET_STEP_DP),
    )
    else -> style
}

private fun stepPanelWidth(current: Int, delta: Int): Int = when {
    delta > 0 && current == PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT ->
        PLAYER_TITLE_MIN_PANEL_WIDTH_PERCENT
    delta < 0 && current <= PLAYER_TITLE_MIN_PANEL_WIDTH_PERCENT ->
        PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT
    else -> stepPlayerUiValue(current, delta, PANEL_WIDTH_STEP_PERCENT)
        .coerceIn(PLAYER_TITLE_MIN_PANEL_WIDTH_PERCENT, PLAYER_TITLE_MAX_PANEL_WIDTH_PERCENT)
}

private const val PANEL_RADIUS_STEP_DP = 2
private const val PANEL_ELEVATION_STEP_DP = 2
private const val PANEL_PADDING_STEP_DP = 2
private const val PANEL_CONTENT_SPACING_STEP_DP = 2
private const val PANEL_VERTICAL_OFFSET_STEP_DP = 4
private const val PANEL_WIDTH_STEP_PERCENT = 5
