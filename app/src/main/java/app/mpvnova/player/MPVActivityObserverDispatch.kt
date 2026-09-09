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
// silently dropped on the UI side (event-thread side-effects only).

private val METADATA_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "track-list" to {
        player.loadTracks()
        applyFireTvVideoEdgeCropIfNeeded()
        maybeApplyShieldHi10pFallback()
    },
    "current-tracks/audio/selected" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "current-tracks/video/image" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "hwdec-current" to {
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

internal fun MPVActivity.eventMetadataPropertyUi(property: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    METADATA_UI_HANDLERS[property]?.invoke(this)
    if (metaUpdated) scheduleMetadataUiRefresh()
}

internal fun MPVActivity.eventBooleanPropertyUi(property: String, value: Boolean) {
    if (!activityIsForeground) return
    // pause has value-dependent behavior (overlay auto-close on unpause).
    when (property) {
        "pause" -> handlePauseUi(value)
        "paused-for-cache" -> {
            streamCacheLoading = value
            refreshLoadingOverlay()
        }
        "mute" -> updateAudioUI()
    }
}

internal fun MPVActivity.eventLongPropertyUi(property: String) {
    if (!activityIsForeground) return
    LONG_UI_HANDLERS[property]?.invoke(this)
}

internal fun MPVActivity.eventDoublePropertyUi(property: String) {
    if (!activityIsForeground) return
    DOUBLE_UI_HANDLERS[property]?.invoke(this)
}

internal fun MPVActivity.eventStringPropertyUi(property: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    STRING_UI_HANDLERS[property]?.invoke(this)
    if (metaUpdated) scheduleMetadataUiRefresh()
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
