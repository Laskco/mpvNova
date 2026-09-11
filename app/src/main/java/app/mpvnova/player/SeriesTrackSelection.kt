package app.mpvnova.player

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal data class SavedSeriesTrack(
    val title: String,
    val language: String,
    val forced: Boolean = false,
    val off: Boolean = false,
) {
    fun match(tracks: List<TrackMeta>, type: String): Int? {
        if (off) return -1
        val compatible = tracks.filter {
            (language.isBlank() || memoryLanguageMatches(it, type)) &&
                (type != "sub" || subtitleTrackKindsMatch(title, it.title, forced, it.forced, language, it.lang))
        }
        val exact = compatible.firstOrNull { it.title.equals(title, ignoreCase = true) }
        val scored = bestTrackTitleMatch(compatible, title, type, forced, language)
            .takeIf { it.second >= TRACK_MEMORY_MIN_SCORE }?.first
        // Keep language and subtitle kind when releases rename their tracks. Never use old IDs.
        return (exact ?: scored ?: compatible.singleOrNull())?.mpvId
    }

    private fun memoryLanguageMatches(track: TrackMeta, type: String): Boolean {
        // Some English companion releases use enm (Middle English). Limit this repair to
        // explicitly identified companion subtitles, never audio or full dialogue tracks.
        val limited = type == "sub" && isLimitedSubtitleTrack(title, forced) &&
            isLimitedSubtitleTrack(track.title, track.forced)
        fun normalized(value: String): String =
            if (limited && value.equals("enm", ignoreCase = true)) "eng" else value
        return subtitleLanguageMatches(normalized(language), normalized(track.lang))
    }
}

internal data class SeriesTrackSelection(val audio: SavedSeriesTrack?, val sub: SavedSeriesTrack?) {
    fun resolve(audioTracks: List<TrackMeta>, subTracks: List<TrackMeta>): Pair<Int?, Int?> {
        val audioId = audio?.match(audioTracks, "audio")
        // A saved full-subtitle choice belongs to its audio choice, not a replacement language.
        val subId = sub?.takeIf { audio == null || audioId != null }?.match(subTracks, "sub")
        return audioId to subId
    }
}

internal fun savedSeriesTrack(tracks: List<TrackMeta>, id: Int): SavedSeriesTrack? =
    if (id == -1) SavedSeriesTrack("", "", off = true) else tracks.firstOrNull { it.mpvId == id }?.let {
        SavedSeriesTrack(it.title.take(SERIES_TRACK_TEXT_LIMIT), it.lang.take(SERIES_TRACK_TEXT_LIMIT), it.forced)
    }

internal fun trackSeriesKey(presentation: PlayerTitlePresentation?): String? = presentation
    ?.takeIf { it.episode != null }
    ?.title?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
    ?.lowercase(Locale.ROOT)?.replace(Regex("\\s+"), " ")?.trim()
    ?.takeIf { it.isNotEmpty() && it.length <= SERIES_TRACK_TEXT_LIMIT }

// In insertion order, newest last. The cache is bounded and independent of global filter settings.
internal class SeriesTrackSelectionCache(serialized: String?) {
    private val entries = linkedMapOf<String, SeriesTrackSelection>()

    init {
        try {
            val rows = JSONArray(serialized?.takeIf { it.length <= SERIES_TRACK_CACHE_TEXT_LIMIT } ?: "[]")
            for (index in maxOf(0, rows.length() - SERIES_TRACK_CACHE_LIMIT) until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val key = row.optString("series")
                if (key.isNotBlank() && key.length <= SERIES_TRACK_TEXT_LIMIT) {
                    entries[key] = SeriesTrackSelection(
                        readTrack(row.optJSONObject("audio")), readTrack(row.optJSONObject("sub")),
                    )
                }
            }
        } catch (_: JSONException) {
            entries.clear()
        }
    }

    fun get(key: String): SeriesTrackSelection? = entries[key]

    fun put(key: String, selection: SeriesTrackSelection) {
        if (key.isBlank() || key.length > SERIES_TRACK_TEXT_LIMIT) return
        entries.remove(key)
        entries[key] = selection
        while (entries.size > SERIES_TRACK_CACHE_LIMIT) entries.remove(entries.keys.first())
    }

    fun serialize(): String = JSONArray().apply {
        entries.forEach { (key, selection) ->
            put(JSONObject().put("series", key)
                .put("audio", writeTrack(selection.audio)).put("sub", writeTrack(selection.sub)))
        }
    }.toString()

    private fun readTrack(json: JSONObject?): SavedSeriesTrack? = json?.let {
        SavedSeriesTrack(
            it.optString("title").take(SERIES_TRACK_TEXT_LIMIT),
            it.optString("language").take(SERIES_TRACK_TEXT_LIMIT),
            it.optBoolean("forced"), it.optBoolean("off"),
        )
    }

    private fun writeTrack(track: SavedSeriesTrack?): JSONObject? = track?.let {
        JSONObject().put("title", it.title).put("language", it.language).put("forced", it.forced).put("off", it.off)
    }
}

private const val SERIES_TRACK_CACHE_LIMIT = 200
private const val SERIES_TRACK_TEXT_LIMIT = 512
private const val SERIES_TRACK_CACHE_TEXT_LIMIT = 2_000_000
