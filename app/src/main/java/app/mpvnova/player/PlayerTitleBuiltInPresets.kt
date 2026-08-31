@file:Suppress("MagicNumber", "MatchingDeclarationName")

package app.mpvnova.player

internal enum class PlayerTitleBuiltInPreset {
    DEFAULT,
    CINEMA,
    MINIMAL,
    BROADCAST,
    NEON,
    ACCESSIBLE,
}

internal fun playerTitleBuiltInStyle(preset: PlayerTitleBuiltInPreset): PlayerTitleStyle = when (preset) {
    PlayerTitleBuiltInPreset.DEFAULT -> PlayerTitleStyle.DEFAULT
    PlayerTitleBuiltInPreset.CINEMA -> cinemaTitleStyle()
    PlayerTitleBuiltInPreset.MINIMAL -> minimalTitleStyle()
    PlayerTitleBuiltInPreset.BROADCAST -> broadcastTitleStyle()
    PlayerTitleBuiltInPreset.NEON -> neonTitleStyle()
    PlayerTitleBuiltInPreset.ACCESSIBLE -> accessibleTitleStyle()
}

private fun cinemaTitleStyle(): PlayerTitleStyle {
    val defaults = PlayerTitleStyle.DEFAULT
    return defaults.copy(
        season = defaults.season.readable().copy(
            color = PlayerTitleColor.CHROME,
            letterSpacing = 0.08f,
        ),
        episodeNumber = defaults.episodeNumber.readable().copy(
            color = PlayerTitleColor.CHROME,
            letterSpacing = 0.08f,
        ),
        title = defaults.title.readable().copy(sizeSp = 21f, weight = PlayerTitleWeight.BOLD),
        episodeTitle = defaults.episodeTitle.copy(
            sizeSp = 13f,
            color = PlayerTitleColor.CHROME,
            weight = PlayerTitleWeight.REGULAR,
            shadow = PlayerTitleShadow.SOFT,
            outlineWidthDp = 0.5f,
        ),
        date = defaults.date.copy(visible = false),
        clock = defaults.clock.readable().copy(sizeSp = 17f, weight = PlayerTitleWeight.BOLD),
        endsAt = defaults.endsAt.copy(visible = false),
        titlePanel = defaults.titlePanel.copy(
            opacityPercent = 86,
            accentStrengthPercent = 4,
            outlineEnabled = false,
            elevationDp = 4,
            horizontalPaddingDp = 22,
            contentSpacingDp = 6,
            widthPercent = PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT,
            verticalOffsetDp = 24,
        ),
        clockPanel = defaults.clockPanel.copy(
            opacityPercent = 86,
            outlineEnabled = false,
            alignment = PlayerTitlePanelAlignment.END,
            contentAlignment = PlayerTitlePanelAlignment.CENTER,
            widthPercent = PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT,
            elevationDp = 4,
            verticalOffsetDp = 24,
        ),
    ).useFont("lato")
}

private fun minimalTitleStyle(): PlayerTitleStyle {
    val defaults = PlayerTitleStyle.DEFAULT
    val compact = defaults.titlePanel.copy(
        surface = PlayerPanelSurface.FLAT,
        opacityPercent = 78,
        accentStrengthPercent = 0,
        gradientEnabled = false,
        outlineEnabled = false,
        cornerRadiusDp = 8,
        elevationDp = 2,
        horizontalPaddingDp = 12,
        verticalPaddingDp = 7,
        contentSpacingDp = 2,
        widthPercent = PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT,
    )
    return defaults.copy(
        season = defaults.season.copy(visible = false),
        episodeNumber = defaults.episodeNumber.copy(visible = false),
        title = defaults.title.readable().copy(sizeSp = 18f, weight = PlayerTitleWeight.SEMIBOLD),
        episodeTitle = defaults.episodeTitle.copy(visible = false),
        date = defaults.date.copy(visible = false),
        clock = defaults.clock.readable().copy(sizeSp = 15f, weight = PlayerTitleWeight.BOLD),
        endsAt = defaults.endsAt.copy(visible = false),
        titlePanel = compact.copy(
            alignment = PlayerTitlePanelAlignment.START,
            contentAlignment = PlayerTitlePanelAlignment.START,
        ),
        clockPanel = compact.copy(
            alignment = PlayerTitlePanelAlignment.END,
            contentAlignment = PlayerTitlePanelAlignment.END,
        ),
    ).useFont("roboto_condensed")
}

