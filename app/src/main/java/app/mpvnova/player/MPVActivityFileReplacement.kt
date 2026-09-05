package app.mpvnova.player

import android.content.Intent

internal fun MPVActivity.applyNewIntentReplacement(
    intent: Intent,
    filepath: String,
    nextResumeSource: String?,
) = synchronized(fileReplacementLock) {
    // Do not save the same outgoing snapshot under an unplayed intermediate intent.
    val replacementWasPending = suppressEndFileFinishForReplace
    if (!replacementWasPending) saveResumePosition()
    fileReplacementGeneration++
    suppressEndFileFinishForReplace = true
    pendingReplacementEntryId = null
    currentResumeSource = nextResumeSource
    prepareMediaTitleFromIntent(intent, filepath)
    parseIntentExtras(intent.extras)
}

internal fun MPVActivity.loadReplacementFile(filepath: String) = synchronized(fileReplacementLock) {
    // Read the command result atomically, before redirects can change the playlist.
    pendingReplacementEntryId = mpvLoadReplacementFile(filepath)
}

internal fun MPVActivity.shouldHandleMpvStartFile(entryId: Long?): Boolean = synchronized(fileReplacementLock) {
    if (!suppressEndFileFinishForReplace) return@synchronized true
    val expectedEntry = pendingReplacementEntryId ?: return@synchronized false
    val matches = expectedEntry < 0L || entryId == null || entryId == expectedEntry
    if (matches) {
        pendingReplacementEntryId = null
        suppressEndFileFinishForReplace = false
    }
    matches
}

internal fun MPVActivity.handleMpvFileEvent(eventId: Int, entryId: Long?) = synchronized(fileReplacementLock) {
    if (eventId == MpvEvent.MPV_EVENT_START_FILE && !shouldHandleMpvStartFile(entryId))
        return@synchronized
    // An intermediate file may finish loading while a newer replacement is queued.
    // Its callbacks must not consume the incoming resume state or hide its loader.
    if (suppressEndFileFinishForReplace &&
        (eventId == MpvEvent.MPV_EVENT_FILE_LOADED || eventId == MpvEvent.MPV_EVENT_PLAYBACK_RESTART)
    ) return@synchronized
    handleMpvEvent(eventId)
}
