package app.mpvnova.player

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

internal fun findTrackMeta(type: String, id: Int): TrackMeta? {
    val count = if (id == -1) 0 else mpvGetPropertyInt("track-list/count") ?: 0
    return (0 until count).firstNotNullOfOrNull { index ->
        val prefix = "track-list/$index"
        if (mpvGetPropertyString("$prefix/type") == type && mpvGetPropertyInt("$prefix/id") == id) {
            readTrackMeta(prefix, id)
        } else null
    }
}

private inline fun forEachTrackMeta(
    accepts: (String) -> Boolean,
    onTrack: (String, TrackMeta) -> Unit,
) {
    val count = mpvGetPropertyInt("track-list/count") ?: return
    for (index in 0 until count) {
        val prefix = "track-list/$index"
        val type = mpvGetPropertyString("$prefix/type")?.takeIf(accepts) ?: continue
        mpvGetPropertyInt("$prefix/id")?.let { id -> onTrack(type, readTrackMeta(prefix, id)) }
    }
}

private fun readTrackMeta(prefix: String, id: Int): TrackMeta {
    val title = mpvGetPropertyString("$prefix/title") ?: ""
    val lang = mpvGetPropertyString("$prefix/lang") ?: ""
    val forced = mpvGetPropertyBoolean("$prefix/forced") == true
    return TrackMeta(id, title, lang, forced)
}
