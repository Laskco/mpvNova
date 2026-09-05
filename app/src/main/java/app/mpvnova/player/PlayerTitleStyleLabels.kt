package app.mpvnova.player

import java.util.Locale

internal fun MPVActivity.playerTitlePartLabel(part: PlayerTitlePart): String = getString(
    when (part) {
        PlayerTitlePart.SEASON -> R.string.player_title_style_season
        PlayerTitlePart.EPISODE_NUMBER -> R.string.player_title_style_episode_number
        PlayerTitlePart.TITLE -> R.string.player_title_style_show_title
        PlayerTitlePart.EPISODE_TITLE -> R.string.player_title_style_episode_title
        PlayerTitlePart.DATE -> R.string.player_title_style_date
        PlayerTitlePart.CLOCK -> R.string.player_title_style_clock
        PlayerTitlePart.ENDS_AT -> R.string.player_title_style_ends_at
    },
)

internal fun <T> cyclePlayerTitleValue(values: List<T>, current: T, delta: Int): T {
    if (values.isEmpty()) return current
    val currentIndex = values.indexOf(current).takeIf { it >= 0 } ?: 0
    return values[Math.floorMod(currentIndex + delta, values.size)]
}

internal fun MPVActivity.playerTitleControlValue(
    control: PlayerTitleStyleControl,
    style: PlayerTitleTextStyle,
    part: PlayerTitlePart,
): String = when (control) {
    PlayerTitleStyleControl.FONT -> playerTitleFontLabel(style.font)
    PlayerTitleStyleControl.SIZE -> getString(R.string.player_title_style_value_size, style.sizeSp)
    PlayerTitleStyleControl.WEIGHT -> playerTitleWeightLabel(style.weight)
    PlayerTitleStyleControl.LETTER_SPACING -> getString(
        R.string.player_title_style_value_spacing,
        normalizedPlayerTitleSpacing(style.letterSpacing),
    )
    PlayerTitleStyleControl.COLOR,
    PlayerTitleStyleControl.OPACITY,
    PlayerTitleStyleControl.SHADOW,
    PlayerTitleStyleControl.SHADOW_STRENGTH,
    PlayerTitleStyleControl.OUTLINE_THICKNESS,
    PlayerTitleStyleControl.OUTLINE_COLOR,
    PlayerTitleStyleControl.BACKGROUND_PLATE,
    PlayerTitleStyleControl.BACKGROUND_STRENGTH,
    -> playerTitleEffectControlValue(control, style)
    PlayerTitleStyleControl.ITALIC -> getString(
        if (style.italic) R.string.player_title_style_value_on
        else R.string.player_title_style_value_off,
    )
    PlayerTitleStyleControl.TEXT_CASE -> getString(playerTitleTextCaseLabelRes(style.textCase))
    PlayerTitleStyleControl.POSITION -> playerTitlePositionLabel(part)
    PlayerTitleStyleControl.LONG_TEXT_MODE,
    PlayerTitleStyleControl.MAX_LINES,
    PlayerTitleStyleControl.WRAPPED_LINE_SPACING,
    PlayerTitleStyleControl.TEXT_OFFSET_X,
    PlayerTitleStyleControl.TEXT_OFFSET_Y,
    PlayerTitleStyleControl.METADATA_FORMAT,
    PlayerTitleStyleControl.COMBINED_PANELS -> playerTitleTextLayoutValue(control, style)
    PlayerTitleStyleControl.PANEL_SURFACE,
    PlayerTitleStyleControl.PANEL_OPACITY,
    PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH,
    PlayerTitleStyleControl.PANEL_GRADIENT,
    PlayerTitleStyleControl.PANEL_OUTLINE,
    PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH,
    PlayerTitleStyleControl.PANEL_CORNER_RADIUS,
    PlayerTitleStyleControl.PANEL_ELEVATION,
    PlayerTitleStyleControl.PANEL_HORIZONTAL_PADDING,
    PlayerTitleStyleControl.PANEL_VERTICAL_PADDING,
    PlayerTitleStyleControl.PANEL_CONTENT_SPACING,
    PlayerTitleStyleControl.PANEL_ALIGNMENT,
    PlayerTitleStyleControl.PANEL_CONTENT_ALIGNMENT,
    PlayerTitleStyleControl.PANEL_WIDTH,
    PlayerTitleStyleControl.PANEL_VERTICAL_OFFSET,
    -> playerTitlePanelControlValue(control, playerTitleStyle.panelStyleFor(part))
}

