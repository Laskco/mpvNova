package app.mpvnova.player

import android.view.View
import androidx.core.content.ContextCompat

/**
 * mpv playlist navigation primitives. The dialog that picks a track lives
 * in PlaylistDialog.kt; this file owns the prev/next commands plus the
 * button-enable/-tint updater that follows playlist position/count changes.
 */

internal fun MPVActivity.playlistPrev() = mpvCommand(arrayOf("playlist-prev"))

internal fun MPVActivity.playlistNext() = mpvCommand(arrayOf("playlist-next"))

internal fun MPVActivity.updatePlaylistButtons() {
    val plCount = psc.playlistCount
    val plPos = psc.playlistPos

    if (!useAudioUI && plCount == 1) {
        // use View.GONE so the buttons won't take up any space
        binding.prevBtn.setVisibilityIfChanged(View.GONE)
        binding.nextBtn.setVisibilityIfChanged(View.GONE)
        return
    }
    binding.prevBtn.setVisibilityIfChanged(View.VISIBLE)
    binding.nextBtn.setVisibilityIfChanged(View.VISIBLE)

    val g = ContextCompat.getColor(this, R.color.tint_disabled)
    val w = ContextCompat.getColor(this, R.color.tint_normal)
    binding.prevBtn.setImageTintColorIfChanged(if (plPos == 0) g else w)
    binding.nextBtn.setImageTintColorIfChanged(if (plPos == plCount - 1) g else w)
    applyPlayerControlOrderAndVisibility()
}
