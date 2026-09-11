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

internal fun MPVActivity.listTrackMeta(type: String): List<TrackMeta> {
    val count = mpvGetPropertyInt("track-list/count") ?: return emptyList()
    return (0 until count).mapNotNull { index ->
        if (mpvGetPropertyString("track-list/$index/type") == type) {
            val id = mpvGetPropertyInt("track-list/$index/id")
            val title = mpvGetPropertyString("track-list/$index/title") ?: ""
            val lang = mpvGetPropertyString("track-list/$index/lang") ?: ""
            val forced = mpvGetPropertyBoolean("track-list/$index/forced") == true
            id?.let { TrackMeta(it, title, lang, forced) }
        } else {
            null
        }
    }
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
    val meta = listTrackMeta(type).firstOrNull { it.mpvId == mpvId }
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

    if (savedTitle != null) {
        val (match, score) = rememberedTrackMatch(listTrackMeta(type), savedTitle, savedLang, type, savedForced)
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

internal fun MPVActivity.trackMemoryKeys(type: String): Pair<String, String> = when (type) {
    "sub"   -> "last_user_sub_title" to "last_user_sub_lang"
    "audio" -> "last_user_audio_title" to "last_user_audio_lang"
    else    -> throw IllegalArgumentException("unknown track type: $type")
}

private const val TRACK_MEMORY_SUB_OFF_KEY = "last_user_sub_off"
private const val TRACK_MEMORY_SUB_FORCED_KEY = "last_user_sub_forced"
