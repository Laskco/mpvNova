package app.mpvnova.player

import android.view.View
import androidx.core.view.isVisible

internal fun MPVActivity.updatePlayerTitleOverlay() {
    val title = currentVideoTitle?.trim().orEmpty()
    if (!shouldShowPlayerTitleOverlay(title)) {
        hidePlayerTitleOverlay()
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

private fun MPVActivity.resolvePlayerTitlePresentation(title: String): PlayerTitlePresentation =
    PlayerTitleResolver.resolve(
        displayTitle = title,
        sourceTitle = currentPlayerTitleSource,
        mediaTitle = psc.meta.mediaTitle,
        fileName = currentFileName,
    ) ?: PlayerTitlePresentation(title)

private fun MPVActivity.bindPlayerTitlePresentation(
    presentation: PlayerTitlePresentation,
): Boolean {
    val season = presentation.season?.let { getString(R.string.player_title_season, it) }
    val episodeNumber = presentation.episode?.let {
        getString(R.string.player_title_episode_number, it)
    }
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
