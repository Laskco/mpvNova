package app.mpvnova.player

import android.os.SystemClock
import android.media.session.MediaSession
import java.util.Locale

internal fun MPVActivity.initMediaSession(): MediaSession {
    val session = MediaSession(this, MPV_ACTIVITY_TAG)
    session.setFlags(0)
    session.setCallback(mediaSessionCallback)
    return session
}

internal fun MPVActivity.updateMediaSession() {
    // Coalesce same-turn property updates into one IPC. Use
    // updateMediaSessionNow() when a sync write is required.
    if (mediaSessionUpdatePending) return
    mediaSessionUpdatePending = true
    eventUiHandler.post(mediaSessionUpdateRunnable)
}

internal fun MPVActivity.updateMediaSessionNow() {
    synchronized (psc) {
        mediaSession?.let { psc.write(it) }
    }
}

// Property → UI handler tables. Observed properties without an entry are
// not posted to the UI unless they also changed displayed metadata.

private val METADATA_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "demuxer-cache-time" to { updatePlaybackBuffer() },
    "track-list" to {
        player.loadTracks()
        refreshSubPickerAfterTrackChange(tracksChanged = true)
        refreshCompanionSubtitleStyle()
        applyFireTvVideoEdgeCropIfNeeded()
        maybeApplyShieldHi10pFallback()
    },
    "current-tracks/audio/selected" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "sid" to {
        refreshCompanionSubtitleStyle()
        refreshSubPickerAfterTrackChange()
    },
    "current-tracks/video/image" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "hwdec-current" to {
        if (!mpvGetPropertyString("hwdec-current").isNullOrBlank())
            gpuNextFallbackState.clearHardwareDecoderRejected()
        updateDecoderButton()
    },
)

private val LONG_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "video-dec-params/w" to { applyFireTvVideoEdgeCropIfNeeded() },
    "video-dec-params/h" to { applyFireTvVideoEdgeCropIfNeeded() },
    "playlist-pos" to { updatePlaylistButtons() },
    "playlist-count" to { updatePlaylistButtons() },
)

private val DOUBLE_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    // time-pos/full is intentionally absent — coalesced via timePosUiRunnable
    // to keep the SW decoder from being preempted by per-frame UI dispatches.
    "duration/full" to { updatePlaybackDuration(psc.duration) },
    "video-params/aspect" to { updatePiPParams() },
    "video-params/rotate" to { updatePiPParams() },
)

private val STRING_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "speed" to { updateSpeedButton() },
    "video-params/gamma" to { applyFireTvVideoEdgeCropIfNeeded() },
    "current-vo" to {
        updateDecoderButton()
    },
)

private val BOOLEAN_UI_HANDLERS: Map<String, MPVActivity.(Boolean) -> Unit> = mapOf(
    // pause has value-dependent behavior (overlay auto-close on unpause).
    "pause" to { paused -> handlePauseUi(paused) },
    "paused-for-cache" to { value ->
        streamCacheLoading = value
        refreshLoadingOverlay()
    },
    "mute" to { _ -> updateAudioUI() },
)

internal fun MPVActivity.postMetadataPropertyUi(property: String, metaUpdated: Boolean) =
    postPropertyUi(METADATA_UI_HANDLERS[property], metaUpdated)

internal fun MPVActivity.postBooleanPropertyUi(property: String, value: Boolean) {
    val handler = BOOLEAN_UI_HANDLERS[property] ?: return
    postPropertyUi({ handler(value) })
}

internal fun MPVActivity.postLongPropertyUi(property: String) = postPropertyUi(LONG_UI_HANDLERS[property])

internal fun MPVActivity.postDoublePropertyUi(property: String) = postPropertyUi(DOUBLE_UI_HANDLERS[property])

internal fun MPVActivity.postStringPropertyUi(property: String, metaUpdated: Boolean) =
    postPropertyUi(STRING_UI_HANDLERS[property], metaUpdated)

private fun MPVActivity.postPropertyUi(handler: (MPVActivity.() -> Unit)?, metaUpdated: Boolean = false) {
    if (handler == null && !metaUpdated) return
    eventUiHandler.post {
        if (!activityIsForeground) return@post
        handler?.invoke(this)
        if (metaUpdated) scheduleMetadataUiRefresh()
    }
}

internal fun MPVActivity.scheduleMetadataUiRefresh() {
    if (metadataUiPending) return
    metadataUiPending = true
    eventUiHandler.post(metadataUiRunnable)
}

internal fun MPVActivity.maybeApplyGpuNextRenderFallback(prefix: String, level: Int, text: String) {
    if (!autoDecoderFallback || sessionDecoderMode == MPVView.DECODER_MODE_MPV_CONF) return
    val renderError = level <= MpvLogLevel.MPV_LOG_LEVEL_ERROR && isGpuNextRenderFailure(prefix, text)
    if (!renderError ||
        !player.requestedVideoOutput.trim().startsWith("gpu-next", ignoreCase = true)
    )
        return
    when (gpuNextFallbackState.onRenderFailure(
        SystemClock.uptimeMillis(),
        player.hwdecActive.trim().lowercase(Locale.US),
        normalizedHwdecOption(),
    )) {
        GpuNextFallbackAction.RetryWithCopyHwdec -> retryGpuNextWithCopyHwdec(prefix, text)
        GpuNextFallbackAction.FallbackToGpu -> fallbackGpuNextToGpu(prefix, text)
        null -> Unit
    }
}
