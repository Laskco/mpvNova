package app.mpvnova.player

internal const val PREF_PRESERVE_COMPANION_SUBTITLE_STYLE = "preserve_companion_subtitle_style"

private fun isFullSubtitleTitle(title: String): Boolean =
    FULL_SUBTITLE_PATTERN.containsMatchIn(title.replace('_', ' '))

internal fun shouldPreserveCompanionSubtitleStyle(enabled: Boolean, track: TrackMeta?): Boolean =
    // Some releases mark full-dialogue tracks forced at the container level.
    enabled && track != null && !isFullSubtitleTitle(track.title) &&
        isCompanionSubtitleTrack(track.title, track.forced, track.lang)

internal fun MPVActivity.shouldPreserveSelectedSubtitleStyle(): Boolean {
    if (!preserveCompanionSubtitleStyle) return false
    val sid = player.sid
    if (sid == -1) return false
    val track = selectedSubtitleTrackMeta(sid) ?: findTrackMeta("sub", sid)
    return shouldPreserveCompanionSubtitleStyle(true, track)
}

private fun selectedSubtitleTrackMeta(sid: Int): TrackMeta? {
    val currentId = mpvGetPropertyInt("current-tracks/sub/id") ?: return null
    if (currentId != sid) return null
    val title = mpvGetPropertyString("current-tracks/sub/title") ?: ""
    val lang = mpvGetPropertyString("current-tracks/sub/lang") ?: ""
    val forced = mpvGetPropertyBoolean("current-tracks/sub/forced") == true
    return TrackMeta(currentId, title, lang, forced)
}

internal fun MPVActivity.refreshCompanionSubtitleStyle() {
    val preserve = shouldPreserveSelectedSubtitleStyle()
    if (preserve != subStylePreservingCompanionTrack)
        applyCustomSubtitleStyle(preserve)
}
