package app.mpvnova.player

import java.io.File
import java.util.concurrent.atomic.AtomicLong

private val openSubtitlesCommandIds = AtomicLong(1L)

internal fun MPVActivity.openOpenSubtitlesSearch(onDismiss: () -> Unit = {}) {
    if (!OpenSubtitlesSettings.isAvailable(this)) {
        onDismiss()
        return
    }
    closeOpenSubtitlesSearch()
    val path = currentMpvPath()
    val localFile = path?.takeUnless(::isNetworkStreamPath)?.let(::File)?.takeIf { it.isFile }
    val presentation = PlayerTitleResolver.resolve(
        currentVideoTitle, currentPlayerTitleSource, psc.meta.mediaTitle, currentFileName,
    )
    val initialTitle = presentation?.title.orEmpty()
    lateinit var searchDialog: OpenSubtitlesSearchDialog
    val restore = keepPlaybackForDialog {
        searchDialog.restorePlaybackOnDismiss && !isFinishing && !isDestroyed
    }
    searchDialog = OpenSubtitlesSearchDialog(
        activity = this,
        initialQuery = OpenSubtitlesQuery(
            initialTitle, OpenSubtitlesSettings.language(this), presentation?.season, presentation?.episode,
        ),
        localFile = localFile,
        downloadedSubtitles = { openSubtitlesPlaybackDownloads.mapValues { it.value.first } },
        onSubtitle = { subtitle, result ->
            applyOpenSubtitlesDownload(subtitle, result, path)
        },
        onDismiss = {
            val returnToPanel = openSubtitlesSearchDialog === searchDialog
            if (returnToPanel) openSubtitlesSearchDialog = null
            restore()
            if (returnToPanel && canUseSubtitleResult(path)) onDismiss()
            else trackPanelChildTransition = false
        },
    )
    openSubtitlesSearchDialog = searchDialog
    searchDialog.show()
}

internal fun MPVActivity.applyOpenSubtitlesDownload(subtitle: File, result: OpenSubtitlesResult, path: String?) {
    if (!canUseSubtitleResult(path)) {
        discardOpenSubtitlesDownload(subtitle, cacheDir)
        return
    }
    val existingTrack = openSubtitlesFirstTrackId(subtitle)
    openSubtitlesPlaybackFiles += subtitle
    openSubtitlesPlaybackDownloads[result.fileId] = subtitle to result
    pendingOpenSubtitlesFocus = subtitle
    if (existingTrack != null) {
        selectTrackForFile("sub", existingTrack)
        saveUserTrackPick("sub", existingTrack)
    } else if (pendingOpenSubtitlesAdds.add(subtitle)) {
        val flags = if (result.hearingImpaired) "select+hearing-impaired" else "select"
        val language = result.language.takeIf(OPEN_SUBTITLES_LANGUAGE_CODE::matches).orEmpty()
        val requestId = openSubtitlesCommandIds.getAndIncrement()
        pendingOpenSubtitlesCommands[requestId] = subtitle
        val status = mpvCommandAsync(requestId, arrayOf("sub-add", subtitle.absolutePath, flags,
            openSubtitlesTrackTitle(result), language))
        if (status < 0) completeOpenSubtitlesAdd(requestId, status.toLong())
    }
}

private fun MPVActivity.openSubtitlesTrackTitle(result: OpenSubtitlesResult): String {
    var label = result.release.ifBlank { result.fileName }.filterNot { it.isISOControl() }
    OpenSubtitlesSettings.credentials(this)?.let { auth ->
        for (secret in listOf(auth.apiKey, auth.token).filter { it.isNotEmpty() })
            label = label.replace(secret, "[redacted]")
    }
    return getString(R.string.os_downloads_track_title, label.take(MAX_OPENSUBTITLES_TRACK_TITLE))
}

internal fun MPVActivity.releaseOpenSubtitlesPlaybackFiles() {
    pendingOpenSubtitlesFocus = null
    openSubtitlesPlaybackFiles.forEach(::releaseOpenSubtitlesDownload)
    openSubtitlesPlaybackFiles.clear()
    openSubtitlesPlaybackDownloads.clear()
    pendingOpenSubtitlesAdds.clear()
    pendingOpenSubtitlesCommands.clear()
    pendingOpenSubtitlesRemoveCommands.clear()
    pendingOpenSubtitlesRemovals.forEach { discardOpenSubtitlesDownload(it, cacheDir) }
    pendingOpenSubtitlesRemovals.clear()
}

private const val MAX_OPENSUBTITLES_TRACK_TITLE = 160

private fun MPVActivity.canUseSubtitleResult(path: String?): Boolean {
    if (isFinishing || isDestroyed) return false
    return !playbackEnded && currentMpvPath() == path
}

internal fun MPVActivity.closeOpenSubtitlesSearch(restorePlayback: Boolean = true) {
    val dialog = openSubtitlesSearchDialog ?: return
    openSubtitlesSearchDialog = null
    drawerReopenPending = false
    trackPanelChildTransition = false
    dialog.close(restorePlayback)
}
