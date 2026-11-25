package app.mpvnova.player

import android.content.Intent
import android.media.session.MediaSession
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap

internal fun MPVActivity.initMediaSession(): MediaSession {
    val session = MediaSession(this, MPV_ACTIVITY_TAG)
    session.setFlags(0)
    session.setCallback(createMediaSessionCallback())
    return session
}

private fun MPVActivity.createMediaSessionCallback(): MediaSession.Callback = object : MediaSession.Callback() {
    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        val event = IntentCompat.getParcelableExtra(
            mediaButtonIntent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java,
        )
        if (mediaButtonIntent.action != Intent.ACTION_MEDIA_BUTTON ||
            event?.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return super.onMediaButtonEvent(mediaButtonIntent)
        // Android's default handler waits for a double tap. A remote's play/pause
        // button should act immediately, once per press, without waiting for release.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            player.cyclePause()
        }
        return true
    }
    override fun onPause() {
        player.paused = true
    }
    override fun onPlay() {
        player.paused = false
    }
    override fun onSeekTo(pos: Long) {
        player.timePos = (pos / MPV_MILLIS_PER_SECOND_DOUBLE)
    }
    override fun onSkipToNext() = playlistNext()
    override fun onSkipToPrevious() = playlistPrev()
}

internal fun MPVActivity.updateMediaSession() {
    // Coalesce same-turn property updates into one IPC. Use
    // updateMediaSessionNow() when a sync write is required.
    if (mediaSessionUpdatePending) return
    mediaSessionUpdatePending = true
    eventUiHandler.post(mediaSessionUpdateRunnable)
}

internal fun MPVActivity.updateMediaSessionNow() {
    mediaSession?.let { psc.write(it) }
}

// Property → UI handler tables. Observed properties without an entry are
// not posted to the UI unless they also changed displayed metadata.

private const val TRACK_LIST_PENDING_STALE_MS = 500L

private class TrackListUiCoalescer(activity: MPVActivity) {
    private val activityRef = WeakReference(activity)
    @Volatile var pending = false
    @Volatile var scheduledAtUptimeMs = 0L

    val runnable = Runnable {
        pending = false
        val target = activityRef.get() ?: return@Runnable
        if (!target.activityIsForeground || target.activityIsDestroying) return@Runnable
        target.runTrackListUiUpdate()
    }
}

private val trackListUiCoalescers = WeakHashMap<MPVActivity, TrackListUiCoalescer>()

private fun MPVActivity.trackListUiCoalescer(): TrackListUiCoalescer =
    synchronized(trackListUiCoalescers) {
        trackListUiCoalescers.getOrPut(this) { TrackListUiCoalescer(this) }
    }

private fun MPVActivity.runTrackListUiUpdate() {
    player.invalidateHi10pH264Cache()
    player.loadTracks()
    refreshSubPickerAfterTrackChange(tracksChanged = true)
    refreshCompanionSubtitleStyle()
    refreshSubWidthAssOverrides()
    applyFireTvVideoEdgeCropIfNeeded()
    maybeApplyShieldHi10pFallback()
}

private fun MPVActivity.scheduleTrackListUiRefresh() {
    val coalescer = trackListUiCoalescer()
    val now = SystemClock.uptimeMillis()
    synchronized(coalescer) {
        if (coalescer.pending && now - coalescer.scheduledAtUptimeMs < TRACK_LIST_PENDING_STALE_MS) {
            return
        }
        coalescer.pending = true
        coalescer.scheduledAtUptimeMs = now
    }
    eventUiHandler.post(coalescer.runnable)
}

internal fun MPVActivity.cancelTrackListUiRefresh() {
    val coalescer = synchronized(trackListUiCoalescers) { trackListUiCoalescers[this] } ?: return
    synchronized(coalescer) {
        coalescer.pending = false
    }
    eventUiHandler.removeCallbacks(coalescer.runnable)
}

private val METADATA_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "demuxer-cache-time" to { updatePlaybackBuffer() },
    "current-tracks/audio/selected" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "sid" to {
        refreshCompanionSubtitleStyle()
        refreshSubWidthAssOverrides()
        refreshSubPickerAfterTrackChange()
    },
    "current-tracks/video/image" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "hwdec-current" to {
        player.invalidateHi10pH264Cache()
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
        player.invalidateHi10pH264Cache()
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

internal fun MPVActivity.postMetadataPropertyUi(property: String, metaUpdated: Boolean) {
    if (property == "track-list") {
        player.invalidateHi10pH264Cache()
        scheduleTrackListUiRefresh()
        if (metaUpdated) scheduleMetadataUiRefresh()
        return
    }
    postPropertyUi(METADATA_UI_HANDLERS[property], metaUpdated)
}

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
