package app.mpvnova.player

import android.text.Layout
import android.text.StaticLayout
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.ceil

internal fun MPVActivity.updatePlayerTitleWidth() {
    val horizontalMargin = Utils.convertDp(activityContext, PLAYER_TITLE_HORIZONTAL_MARGIN_DP)
    val screenWidth = resources.displayMetrics.widthPixels
    val availableWidth = (screenWidth - horizontalMargin * 2).coerceAtLeast(1)
    val maximumWidth = playerTitleMaximumWidth(screenWidth)
    val cappedWidth = minOf(
        availableWidth,
        maximumWidth,
    )
    listOf(
        binding.playerTitleSeason,
        binding.playerTitleEpisodeNumber,
        binding.playerTitlePrimary,
    ).forEach { textView ->
        if (textView.maxWidth != cappedWidth) textView.maxWidth = cappedWidth
    }
    val episodeWidth = episodeTitleMaxWidth(cappedWidth)
    if (binding.playerTitleSecondary.maxWidth != episodeWidth)
        binding.playerTitleSecondary.maxWidth = episodeWidth
    fitPrimaryPlayerTitle(cappedWidth)
}

private fun MPVActivity.playerTitleMaximumWidth(screenWidth: Int): Int {
    val panel = playerTitleStyle.titlePanel
    val usesCompactEdgeWidth =
        panel.widthPercent == PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT &&
            panel.alignment != PlayerTitlePanelAlignment.CENTER
    if (!usesCompactEdgeWidth) {
        return Utils.convertDp(activityContext, PLAYER_TITLE_MAX_WIDTH_DP)
    }
    return minOf(
        Utils.convertDp(activityContext, PLAYER_TITLE_EDGE_MAX_WIDTH_DP),
        screenWidth * PLAYER_TITLE_EDGE_MAX_WIDTH_PERCENT / MAX_PERCENT,
    )
}

private fun MPVActivity.episodeTitleMaxWidth(cappedWidth: Int): Int {
    if (playerTitleStyle.titlePanel.widthPercent != PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT) {
        return cappedWidth
    }
    val contextWidth = listOf(
        binding.playerTitleSeason,
        binding.playerTitleContextSeparator,
        binding.playerTitleEpisodeNumber,
    ).sumOf { it.desiredVisibleTextWidth() }
    val anchorWidth = maxOf(contextWidth, binding.playerTitlePrimary.desiredVisibleTextWidth())
    val minimumWidth = Utils.convertDp(
        activityContext,
        PLAYER_TITLE_EPISODE_AUTO_MIN_WIDTH_DP,
    )
    val maximumGrowth = Utils.convertDp(
        activityContext,
        PLAYER_TITLE_EPISODE_AUTO_MAX_GROWTH_DP,
    )
    return minOf(cappedWidth, maxOf(minimumWidth, anchorWidth + maximumGrowth))
}

private fun MPVActivity.fitPrimaryPlayerTitle(cappedWidth: Int) {
    val title = binding.playerTitlePrimary.text.toString()
    val fontScale = resources.configuration.fontScale
    val preferredSizeSp = playerTitleStyle.title.sizeSp
    val contentMatches = fittedPlayerTitleText == title && fittedPlayerTitleWidth == cappedWidth
    val sizingMatches = fittedPlayerTitleFontScale == fontScale &&
        fittedPlayerTitlePreferredSizeSp == preferredSizeSp
    if (contentMatches && sizingMatches) return

    binding.playerTitlePrimary.fitPlayerTitleText(
        availableWidth = cappedWidth,
        preferredSizeSp = preferredSizeSp,
        minimumSizeSp = (preferredSizeSp - PLAYER_TITLE_AUTO_FIT_RANGE_SP)
            .coerceAtLeast(PLAYER_TITLE_MIN_CUSTOM_SIZE_SP),
    )
    fittedPlayerTitleText = title
    fittedPlayerTitleWidth = cappedWidth
    fittedPlayerTitleFontScale = fontScale
    fittedPlayerTitlePreferredSizeSp = preferredSizeSp
}

private fun TextView.desiredVisibleTextWidth(): Int {
    if (visibility != View.VISIBLE || text.isNullOrBlank()) return 0
    val displayedText = transformationMethod?.getTransformation(text, this) ?: text
    return ceil(Layout.getDesiredWidth(displayedText, paint).toDouble()).toInt() +
        compoundPaddingLeft + compoundPaddingRight
}

private fun TextView.fitPlayerTitleText(
    availableWidth: Int,
    preferredSizeSp: Float,
    minimumSizeSp: Float,
) {
    val value = transformationMethod?.getTransformation(text, this) ?: text ?: return
    val textWidth = availableWidth - compoundPaddingLeft - compoundPaddingRight
    if (value.isBlank() || textWidth <= 0) return

    val originalTextSize = paint.textSize
    val chosenSizeSp = generateSequence(preferredSizeSp) { sizeSp ->
        (sizeSp - PLAYER_TITLE_TEXT_SIZE_STEP_SP).takeIf { it >= minimumSizeSp }
    }.firstOrNull { sizeSp ->
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp,
            resources.displayMetrics,
        )
        StaticLayout.Builder.obtain(value, 0, value.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setBreakStrategy(breakStrategy)
            .setHyphenationFrequency(hyphenationFrequency)
            .setIncludePad(includeFontPadding)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .build()
            .lineCount <= maxLines
    } ?: minimumSizeSp
    paint.textSize = originalTextSize

    val chosenSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        chosenSizeSp,
        resources.displayMetrics,
    )
    if (kotlin.math.abs(textSize - chosenSizePx) >= PLAYER_TITLE_TEXT_SIZE_TOLERANCE_PX) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, chosenSizePx)
    }
}
