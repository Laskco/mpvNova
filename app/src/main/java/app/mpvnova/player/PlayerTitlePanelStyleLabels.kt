package app.mpvnova.player

internal fun MPVActivity.playerTitlePanelControlValue(
    control: PlayerTitleStyleControl,
    style: PlayerTitlePanelStyle,
): String = when (control) {
    PlayerTitleStyleControl.PANEL_SURFACE -> panelSurfaceLabel(style.surface)
    PlayerTitleStyleControl.PANEL_OPACITY -> getString(
        R.string.player_ui_value_percent,
        style.opacityPercent,
    )
    PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH -> getString(
        R.string.player_ui_value_percent,
        style.accentStrengthPercent,
    )
    PlayerTitleStyleControl.PANEL_GRADIENT -> onOffLabel(style.gradientEnabled)
    PlayerTitleStyleControl.PANEL_OUTLINE -> onOffLabel(style.outlineEnabled)
    PlayerTitleStyleControl.PANEL_ALIGNMENT -> panelAlignmentLabel(style.alignment)
    PlayerTitleStyleControl.PANEL_CONTENT_ALIGNMENT -> panelAlignmentLabel(style.contentAlignment)
    PlayerTitleStyleControl.PANEL_WIDTH -> if (
        style.widthPercent == PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT
    ) {
        getString(R.string.player_title_style_panel_width_auto)
    } else {
        getString(R.string.player_ui_value_percent, style.widthPercent)
    }
    else -> panelDimensionLabel(control, style)
}

private fun MPVActivity.panelSurfaceLabel(surface: PlayerPanelSurface): String = getString(
    when (surface) {
        PlayerPanelSurface.GLASS -> R.string.player_ui_surface_glass
        PlayerPanelSurface.FLAT -> R.string.player_ui_surface_flat
        PlayerPanelSurface.TRANSPARENT -> R.string.player_ui_surface_transparent
    },
)

private fun MPVActivity.panelAlignmentLabel(alignment: PlayerTitlePanelAlignment): String =
    getString(
        when (alignment) {
            PlayerTitlePanelAlignment.START -> R.string.player_ui_alignment_start
            PlayerTitlePanelAlignment.CENTER -> R.string.player_ui_alignment_center
            PlayerTitlePanelAlignment.END -> R.string.player_ui_alignment_end
        },
    )

private fun MPVActivity.panelDimensionLabel(
    control: PlayerTitleStyleControl,
    style: PlayerTitlePanelStyle,
): String {
    val value = when (control) {
        PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH -> style.outlineWidthDp
        PlayerTitleStyleControl.PANEL_CORNER_RADIUS -> style.cornerRadiusDp
        PlayerTitleStyleControl.PANEL_ELEVATION -> style.elevationDp
        PlayerTitleStyleControl.PANEL_HORIZONTAL_PADDING -> style.horizontalPaddingDp
        PlayerTitleStyleControl.PANEL_VERTICAL_PADDING -> style.verticalPaddingDp
        PlayerTitleStyleControl.PANEL_CONTENT_SPACING -> style.contentSpacingDp
        PlayerTitleStyleControl.PANEL_VERTICAL_OFFSET -> style.verticalOffsetDp
        else -> return ""
    }
    return getString(R.string.player_ui_value_dp, value)
}

private fun MPVActivity.onOffLabel(enabled: Boolean): String = getString(
    if (enabled) R.string.player_ui_value_on else R.string.player_ui_value_off,
)
