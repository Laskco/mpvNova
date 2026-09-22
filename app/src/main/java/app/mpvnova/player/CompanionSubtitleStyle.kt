package app.mpvnova.player

internal const val PREF_PRESERVE_COMPANION_SUBTITLE_STYLE = "preserve_companion_subtitle_style"

internal fun shouldPreserveCompanionSubtitleStyle(enabled: Boolean, track: TrackMeta?): Boolean =
    enabled && track != null && isCompanionSubtitleTrack(track.title, track.forced, track.lang)

internal fun MPVActivity.shouldPreserveSelectedSubtitleStyle(): Boolean {
    if (!preserveCompanionSubtitleStyle) return false
    val selectedId = player.sid
    return shouldPreserveCompanionSubtitleStyle(
        true, listTrackMeta("sub").firstOrNull { it.mpvId == selectedId },
    )
}

internal fun MPVActivity.refreshCompanionSubtitleStyle() {
    if (shouldPreserveSelectedSubtitleStyle() != subStylePreservingCompanionTrack)
        applyCustomSubtitleStyle()
}
