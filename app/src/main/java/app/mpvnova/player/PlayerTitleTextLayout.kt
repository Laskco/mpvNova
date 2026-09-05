package app.mpvnova.player

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

private data class PlayerTitleOriginalTextLayout(
    val maxLines: Int,
    val ellipsize: TextUtils.TruncateAt?,
    val lineSpacingExtra: Float,
    val lineSpacingMultiplier: Float,
)

internal fun TextView.applyPlayerTitleTextLayout(style: PlayerTitleTextStyle) {
    val original = getTag(R.id.player_title_original_text_layout) as? PlayerTitleOriginalTextLayout
        ?: PlayerTitleOriginalTextLayout(maxLines, ellipsize, lineSpacingExtra, lineSpacingMultiplier)
            .also { setTag(R.id.player_title_original_text_layout, it) }
    val marquee = style.longTextMode == PlayerTitleLongTextMode.MARQUEE
    setSingleLine(marquee)
    maxLines = when (style.longTextMode) {
        PlayerTitleLongTextMode.MARQUEE -> 1
        PlayerTitleLongTextMode.ELLIPSIS -> style.maxLines.takeIf { it > 0 } ?: 1
        else -> style.maxLines.takeIf { it > 0 } ?: original.maxLines
    }
    ellipsize = when (style.longTextMode) {
        PlayerTitleLongTextMode.DEFAULT -> original.ellipsize
        PlayerTitleLongTextMode.WRAP -> null
        PlayerTitleLongTextMode.ELLIPSIS -> TextUtils.TruncateAt.END
        PlayerTitleLongTextMode.MARQUEE -> TextUtils.TruncateAt.MARQUEE
    }
    setLineSpacing(
        original.lineSpacingExtra + style.wrappedLineSpacingDp * resources.displayMetrics.density,
        original.lineSpacingMultiplier,
    )
    // Selection starts marquee without making passive overlay text focusable.
    marqueeRepeatLimit = -1
    isSelected = marquee
}

internal fun MPVActivity.resetPlayerTitleTextOffsets() {
    playerTitleOffsetViews().forEach { (view, _) ->
        (view.getTag(R.id.player_title_text_offsets) as? PlayerTitleTextOffsets)?.restoreMargins()
    }
}

internal fun MPVActivity.applyPlayerTitleTextOffsets() {
    playerTitleOffsetViews().forEach { (view, style) ->
        val offsets = view.getTag(R.id.player_title_text_offsets) as? PlayerTitleTextOffsets
            ?: PlayerTitleTextOffsets(view).also { view.setTag(R.id.player_title_text_offsets, it) }
        offsets.update(style)
    }
}

private fun MPVActivity.playerTitleOffsetViews() = listOf(
    binding.playerTitleSeason to playerTitleStyle.season,
    binding.playerTitleEpisodeNumber to playerTitleStyle.episodeNumber,
    binding.playerTitlePrimary to playerTitleStyle.title,
    binding.playerTitleSecondary to playerTitleStyle.episodeTitle,
    binding.dateTextView to playerTitleStyle.date,
    binding.clockTextView to playerTitleStyle.clock,
    binding.endsAtTextView to playerTitleStyle.endsAt,
)

private data class PlayerTitleTextMargins(val left: Int, val top: Int, val right: Int, val bottom: Int)

private class PlayerTitleTextOffsets(private val view: TextView) {
    private var horizontalDp = 0
    private var verticalDp = 0
    private var listening = false
    private var originalMargins: PlayerTitleTextMargins? = null
    private val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }

    fun update(style: PlayerTitleTextStyle) {
        horizontalDp = style.horizontalOffsetDp
        verticalDp = style.verticalOffsetDp
        val enabled = horizontalDp != 0 || verticalDp != 0
        restoreMargins()
        if (enabled) {
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                originalMargins = PlayerTitleTextMargins(
                    params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin,
                )
                val density = view.resources.displayMetrics.density
                val horizontal = (kotlin.math.abs(horizontalDp) * density).toInt()
                val vertical = (kotlin.math.abs(verticalDp) * density).toInt()
                // Reserve each text's travel area so its translated bounds cannot overlap a sibling.
                params.leftMargin += horizontal
                params.rightMargin += horizontal
                params.topMargin += vertical
                params.bottomMargin += vertical
                view.layoutParams = params
            }
        }
        if (enabled != listening) {
            if (enabled) {
                view.addOnLayoutChangeListener(listener)
                (view.parent as? View)?.addOnLayoutChangeListener(listener)
            } else {
                view.removeOnLayoutChangeListener(listener)
                (view.parent as? View)?.removeOnLayoutChangeListener(listener)
            }
            listening = enabled
        }
        apply()
    }

    fun restoreMargins() {
        val margins = originalMargins ?: return
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.setMargins(margins.left, margins.top, margins.right, margins.bottom)
        view.layoutParams = params
        originalMargins = null
    }

    private fun apply() {
        val parent = view.parent as? ViewGroup ?: return
        val density = view.resources.displayMetrics.density
        // Keep translations inside the content box, including after wrapping or resizing.
        fun boundedOffset(desired: Float, start: Int, end: Int, size: Int): Float =
            if (end - start < size) 0f else desired.coerceIn(start.toFloat(), (end - size).toFloat())
        view.translationX = if (horizontalDp == 0) 0f else boundedOffset(
            horizontalDp * density,
            parent.paddingLeft - view.left,
            parent.width - parent.paddingRight - view.left,
            view.width,
        )
        view.translationY = if (verticalDp == 0) 0f else boundedOffset(
            verticalDp * density,
            parent.paddingTop - view.top,
            parent.height - parent.paddingBottom - view.top,
            view.height,
        )
    }
}

internal fun MPVActivity.playerTitleMetadataText(
    presentation: PlayerTitlePresentation,
): Pair<String?, String?> {
    val season = presentation.season
    val episode = presentation.episode
    return when (playerTitleStyle.metadataFormat) {
        PlayerTitleMetadataFormat.WORDS ->
            season?.let { getString(R.string.player_title_season, it) } to
                episode?.let { getString(R.string.player_title_episode_number, it) }
        PlayerTitleMetadataFormat.PADDED ->
            season?.let { "S${it.toString().padStart(2, '0')}" } to
                episode?.let { "E${it.toString().padStart(2, '0')}" }
        PlayerTitleMetadataFormat.COMPACT -> compactPlayerTitleMetadata(season, episode)
    }
}

private fun MPVActivity.compactPlayerTitleMetadata(season: Int?, episode: Int?): Pair<String?, String?> {
    val bothVisible = season != null && episode != null &&
        playerTitleStyle.season.visible && playerTitleStyle.episodeNumber.visible
    return season?.let { if (bothVisible) it.toString() else "S$it" } to
        episode?.let { if (bothVisible) "x${it.toString().padStart(2, '0')}" else "E$it" }
}

internal fun MPVActivity.refreshPlayerTitleMetadataFormat() {
    val stored = binding.playerTitleOverlay.getTag(R.id.player_title_presentation)
        as? PlayerTitlePresentation
    val presentation = if (playerTextStylePreviewActive) {
        (stored ?: PlayerTitlePresentation("")).let {
            it.copy(season = it.season ?: 1, episode = it.episode ?: 3)
        }
    } else stored ?: return
    val (season, episode) = playerTitleMetadataText(presentation)
    binding.playerTitleSeason.setTextIfChanged(season)
    binding.playerTitleEpisodeNumber.setTextIfChanged(episode)
}
