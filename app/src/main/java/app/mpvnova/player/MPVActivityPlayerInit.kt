package app.mpvnova.player

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Looper
import android.util.Log
import android.view.View
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
internal fun MPVActivity.initListeners() {
    bindClickListeners()
    bindLongClickListeners()
    bindSeekbarListeners()
    bindTouchAndInsetsListeners()
    bindActivityCallbacks()
}

internal fun MPVActivity.finishWithResult(code: Int, includeTimePos: Boolean = false) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        runOnUiThread { finishWithResult(code, includeTimePos) }
        return
    }
    if (isFinishing) // only count first call
        return
    val result = if (includeTimePos) {
        if (resultPositionMs < 0L) {
            capturePlaybackResultSnapshot(updateCompletion = true)
        } else if (!playbackCompletionReached) {
            playbackCompletionReached = isPlaybackCompleteForResult(resultPositionMs, resultDurationMs)
        }
        val endBy = if (playbackCompletionReached)
            EXTERNAL_END_BY_PLAYBACK_COMPLETION
        else
            EXTERNAL_END_BY_USER
        buildExternalPlaybackResultIntent(endBy)
    } else {
        Intent(RESULT_INTENT).apply {
            data = if (intent.data?.scheme == "file") null else intent.data
        }
    }
    if (intent.getBooleanExtra(EXTRA_EXTERNAL_PLAYER_RESULT, false)) {
        Log.v(
            MPV_ACTIVITY_TAG,
            "external-player: direct result caller=${intent.getStringExtra(EXTRA_EXTERNAL_CALLER_PACKAGE)} " +
                "code=${code.resultName()} action=${result.action} extras=${result.resultExtraSummary()}"
        )
    }
    setResult(code, result)
    finish()
}

internal fun MPVActivity.resetPlaybackResultState() {
    playbackHasStarted = false
    playbackCompletionReached = false
    resultPositionMs = -1L
    resultDurationMs = 0L
}

internal fun MPVActivity.isNetworkStreamPath(path: String?): Boolean {
    val normalized = path?.trim()?.lowercase(Locale.US) ?: return false
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}

internal fun MPVActivity.currentMpvPath(): String? {
    return mpvGetPropertyString("stream-open-filename")
        ?: mpvGetPropertyString("path")
        ?: mpvGetPropertyString("filename")
}

internal fun MPVActivity.prepareStreamLoading(path: String?) {
    streamOpenLoading = isNetworkStreamPath(path)
    streamCacheLoading = false
    refreshLoadingOverlay()
}

internal fun MPVActivity.refreshLoadingOverlay() {
    val visible = streamOpenLoading || streamCacheLoading
    binding.loadingText.setText(
        if (streamCacheLoading) R.string.player_buffering_stream
        else R.string.player_loading_stream
    )
    refreshStreamLoadingCover()
    binding.loadingOverlay.animate().cancel()
    if (visible) {
        if (binding.loadingOverlay.visibility != View.VISIBLE) {
            binding.loadingOverlay.alpha = 0f
            binding.loadingOverlay.visibility = View.VISIBLE
        }
        binding.loadingOverlay.animate()
            .alpha(1f)
            .setDuration(LOADING_OVERLAY_FADE_MS)
            .setListener(null)
            .withLayer()
    } else if (binding.loadingOverlay.visibility == View.VISIBLE) {
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(LOADING_OVERLAY_FADE_MS)
            .withLayer()
            .withEndAction { binding.loadingOverlay.visibility = View.GONE }
    }
}

// Opaque backdrop tied to the initial network-stream open (streamOpenLoading), so the
// embedded cover-art still and the demuxer's far-offset network seeks happen behind black
// and the user sees loading -> real video. Mid-playback rebuffering (streamCacheLoading)
// deliberately keeps the paused frame visible, so the cover is not tied to it.
private fun MPVActivity.refreshStreamLoadingCover() {
    val cover = binding.streamLoadingCover
    cover.animate().cancel()
    if (streamOpenLoading) {
        // Snap on (no fade-in) so no cover-art frame slips through before it's opaque.
        cover.visibility = View.VISIBLE
        cover.alpha = 1f
    } else if (cover.visibility == View.VISIBLE) {
        // The first decoded frames are the most timing-sensitive. Fading a full-screen
        // black layer over the SurfaceView forces expensive composition exactly then.
        cover.alpha = 0f
        cover.visibility = View.GONE
    }
}

internal fun MPVActivity.updateAudioPresence() {
    val haveAudio = mpvGetPropertyBoolean("current-tracks/audio/selected")
    if (haveAudio == null) {
        // If we *don't know* if there's an active audio track then don't update to avoid
        // spurious UI changes. The property will become available again later.
        return
    }
    isPlayingAudio = (haveAudio && mpvGetPropertyBoolean("mute") != true)
}
