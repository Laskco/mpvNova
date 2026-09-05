package app.mpvnova.player

internal fun PlayerUiCustomization.withSurfaceFrom(source: PlayerTitlePanelStyle): PlayerUiCustomization = copy(
    surface = source.surface,
    panelOpacityPercent = source.opacityPercent,
    gradientEnabled = source.gradientEnabled,
    panelOutlineEnabled = source.outlineEnabled,
    panelOutlineWidthDp = source.outlineWidthDp,
    cornerRadiusDp = source.cornerRadiusDp,
    panelElevationDp = source.elevationDp,
).normalized()

internal fun PlayerTitlePanelStyle.withSurfaceFrom(source: PlayerUiCustomization): PlayerTitlePanelStyle = copy(
    surface = source.surface,
    opacityPercent = source.panelOpacityPercent,
    gradientEnabled = source.gradientEnabled,
    outlineEnabled = source.panelOutlineEnabled,
    outlineWidthDp = source.panelOutlineWidthDp,
    cornerRadiusDp = source.cornerRadiusDp,
    elevationDp = source.panelElevationDp,
).normalized()

private fun PlayerTitlePanelStyle.withAppearanceFrom(source: PlayerTitlePanelStyle): PlayerTitlePanelStyle = copy(
    surface = source.surface,
    opacityPercent = source.opacityPercent,
    accentStrengthPercent = source.accentStrengthPercent,
    gradientEnabled = source.gradientEnabled,
    outlineEnabled = source.outlineEnabled,
    outlineWidthDp = source.outlineWidthDp,
    cornerRadiusDp = source.cornerRadiusDp,
    elevationDp = source.elevationDp,
)

private fun PlayerTitleTextStyle.withAppearanceFrom(source: PlayerTitleTextStyle): PlayerTitleTextStyle = copy(
    font = source.font,
    sizeSp = source.sizeSp,
    weight = source.weight,
    letterSpacing = source.letterSpacing,
    color = source.color,
    shadow = source.shadow,
    italic = source.italic,
    opacityPercent = source.opacityPercent,
    textCase = source.textCase,
    outlineWidthDp = source.outlineWidthDp,
    outlineColor = source.outlineColor,
    shadowStrengthPercent = source.shadowStrengthPercent,
    backgroundEnabled = source.backgroundEnabled,
    backgroundStrengthPercent = source.backgroundStrengthPercent,
)

// Copy only appearance: visibility, positions, widths, wrapping and order stay put.
internal fun PlayerTitleStyle.copyOtherPanelAppearance(toTitle: Boolean): PlayerTitleStyle = if (toTitle) {
    copy(
        titlePanel = titlePanel.withAppearanceFrom(clockPanel),
        season = season.withAppearanceFrom(date),
        episodeNumber = episodeNumber.withAppearanceFrom(date),
        title = title.withAppearanceFrom(clock),
        episodeTitle = episodeTitle.withAppearanceFrom(endsAt),
    ).normalized()
} else {
    copy(
        clockPanel = clockPanel.withAppearanceFrom(titlePanel),
        date = date.withAppearanceFrom(season),
        clock = clock.withAppearanceFrom(title),
        endsAt = endsAt.withAppearanceFrom(episodeTitle),
    ).normalized()
}