private fun MPVActivity.playerTitlePositionLabel(part: PlayerTitlePart): String = getString(
    when (playerTitleStyle.positionOf(part)) {
        0 -> R.string.player_title_style_position_top
        1 -> R.string.player_title_style_position_middle
        else -> R.string.player_title_style_position_bottom
    },
)

internal fun MPVActivity.playerTitleFontValues(): List<String> = buildList {
    add(PlayerTitleStyle.INHERIT_FONT)
    addAll(UiFont.choices.map(UiFont.Choice::value))
}

private fun MPVActivity.playerTitleFontLabel(value: String): String {
    if (value == PlayerTitleStyle.INHERIT_FONT)
        return getString(R.string.player_title_style_inherit_font)
    val choice = UiFont.choices.firstOrNull { it.value == value }
    return choice?.let { getString(it.titleRes) }
        ?: getString(R.string.player_title_style_inherit_font)
}

private fun MPVActivity.playerTitleWeightLabel(weight: PlayerTitleWeight): String = getString(
    when (weight) {
        PlayerTitleWeight.LIGHT -> R.string.player_title_style_value_light
        PlayerTitleWeight.REGULAR -> R.string.player_title_style_value_regular
        PlayerTitleWeight.MEDIUM -> R.string.player_title_style_value_medium
        PlayerTitleWeight.SEMIBOLD -> R.string.player_title_style_value_semibold
        PlayerTitleWeight.BOLD -> R.string.player_title_style_value_bold
        PlayerTitleWeight.EXTRABOLD -> R.string.player_title_style_value_extrabold
        PlayerTitleWeight.BLACK -> R.string.player_title_style_value_black
    },
)

internal fun MPVActivity.playerTitleColorLabel(color: PlayerTitleColor): String {
    val appearanceValue = color.appearanceValue
        ?: return getString(R.string.player_title_style_value_app_color)
    val choice = appearanceColorChoices.firstOrNull { it.value == appearanceValue }
    return choice?.let { getString(it.labelRes) }
        ?: getString(R.string.appearance_theme_white)
}

internal fun MPVActivity.playerTitleShadowLabel(shadow: PlayerTitleShadow): String = getString(
    when (shadow) {
        PlayerTitleShadow.OFF -> R.string.player_title_style_value_off
        PlayerTitleShadow.SUBTLE -> R.string.player_title_style_value_subtle
        PlayerTitleShadow.SOFT -> R.string.player_title_style_value_soft
        PlayerTitleShadow.STRONG -> R.string.player_title_style_value_strong
        PlayerTitleShadow.HEAVY -> R.string.player_title_style_value_heavy
    },
)

internal fun MPVActivity.playerTitleSeparatorLabel(separator: PlayerTitleSeparator): String =
    getString(
        when (separator) {
            PlayerTitleSeparator.DOT -> R.string.player_title_style_separator_dot
            PlayerTitleSeparator.DASH -> R.string.player_title_style_separator_dash
            PlayerTitleSeparator.BAR -> R.string.player_title_style_separator_bar
            PlayerTitleSeparator.SLASH -> R.string.player_title_style_separator_slash
            PlayerTitleSeparator.NONE -> R.string.player_title_style_separator_none
        },
    )

private fun normalizedPlayerTitleSpacing(value: Float): Float =
    String.format(Locale.US, "%.2f", value).toFloat()