private fun broadcastTitleStyle(): PlayerTitleStyle {
    val defaults = PlayerTitleStyle.DEFAULT
    val panel = defaults.titlePanel.copy(
        surface = PlayerPanelSurface.FLAT,
        opacityPercent = 90,
        accentStrengthPercent = 10,
        gradientEnabled = false,
        cornerRadiusDp = 8,
        elevationDp = 2,
        horizontalPaddingDp = 18,
        verticalPaddingDp = 10,
        contentAlignment = PlayerTitlePanelAlignment.START,
        widthPercent = PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT,
    )
    return defaults.copy(
        season = defaults.season.readable().copy(textCase = PlayerTitleTextCase.UPPERCASE),
        episodeNumber = defaults.episodeNumber.readable().copy(
            textCase = PlayerTitleTextCase.UPPERCASE,
        ),
        title = defaults.title.readable().copy(
            sizeSp = 20f,
            weight = PlayerTitleWeight.EXTRABOLD,
            textCase = PlayerTitleTextCase.UPPERCASE,
        ),
        episodeTitle = defaults.episodeTitle.readable().copy(weight = PlayerTitleWeight.MEDIUM),
        date = defaults.date.readable(),
        clock = defaults.clock.readable().copy(weight = PlayerTitleWeight.EXTRABOLD),
        endsAt = defaults.endsAt.readable(),
        titlePanel = panel.copy(alignment = PlayerTitlePanelAlignment.START),
        clockPanel = panel.copy(
            alignment = PlayerTitlePanelAlignment.END,
            contentAlignment = PlayerTitlePanelAlignment.END,
            horizontalPaddingDp = 16,
        ),
    ).useFont("barlow")
}

private fun neonTitleStyle(): PlayerTitleStyle {
    val defaults = PlayerTitleStyle.DEFAULT
    val primaryText = defaults.title.readable().copy(
        shadow = PlayerTitleShadow.STRONG,
        shadowStrengthPercent = 85,
        outlineWidthDp = 1f,
    )
    val panel = defaults.titlePanel.copy(
        surface = PlayerPanelSurface.GLASS,
        opacityPercent = 82,
        accentStrengthPercent = 24,
        gradientEnabled = true,
        outlineEnabled = true,
        outlineWidthDp = 2,
        cornerRadiusDp = 16,
        elevationDp = 6,
        widthPercent = PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT,
    )
    return defaults.copy(
        season = defaults.season.readable().copy(color = PlayerTitleColor.APP_COLOR),
        episodeNumber = defaults.episodeNumber.readable().copy(color = PlayerTitleColor.APP_COLOR),
        title = primaryText.copy(sizeSp = 21f, weight = PlayerTitleWeight.BOLD),
        episodeTitle = defaults.episodeTitle.readable().copy(
            sizeSp = 13f,
            color = PlayerTitleColor.CHROME,
        ),
        date = defaults.date.readable().copy(color = PlayerTitleColor.CHROME),
        clock = primaryText.copy(sizeSp = 19f, weight = PlayerTitleWeight.BLACK),
        endsAt = defaults.endsAt.readable().copy(color = PlayerTitleColor.CHROME),
        titlePanel = panel.copy(
            alignment = PlayerTitlePanelAlignment.END,
            contentAlignment = PlayerTitlePanelAlignment.END,
        ),
        clockPanel = panel.copy(
            alignment = PlayerTitlePanelAlignment.START,
            contentAlignment = PlayerTitlePanelAlignment.START,
        ),
    ).useFont("rubik")
}

private fun accessibleTitleStyle(): PlayerTitleStyle {
    val defaults = PlayerTitleStyle.DEFAULT
    fun PlayerTitleTextStyle.accessible(size: Float) = copy(
        sizeSp = size,
        weight = PlayerTitleWeight.BOLD,
        color = PlayerTitleColor.WHITE,
        shadow = PlayerTitleShadow.SUBTLE,
        outlineWidthDp = 1f,
        outlineColor = PlayerTitleColor.APP_COLOR,
        backgroundEnabled = false,
    )
    val panel = defaults.titlePanel.copy(
        surface = PlayerPanelSurface.FLAT,
        opacityPercent = 100,
        accentStrengthPercent = 8,
        gradientEnabled = false,
        outlineEnabled = true,
        outlineWidthDp = 2,
        cornerRadiusDp = 12,
        elevationDp = 8,
        horizontalPaddingDp = 24,
        verticalPaddingDp = 14,
        contentSpacingDp = 6,
        widthPercent = PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT,
    )
    return defaults.copy(
        season = defaults.season.accessible(16f),
        episodeNumber = defaults.episodeNumber.accessible(16f),
        title = defaults.title.accessible(24f),
        episodeTitle = defaults.episodeTitle.accessible(16f),
        date = defaults.date.accessible(14f),
        clock = defaults.clock.accessible(22f).copy(weight = PlayerTitleWeight.BLACK),
        endsAt = defaults.endsAt.accessible(14f),
        titlePanel = panel,
        clockPanel = panel.copy(
            alignment = PlayerTitlePanelAlignment.START,
            horizontalPaddingDp = 18,
        ),
    ).useFont("atkinson_hyperlegible")
}

private fun PlayerTitleStyle.useFont(value: String): PlayerTitleStyle = copy(
    season = season.copy(font = value),
    episodeNumber = episodeNumber.copy(font = value),
    title = title.copy(font = value),
    episodeTitle = episodeTitle.copy(font = value),
    date = date.copy(font = value),
    clock = clock.copy(font = value),
    endsAt = endsAt.copy(font = value),
)

private fun PlayerTitleTextStyle.readable(): PlayerTitleTextStyle = copy(
    color = PlayerTitleColor.WHITE,
    opacityPercent = 100,
    shadow = PlayerTitleShadow.SOFT,
    shadowStrengthPercent = 80,
    outlineWidthDp = 0.5f,
    outlineColor = PlayerTitleColor.APP_COLOR,
    backgroundEnabled = false,
)
