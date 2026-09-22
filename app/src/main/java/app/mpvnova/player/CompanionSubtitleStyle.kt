package app.mpvnova.player

internal const val PREF_PRESERVE_COMPANION_SUBTITLE_STYLE = "preserve_companion_subtitle_style"

internal fun shouldPreserveCompanionSubtitleStyle(enabled: Boolean, track: TrackMeta?): Boolean =
    enabled && track != null && isCompanionSubtitleTrack(track.title, track.forced, track.lang)
