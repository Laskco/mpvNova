package app.mpvnova.player

import android.view.View
import androidx.core.view.isVisible

internal fun MPVActivity.updatePlayerTitleOverlay() {
    val title = currentVideoTitle?.trim().orEmpty()
    if (!shouldShowPlayerTitleOverlay(title)) {
        hidePlayerTitleOverlay()
        if (shouldPrefetchPlayerTitle(title)) {
            resolvePlayerTitlePresentation(title, prefetchOnly = true)
        }
        return
    }

    val presentation = resolvePlayerTitlePresentation(title)
    val wasHidden = !binding.playerTitleOverlay.isVisible
    val contentChanged = bindPlayerTitlePresentation(presentation)
    applyPlayerTitleStyle()
    updatePlayerTitleWidth()
    binding.playerTitleOverlay.alpha = 1f
    binding.playerTitleOverlay.setVisibilityIfChanged(View.VISIBLE)
    if (wasHidden)
        updatePlayerToastPlacement()
    if (wasHidden || contentChanged)
        schedulePlayerTitleToastPlacement()
}

private fun MPVActivity.shouldPrefetchPlayerTitle(title: String): Boolean =
    !playbackEnded && !useAudioUI && showMediaTitle && title.isNotBlank()

private fun MPVActivity.shouldShowPlayerTitleOverlay(title: String): Boolean {
    val playbackAllowsTitle =
        !playbackEnded && !inPictureInPicture() && !isStatsOverlayVisible()
    val contentAllowsTitle = !useAudioUI && showMediaTitle && title.isNotBlank()
    val playerAllowsTitle = playbackAllowsTitle && contentAllowsTitle
    val dialogAllowsTitle =
        playerDialogStack.none { it.isShowing } || playerTextStylePreviewActive
    val controlsAllowTitle = binding.controls.isVisible || shouldShowTitleWhileControlsHidden()
    return playerAllowsTitle && dialogAllowsTitle && controlsAllowTitle
}

internal fun MPVActivity.shouldShowTitleWhileControlsHidden(): Boolean {
    val playbackAllowsTitle =
        !playbackEnded && !inPictureInPicture() && !isStatsOverlayVisible()
    val pausedTitleEnabled = showTitleOnPause && psc.pause
    val dialogAllowsTitle =
        playerDialogStack.none { it.isShowing } || playerTextStylePreviewActive
    return playbackAllowsTitle && pausedTitleEnabled && dialogAllowsTitle
}

private fun MPVActivity.hidePlayerTitleOverlay() {
    val wasVisible = binding.playerTitleOverlay.isVisible
    binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
    if (wasVisible)
        updatePlayerToastPlacement()
}

private fun MPVActivity.resolvePlayerTitlePresentation(
    title: String,
    prefetchOnly: Boolean = false,
): PlayerTitlePresentation = tmdbTitleController.resolveInputs(
    inputs = OverlayTitleInputs(
        display = title,
        source = currentPlayerTitleSource,
        media = psc.meta.mediaTitle,
        filename = currentFileName,
    ),
    titleCache = overlayTitleCache,
    prefetchOnly = prefetchOnly,
)

internal data class OverlayTitleInputs(
    val display: String,
    val source: String?,
    val media: String?,
    val filename: String?,
)

internal class OverlayTitleCache {
    @Volatile
    private var last: Pair<OverlayTitleInputs, PlayerTitlePresentation>? = null

    fun peek(inputs: OverlayTitleInputs): PlayerTitlePresentation? =
        last?.let { (previous, title) -> if (inputs == previous) title else null }

    fun put(inputs: OverlayTitleInputs, parsed: PlayerTitlePresentation) {
        last = inputs to parsed
    }
}

private fun MPVActivity.bindPlayerTitlePresentation(
    presentation: PlayerTitlePresentation,
): Boolean {
    binding.playerTitleOverlay.setTag(R.id.player_title_presentation, presentation)
    val (season, episodeNumber) = playerTitleMetadataText(presentation)
    val contentChanged = playerTitleContentChanged(presentation, season, episodeNumber)
    binding.playerTitleSeason.setTextIfChanged(season)
    binding.playerTitleEpisodeNumber.setTextIfChanged(episodeNumber)
    binding.playerTitlePrimary.setTextIfChanged(presentation.title)
    binding.playerTitleSecondary.setTextIfChanged(presentation.episodeTitle)
    return contentChanged
}

private fun MPVActivity.playerTitleContentChanged(
    presentation: PlayerTitlePresentation,
    season: String?,
    episodeNumber: String?,
): Boolean = binding.playerTitleSeason.text.toString() != season.orEmpty() ||
    binding.playerTitleEpisodeNumber.text.toString() != episodeNumber.orEmpty() ||
    binding.playerTitlePrimary.text.toString() != presentation.title ||
    binding.playerTitleSecondary.text.toString() != presentation.episodeTitle.orEmpty()

private fun MPVActivity.schedulePlayerTitleToastPlacement() {
    binding.playerTitleOverlay.post {
        if (binding.playerTitleOverlay.isVisible)
            updatePlayerToastPlacement()
    }
}
