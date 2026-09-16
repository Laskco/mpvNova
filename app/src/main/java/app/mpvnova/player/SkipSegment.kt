package app.mpvnova.player

private const val MOVIE_SEEK_END_GUARD_SEC = 0.001

/** A supplied skippable segment. Times are in seconds. */
internal data class SkipSegment(val type: String, val start: Double, val end: Double)

internal fun skipSegmentType(type: String): String {
    val normalized = type.trim().lowercase(java.util.Locale.ROOT).replace('_', '-')
    return if (normalized == "movie-credits") "end-credits" else normalized
}

internal fun normalizedSkipSegments(segments: List<SkipSegment>): List<SkipSegment> {
    val valid = segments.filter { it.start.isFinite() && it.end.isFinite() && it.start >= 0 && it.end > it.start }
    return valid.mapNotNull { segment ->
        // Credits must never swallow a post-credits scene, even if the source ranges overlap.
        val sceneStart = if (segment.type == "end-credits") {
            valid.filter { it.type == "post-credits" && it.end > segment.start && it.start < segment.end }
                .minOfOrNull { it.start }
        } else null
        val end = sceneStart?.let { minOf(segment.end, it) } ?: segment.end
        segment.copy(end = end).takeIf { end > segment.start }
    }.sortedBy { it.start }
}

internal fun SkipSegment.mode(modes: Map<SkipSegmentKind, SkipSegmentsMode>): SkipSegmentsMode =
    modes[kind] ?: kind.defaultMode

internal fun SkipSegment.seekTarget(durationSec: Double): Double =
    if (type in setOf("end-credits", "post-credits") && durationSec.isFinite() && durationSec > 0) {
        minOf(end, (durationSec - MOVIE_SEEK_END_GUARD_SEC).coerceAtLeast(0.0))
    } else end
