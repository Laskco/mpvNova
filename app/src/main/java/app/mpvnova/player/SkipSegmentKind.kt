package app.mpvnova.player

internal enum class SkipSegmentKind(val preferenceKey: String, val defaultMode: SkipSegmentsMode) {
    INTRO("intro_skip_mode", SkipSegmentsMode.AUTO),
    OUTRO("outro_skip_mode", SkipSegmentsMode.AUTO),
    RECAP("recap_skip_mode", SkipSegmentsMode.AUTO),
    END_CREDITS("movie_credits_mode", SkipSegmentsMode.BUTTON),
    POST_CREDITS("post_credits_mode", SkipSegmentsMode.BUTTON),
}

internal fun readSegmentSkipModes(values: Map<String, *>): Map<SkipSegmentKind, SkipSegmentsMode> {
    val legacyMode = (values["skip_segments_mode"] as? String)?.let(SkipSegmentsMode::fromPref)
        ?: if (values["auto_skip_segments"] == false) SkipSegmentsMode.OFF else SkipSegmentsMode.AUTO
    return SkipSegmentKind.entries.associateWith { kind ->
        val fallback = when (kind) {
            SkipSegmentKind.INTRO, SkipSegmentKind.OUTRO, SkipSegmentKind.RECAP -> legacyMode
            else -> kind.defaultMode
        }
        when (val value = values[kind.preferenceKey] as? String) {
            "off", "auto", "button" -> SkipSegmentsMode.fromPref(value)
            else -> fallback
        }
    }
}

internal val SkipSegment.kind: SkipSegmentKind
    get() = when (type) {
        "recap" -> SkipSegmentKind.RECAP
        "outro", "ed", "ending", "credits", "mixed-ed" -> SkipSegmentKind.OUTRO
        "end-credits" -> SkipSegmentKind.END_CREDITS
        "post-credits" -> SkipSegmentKind.POST_CREDITS
        else -> SkipSegmentKind.INTRO
    }

internal val SkipSegmentKind.titleRes: Int
    get() = when (this) {
        SkipSegmentKind.INTRO -> R.string.skip_segment_intro
        SkipSegmentKind.OUTRO -> R.string.skip_segment_outro
        SkipSegmentKind.RECAP -> R.string.skip_segment_recap
        SkipSegmentKind.END_CREDITS -> R.string.skip_segment_movie_credits
        SkipSegmentKind.POST_CREDITS -> R.string.skip_segment_post_credits
    }
