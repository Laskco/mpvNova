package app.mpvnova.player

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

    private var pendingEndFileReason: Long? = null
    private var pendingFileEntryId: Long? = null

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
        dispatchEventThreadBoolean(property, metaUpdated)
        if (!activityIsForeground) return
        eventUiHandler.post { eventBooleanPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: Long): Unit = with(activity) {
        if (property == END_FILE_REASON_PROPERTY) {
            pendingEndFileReason = value
            return
        }
        if (property == FILE_EVENT_ENTRY_ID_PROPERTY) {
            pendingFileEntryId = value.takeUnless { it == -1L }
            return
        }
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
        val endFileReason = pendingEndFileReason
        val fileEntryId = pendingFileEntryId
        pendingEndFileReason = null
        pendingFileEntryId = null
        if (eventId == MpvEvent.MPV_EVENT_END_FILE &&
            endFileReason == MPV_END_FILE_REASON_REDIRECT
        ) return
        activity.handleMpvFileEvent(eventId, fileEntryId)
    }

    /** Event-thread side-effects that must run regardless of foreground state. */
    private fun MPVActivity.dispatchEventThreadBoolean(property: String, metaUpdated: Boolean) {
        when (property) {
            "mute" -> updateAudioPresence()
        }
        if (metaUpdated || property == "mute")
            handleAudioFocus()
    }

    /** FORMAT_NONE / metadata-string event-thread side-effects. */
    private fun MPVActivity.dispatchEventThreadMetadata(property: String) {
        when (property) {
            "current-tracks/audio/selected" -> {
                updateAudioPresence()
                if (persistAudioFilters && !audioFiltersAwaitingPostLoadReconcile) {
                    rebuildAudioFilters()
                    eventUiHandler.post { refreshAllFilterTints() }
                }
            }
        }
        if (property == "pause" || property == "current-tracks/audio/selected")
            handleAudioFocus()
    }

    private companion object {
        const val END_FILE_REASON_PROPERTY = "end-file-reason"
        const val FILE_EVENT_ENTRY_ID_PROPERTY = "file-event-entry-id"
        const val MPV_END_FILE_REASON_REDIRECT = 5L
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
