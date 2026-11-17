package app.mpvnova.player

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.lang.IllegalArgumentException

internal fun MPVActivity.availableSecondarySubTracks(): List<MPVView.Track> {
    val subs = player.tracks["sub"] ?: return emptyList()
    val primarySid = player.sid
    return subs.filter { it.mpvId >= 1 && it.mpvId != primarySid }
}

internal fun MPVActivity.listTrackMeta(type: String): List<TrackMeta> = buildList {
    forEachTrackMeta({ it == type }) { _, track -> add(track) }
}

internal fun listAudioAndSubtitleMeta(): Pair<List<TrackMeta>, List<TrackMeta>> {
    val audio = mutableListOf<TrackMeta>()
    val subtitles = mutableListOf<TrackMeta>()
    forEachTrackMeta({ it == "audio" || it == "sub" }) { type, track ->
        if (type == "audio") audio.add(track) else subtitles.add(track)
    }
    return audio to subtitles
}

private inline fun forEachTrackMeta(
    accepts: (String) -> Boolean,
    onTrack: (String, TrackMeta) -> Unit,
) {
    val count = mpvGetPropertyInt("track-list/count") ?: return
    for (index in 0 until count) {
        val prefix = "track-list/$index"
        val type = mpvGetPropertyString("$prefix/type")?.takeIf(accepts) ?: continue
        readTrackMeta(prefix)?.let { onTrack(type, it) }
    }
}

private fun readTrackMeta(prefix: String): TrackMeta? {
    val id = mpvGetPropertyInt("$prefix/id") ?: return null
    val title = mpvGetPropertyString("$prefix/title") ?: ""
    val lang = mpvGetPropertyString("$prefix/lang") ?: ""
    val forced = mpvGetPropertyBoolean("$prefix/forced") == true
    return TrackMeta(id, title, lang, forced)
}

internal fun MPVActivity.saveUserTrackPick(type: String, mpvId: Int) {
    if (saveSeriesTrackPick(type, mpvId)) return
    val prefs = getDefaultSharedPreferences(applicationContext)
    val (titleKey, langKey) = trackMemoryKeys(type)
    when {
        type == "sub" && mpvId == -1 -> {
            prefs.edit().apply {
                putBoolean(TRACK_MEMORY_SUB_OFF_KEY, true)
                remove(titleKey)
                remove(langKey)
                remove(TRACK_MEMORY_SUB_FORCED_KEY)
                remove(TRACK_MEMORY_SUB_DUPLICATE_INDEX_KEY)
                remove(TRACK_MEMORY_SUB_DUPLICATE_COUNT_KEY)
                remove(TRACK_MEMORY_SUB_SERIES_KEY)
                apply()
            }
            Log.v(MPV_ACTIVITY_TAG, "track-memory: saved sub track off")
        }
        mpvId != -1 -> saveTrackMetaPick(type, mpvId, prefs, titleKey, langKey)
    }
}

private fun MPVActivity.saveTrackMetaPick(
    type: String,
    mpvId: Int,
    prefs: SharedPreferences,
    titleKey: String,
    langKey: String
) {
    val tracks = listTrackMeta(type)
    val meta = tracks.firstOrNull { it.mpvId == mpvId }
    if (meta == null) {
        if (type == "sub") {
            prefs.edit().remove(TRACK_MEMORY_SUB_OFF_KEY).apply()
        }
        return
    }
    prefs.edit().apply {
        if (type == "sub") {
            remove(TRACK_MEMORY_SUB_OFF_KEY)
            putBoolean(TRACK_MEMORY_SUB_FORCED_KEY, meta.forced)
            val duplicates = matchingTrackDuplicates(tracks, meta.title, meta.lang, meta.forced)
            val seriesKey = resolveTrackSeriesKey()
            if (duplicates.size > 1 && seriesKey != null) {
                putInt(TRACK_MEMORY_SUB_DUPLICATE_INDEX_KEY, duplicates.indexOf(meta))
                putInt(TRACK_MEMORY_SUB_DUPLICATE_COUNT_KEY, duplicates.size)
                putString(TRACK_MEMORY_SUB_SERIES_KEY, seriesKey)
            } else {
                remove(TRACK_MEMORY_SUB_DUPLICATE_INDEX_KEY)
                remove(TRACK_MEMORY_SUB_DUPLICATE_COUNT_KEY)
                remove(TRACK_MEMORY_SUB_SERIES_KEY)
            }
        }
        putString(titleKey, meta.title)
        putString(langKey, meta.lang)
        apply()
    }
}

