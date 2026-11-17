package app.mpvnova.player

internal fun orderSubtitlePickerTracks(
    tracks: List<MPVView.Track>, primaryId: Int, secondaryId: Int,
): Pair<List<MPVView.Track>, Boolean> {
    var off: MPVView.Track? = null
    var primary: MPVView.Track? = null
    var secondary: MPVView.Track? = null
    val remaining = ArrayList<MPVView.Track>(tracks.size)
    for (track in tracks) {
        val id = track.mpvId
        when {
            id == -1 -> if (off == null) off = track
            id == primaryId || id == secondaryId -> {
                if (id == primaryId && primary == null) primary = track
                if (id == secondaryId && secondary == null) secondary = track
            }
            else -> remaining.add(track)
        }
    }
    val ordered = buildList(tracks.size) {
        off?.let(::add)
        primary?.let(::add)
        secondary?.let(::add)
        addAll(remaining)
    }
    return ordered to (secondary != null)
}
