package app.mpvnova.player

import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal fun MPVActivity.selectTrackForFile(type: String, id: Int) {
    if (getDefaultSharedPreferences(applicationContext).getBoolean(PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES, false)) {
        val property = if (type == "audio") "aid" else "sid"
        mpvSetPropertyString("file-local-options/$property", if (id == -1) "no" else id.toString())
    } else {
        when (type) {
            "audio" -> player.aid = id
            "sub" -> player.sid = id
        }
    }
}

internal fun MPVActivity.resolveTrackSeriesKey(): String? {
    // Freeze local identity before asynchronous TMDB results can rename the displayed series.
    val filePresentation = PlayerTitleResolver.resolve(currentFileName, null, null, currentFileName)
    val presentation = filePresentation?.takeIf { it.episode != null } ?: PlayerTitleResolver.resolve(
        currentVideoTitle, currentPlayerTitleSource, mpvGetPropertyString("media-title"), currentFileName,
    )
    return trackSeriesKey(presentation)
}

internal fun MPVActivity.saveSeriesTrackPick(type: String, id: Int): Boolean {
    val prefs = getDefaultSharedPreferences(applicationContext)
    // The setting can be enabled after this file has already loaded.
    val key = if (prefs.getBoolean(PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES, false)) {
        currentTrackSeriesKey ?: resolveTrackSeriesKey()?.also { currentTrackSeriesKey = it }
    } else null
    if (key == null) return false
    val cache = SeriesTrackSelectionCache(prefs.getString(SERIES_TRACK_SELECTIONS_KEY, null))
    val selection = SeriesTrackSelection(
        savedSeriesTrack(listTrackMeta("audio"), if (type == "audio") id else player.aid),
        savedSeriesTrack(listTrackMeta("sub"), if (type == "sub") id else player.sid),
    )
    cache.put(key, selection)
    prefs.edit().putString(SERIES_TRACK_SELECTIONS_KEY, cache.serialize()).apply()
    return true
}

internal fun MPVActivity.applyFileTrackSelections(preserveForwardedSubtitles: Boolean) {
    val prefs = getDefaultSharedPreferences(applicationContext)
    if (!prefs.getBoolean(PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES, false)) {
        currentTrackSeriesKey = null
        applyRememberedTrack("audio")
        if (!preserveForwardedSubtitles) applyRememberedTrack("sub")
        return
    }
    currentTrackSeriesKey = resolveTrackSeriesKey()
    val selection = currentTrackSeriesKey?.let {
        SeriesTrackSelectionCache(prefs.getString(SERIES_TRACK_SELECTIONS_KEY, null)).get(it)
    }
    val (audioId, subId) = selection?.resolve(listTrackMeta("audio"), listTrackMeta("sub")) ?: (null to null)
    if (audioId != null) selectTrackForFile("audio", audioId)
    when {
        subId != null -> selectTrackForFile("sub", subId)
        preserveForwardedSubtitles -> Unit
        else -> applyPreferredLanguageSubtitles()
    }
}

private const val SERIES_TRACK_SELECTIONS_KEY = "series_track_selections_v1"
