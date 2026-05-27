package app.mpvnova.player

import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class MpvActivityLifecycleObserver(private val activity: MPVActivity) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        activity.activityIsStopped = false
    }

    override fun onStop(owner: LifecycleOwner) {
        activity.activityIsStopped = true
    }
}

/**
 * mpv event callback wrapper. Folds property updates into the playback-state
 * cache + media session, then dispatches UI handlers via typed property tables.
 */
internal class MpvActivityEventObserver(private val activity: MPVActivity) : MpvEventObserver {

    override fun eventProperty(property: String): Unit = with(activity) {
        val metaUpdated = psc.update(property)
        if (metaUpdated) updateMediaSession()
        dispatchEventThreadMetadata(property)
        if (!activityIsForeground) return
        eventUiHandler.post { eventMetadataPropertyUi(property, metaUpdated) }
    }

    override fun eventProperty(property: String, value: Boolean): Unit = with(activity) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated) updateMediaSession()
        dispatchEventThreadBoolean(property, value, metaUpdated)
        if (!activityIsForeground) return
        eventUiHandler.post { eventBooleanPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: Long): Unit = with(activity) {
        if (psc.update(property, value)) updateMediaSession()
        if (!activityIsForeground) return
        eventUiHandler.post { eventLongPropertyUi(property) }
    }

    override fun eventProperty(property: String, value: Double): Unit = with(activity) {
        if (psc.update(property, value)) updateMediaSession()
        if (!activityIsForeground) return
        // time-pos/full fires at frame rate — coalesce to ~5 UI/sec or it
        // starves the SW Hi10p decoder.
        if (property == "time-pos/full") {
            if (!timePosUiPending) {
                timePosUiPending = true
                eventUiHandler.postDelayed(timePosUiRunnable, TIME_POS_UI_COALESCE_DELAY_MS)
            }
        } else {
            eventUiHandler.post { eventDoublePropertyUi(property) }
        }
    }

    override fun eventProperty(property: String, value: String): Unit = with(activity) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated) updateMediaSession()
        if (!activityIsForeground) return
        eventUiHandler.post { eventStringPropertyUi(property, metaUpdated) }
    }

    override fun event(eventId: Int) {
        activity.handleMpvEvent(eventId)
    }

    /** Event-thread side-effects that must run regardless of foreground state. */
    private fun MPVActivity.dispatchEventThreadBoolean(property: String, value: Boolean, metaUpdated: Boolean) {
        when (property) {
            "shuffle" -> mediaSession?.setShuffleMode(
                if (value) PlaybackStateCompat.SHUFFLE_MODE_ALL
                else PlaybackStateCompat.SHUFFLE_MODE_NONE
            )
            "mute" -> updateAudioPresence()
        }
        if (metaUpdated || property == "mute")
            handleAudioFocus()
    }

    /** FORMAT_NONE / metadata-string event-thread side-effects. */
    private fun MPVActivity.dispatchEventThreadMetadata(property: String) {
        when (property) {
            "loop-file", "loop-playlist" -> {
                mediaSession?.setRepeatMode(when (player.getRepeat()) {
                    2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                    1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                })
            }
            "current-tracks/audio/selected" -> {
                updateAudioPresence()
                if (persistAudioFilters) {
                    rebuildAudioFilters()
                    eventUiHandler.post { refreshAllFilterTints() }
                }
            }
        }
        if (property == "pause" || property == "current-tracks/audio/selected")
            handleAudioFocus()
    }
}

internal class MpvActivityLogObserver(private val activity: MPVActivity) : MpvLogObserver {
    override fun logMessage(prefix: String, level: Int, text: String) = activity.run {
        updateGpuNextRetryFrameConfirmation(prefix, text)
        maybeApplyGpuNextRenderFallback(prefix, level, text)
        maybeShowAudioNormUnderrunHint(text)
    }

    /**
     * "Audio device underrun" + normalisation + non-downmixed surround →
     * suggest the downmix toggle (one-shot hint).
     */
    private fun MPVActivity.maybeShowAudioNormUnderrunHint(text: String) {
        val shouldShowHint = !audioNormUnderrunHintShown &&
            activityIsForeground &&
            text.contains("Audio device underrun detected", ignoreCase = true) &&
            isAudioNormOn() &&
            !isDownmixOn() &&
            currentAudioChannelCount() >= MIN_SURROUND_CHANNELS
        if (!shouldShowHint) return
        audioNormUnderrunHintShown = true
        eventUiHandler.post {
            showToast(
                getString(R.string.btn_audio_norm),
                getString(R.string.toast_audio_norm_surround_hint)
            )
        }
    }
}