internal fun MPVActivity.applyRememberedTrack(type: String) {
    if (type == "sub" && !persistSubFilters)
        return

    val prefs = getDefaultSharedPreferences(applicationContext)
    val (titleKey, langKey) = trackMemoryKeys(type)
    if (type == "sub" && prefs.getBoolean(TRACK_MEMORY_SUB_OFF_KEY, false)) {
        selectTrackForFile("sub", -1)
        Log.v(MPV_ACTIVITY_TAG, "track-memory: restored sub track off")
        return
    }

    val savedTitle = prefs.getString(titleKey, null)
    val savedLang = prefs.getString(langKey, "") ?: ""
    val savedForced = type == "sub" && prefs.getBoolean(TRACK_MEMORY_SUB_FORCED_KEY, false)
    val sameSeries = type == "sub" && resolveTrackSeriesKey()?.let {
        it == prefs.getString(TRACK_MEMORY_SUB_SERIES_KEY, null)
    } == true

    if (savedTitle != null) {
        val (match, score) = rememberedTrackMatch(
            listTrackMeta(type), savedTitle, savedLang, type, savedForced,
            duplicateIndex = if (sameSeries) prefs.getInt(TRACK_MEMORY_SUB_DUPLICATE_INDEX_KEY, -1) else -1,
            duplicateCount = if (sameSeries) prefs.getInt(TRACK_MEMORY_SUB_DUPLICATE_COUNT_KEY, 0) else 0,
        )
        if (match != null) {
            setTrackForMemory(type, match.mpvId, match.title, score, exact = match.title.equals(savedTitle, true))
        }
    }
}

internal fun MPVActivity.setTrackForMemory(
    type: String, mpvId: Int, title: String, score: Double, exact: Boolean
) {
    selectTrackForFile(type, mpvId)
    android.util.Log.v(
        MPV_ACTIVITY_TAG,
        "track-memory: restored $type track #$mpvId " +
                "(title='$title', exact=$exact, score=$score)"
    )
}

private fun trackMemoryKeys(type: String): Pair<String, String> = when (type) {
    "sub"   -> "last_user_sub_title" to "last_user_sub_lang"
    "audio" -> "last_user_audio_title" to "last_user_audio_lang"
    else    -> throw IllegalArgumentException("unknown track type: $type")
}

internal fun clearTrackMemory(prefs: SharedPreferences, kind: RememberedTrackKind) {
    val (titleKey, langKey) = trackMemoryKeys(kind.trackType)
    val editor = prefs.edit().remove(titleKey).remove(langKey)
    if (kind == RememberedTrackKind.SUBTITLE) {
        editor.remove(TRACK_MEMORY_SUB_OFF_KEY)
            .remove(TRACK_MEMORY_SUB_FORCED_KEY)
            .remove(TRACK_MEMORY_SUB_DUPLICATE_INDEX_KEY)
            .remove(TRACK_MEMORY_SUB_DUPLICATE_COUNT_KEY)
            .remove(TRACK_MEMORY_SUB_SERIES_KEY)
    }
    val cache = SeriesTrackSelectionCache(prefs.getString(SERIES_TRACK_SELECTIONS_KEY, null))
    cache.clear(kind)
    if (cache.isEmpty()) editor.remove(SERIES_TRACK_SELECTIONS_KEY)
    else editor.putString(SERIES_TRACK_SELECTIONS_KEY, cache.serialize())
    editor.apply()
}

private const val TRACK_MEMORY_SUB_OFF_KEY = "last_user_sub_off"
private const val TRACK_MEMORY_SUB_FORCED_KEY = "last_user_sub_forced"
private const val TRACK_MEMORY_SUB_DUPLICATE_INDEX_KEY = "last_user_sub_duplicate_index"
private const val TRACK_MEMORY_SUB_DUPLICATE_COUNT_KEY = "last_user_sub_duplicate_count"
private const val TRACK_MEMORY_SUB_SERIES_KEY = "last_user_sub_series"
