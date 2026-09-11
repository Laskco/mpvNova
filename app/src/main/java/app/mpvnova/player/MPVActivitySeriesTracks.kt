package app.mpvnova.player

import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal fun MPVActivity.selectTrackForFile(type: String, id: Int) {
    val property = if (type == "audio") "aid" else "sid"
    mpvSetPropertyString("file-local-options/$property", if (id == -1) "no" else id.toString())
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
    val key = currentTrackSeriesKey ?: return false
    val prefs = getDefaultSharedPreferences(applicationContext)
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
    currentTrackSeriesKey = resolveTrackSeriesKey()
    val prefs = getDefaultSharedPreferences(applicationContext)
    val selection = currentTrackSeriesKey?.let {
        SeriesTrackSelectionCache(prefs.getString(SERIES_TRACK_SELECTIONS_KEY, null)).get(it)
    }
    val (audioId, subId) = selection?.resolve(listTrackMeta("audio"), listTrackMeta("sub")) ?: (null to null)
    if (audioId != null) selectTrackForFile("audio", audioId)
    val useLegacy = currentTrackSeriesKey == null &&
        !prefs.getBoolean(PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES, false)
    if (useLegacy) applyRememberedTrack("audio")
    when {
        subId != null -> selectTrackForFile("sub", subId)
        preserveForwardedSubtitles -> Unit
        !applyPreferredLanguageSubtitles() && useLegacy -> applyRememberedTrack("sub")
    }
}

private const val SERIES_TRACK_SELECTIONS_KEY = "series_track_selections_v1"
