package app.mpvnova.player

import android.util.Log

internal fun MPVView.addObserver(observer: MpvEventObserver) {
    addMpvObserver(observer)
}

internal fun MPVView.removeObserver(observer: MpvEventObserver) {
    removeMpvObserver(observer)
}

internal fun MPVView.loadTracks() {
    for (list in tracks.values) {
        list.clear()
        list.add(MPVView.Track(-1, context.getString(R.string.track_off)))
    }
    val count = mpvGetPropertyInt("track-list/count")!!
    // Note that because events are async, properties might disappear at any moment
    // so skip incomplete entries instead of asserting them.
    for (index in 0 until count) {
        trackFromPropertyIndex(index)?.let { (type, track) ->
            tracks.getValue(type).add(track)
        }
    }
}

private fun MPVView.trackFromPropertyIndex(index: Int): Pair<String, MPVView.Track>? {
    val type = mpvGetPropertyString("track-list/$index/type")
    return when {
        type == null -> null
        !tracks.containsKey(type) -> {
            Log.w(MPV_VIEW_LOG_TAG, "Got unknown track type: $type")
            null
        }
        else -> {
            val mpvId = mpvGetPropertyInt("track-list/$index/id")
            mpvId?.let { type to MPVView.Track(mpvId = it, name = trackName(index, it)) }
        }
    }
}

private fun MPVView.trackName(index: Int, mpvId: Int): String {
    val lang = mpvGetPropertyString("track-list/$index/lang")
    val title = mpvGetPropertyString("track-list/$index/title")
    return if (!lang.isNullOrEmpty() && !title.isNullOrEmpty())
        context.getString(R.string.ui_track_title_lang, mpvId, title, lang)
    else if (!lang.isNullOrEmpty() || !title.isNullOrEmpty())
        context.getString(R.string.ui_track_text, mpvId, (lang ?: "") + (title ?: ""))
    else
        context.getString(R.string.ui_track, mpvId)
}

internal fun MPVView.loadPlaylist(): MutableList<MPVView.PlaylistItem> {
    val playlist = mutableListOf<MPVView.PlaylistItem>()
    val count = mpvGetPropertyInt("playlist-count")!!
    for (index in 0 until count) {
        val filename = mpvGetPropertyString("playlist/$index/filename")!!
        val title = mpvGetPropertyString("playlist/$index/title")
        playlist.add(MPVView.PlaylistItem(index = index, filename = filename, title = title))
    }
    return playlist
}

internal fun MPVView.loadChapters(): MutableList<MPVView.Chapter> {
    val chapters = mutableListOf<MPVView.Chapter>()
    val count = mpvGetPropertyInt("chapter-list/count")!!
    for (index in 0 until count) {
        val title = mpvGetPropertyString("chapter-list/$index/title")
        val time = mpvGetPropertyDouble("chapter-list/$index/time")!!
        chapters.add(MPVView.Chapter(index = index, title = title, time = time))
    }
    return chapters
}
