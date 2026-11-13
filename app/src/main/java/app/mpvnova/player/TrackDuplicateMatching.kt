package app.mpvnova.player

internal fun matchingTrackDuplicates(
    tracks: List<TrackMeta>, title: String, language: String, forced: Boolean,
): List<TrackMeta> = tracks.filter {
    it.title.equals(title, ignoreCase = true) && it.lang.equals(language, ignoreCase = true) && it.forced == forced
}

internal fun rememberedDuplicateTrack(
    tracks: List<TrackMeta>, title: String, language: String, forced: Boolean,
    index: Int, count: Int,
): TrackMeta? {
    if (count < 2 || index !in 0 until count) return null
    val duplicates = matchingTrackDuplicates(tracks, title, language, forced)
    // An ordinal is meaningful only within an unchanged group of indistinguishable tracks.
    return duplicates.takeIf { it.size == count }?.getOrNull(index)
}
