package app.mpvnova.player

internal fun adjustPlayerTitleTextLayout(
    style: PlayerTitleTextStyle,
    control: PlayerTitleStyleControl,
    delta: Int,
): PlayerTitleTextStyle = when (control) {
    PlayerTitleStyleControl.LONG_TEXT_MODE -> style.copy(
        longTextMode = cyclePlayerTitleValue(PlayerTitleLongTextMode.entries, style.longTextMode, delta),
    )
    PlayerTitleStyleControl.MAX_LINES -> style.copy(
        maxLines = (style.maxLines + delta).coerceIn(0, PLAYER_TITLE_MAX_LINES),
    )
    PlayerTitleStyleControl.WRAPPED_LINE_SPACING -> style.copy(
        wrappedLineSpacingDp = (style.wrappedLineSpacingDp + delta)
            .coerceIn(0, PLAYER_TITLE_MAX_WRAPPED_LINE_SPACING_DP),
    )
    PlayerTitleStyleControl.TEXT_OFFSET_X -> style.copy(
        horizontalOffsetDp = (style.horizontalOffsetDp + delta)
            .coerceIn(-PLAYER_TITLE_MAX_TEXT_OFFSET_DP, PLAYER_TITLE_MAX_TEXT_OFFSET_DP),
    )
    PlayerTitleStyleControl.TEXT_OFFSET_Y -> style.copy(
        verticalOffsetDp = (style.verticalOffsetDp + delta)
            .coerceIn(-PLAYER_TITLE_MAX_TEXT_OFFSET_DP, PLAYER_TITLE_MAX_TEXT_OFFSET_DP),
    )
    else -> style
}

internal fun MPVActivity.playerTitleTextLayoutValue(
    control: PlayerTitleStyleControl,
    style: PlayerTitleTextStyle,
): String = when (control) {
    PlayerTitleStyleControl.LONG_TEXT_MODE -> getString(style.longTextMode.labelRes())
    PlayerTitleStyleControl.MAX_LINES -> if (style.maxLines == 0) {
        getString(R.string.player_title_extra_default)
    } else style.maxLines.toString()
    PlayerTitleStyleControl.WRAPPED_LINE_SPACING ->
        getString(R.string.player_title_extra_dp, style.wrappedLineSpacingDp)
    PlayerTitleStyleControl.TEXT_OFFSET_X -> getString(R.string.player_title_extra_dp, style.horizontalOffsetDp)
    PlayerTitleStyleControl.TEXT_OFFSET_Y -> getString(R.string.player_title_extra_dp, style.verticalOffsetDp)
    PlayerTitleStyleControl.METADATA_FORMAT -> getString(playerTitleStyle.metadataFormat.labelRes())
    PlayerTitleStyleControl.COMBINED_PANELS -> getString(
        if (playerTitleStyle.combinedPanels) R.string.player_title_extra_combined
        else R.string.player_title_extra_separate,
    )
    else -> ""
}

private fun PlayerTitleLongTextMode.labelRes(): Int = when (this) {
    PlayerTitleLongTextMode.DEFAULT -> R.string.player_title_extra_default
    PlayerTitleLongTextMode.WRAP -> R.string.player_title_extra_wrap
    PlayerTitleLongTextMode.ELLIPSIS -> R.string.player_title_extra_ellipsis
    PlayerTitleLongTextMode.MARQUEE -> R.string.player_title_extra_marquee
}

private fun PlayerTitleMetadataFormat.labelRes(): Int = when (this) {
    PlayerTitleMetadataFormat.WORDS -> R.string.player_title_extra_words
    PlayerTitleMetadataFormat.PADDED -> R.string.player_title_extra_padded
    PlayerTitleMetadataFormat.COMPACT -> R.string.player_title_extra_compact
}
