package app.mpvnova.player

/**
 * Pure string-matching helpers used by the track-memory subsystem to find
 * the closest "looks like the one I picked last time" audio / subtitle
 * track on the next file load.
 *
 * Kept as top-level functions (rather than MPVActivity extensions) so they
 * can be exercised by JVM unit tests without an Activity / Android context.
 */

internal val trackTitleStopwords = setOf(
    "the", "and", "of", "a", "an", "or", "to", "for"
)

/**
 * Lowercase, strip everything that isn't a letter or digit, split on
 * whitespace, and drop tokens that are either too short to be informative
 * or in the stop-word list. The resulting set is what
 * [titleScoreFromTokens] compares for "did the user pick this kind of
 * track" without being fooled by punctuation or capitalisation drift
 * between release groups.
 */
internal fun normalizeTrackTitleTokens(title: String): Set<String> {
    if (title.isEmpty()) return emptySet()
    return title
        .lowercase()
        .replace(TITLE_NON_ALNUM_REGEX, " ")
        .split(WHITESPACE_REGEX)
        .filter { it.length > 2 && it !in trackTitleStopwords }
        .toSet()
}

/**
 * Jaccard-like recall over normalized tokens of `saved` and `candidate`.
 * Returns the fraction of the saved track's tokens that also appear in
 * the candidate. 0.0 means none; 1.0 means every token in the saved title
 * is present in the candidate's. Asymmetric on purpose — "Signs & Songs"
 * should match a candidate "[Group] Signs & Songs (en)" cleanly even
 * though the candidate has extra release-group cruft.
 */
internal fun titleSimilarityScore(saved: String, candidate: String): Double {
    val savedTokens = normalizeTrackTitleTokens(saved)
    val candidateTokens = normalizeTrackTitleTokens(candidate)
    if (savedTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
    val common = savedTokens.intersect(candidateTokens).size
    return common.toDouble() / savedTokens.size.toDouble()
}

internal fun bestTrackTitleMatch(
    tracks: List<TrackMeta>, savedTitle: String, type: String,
): Pair<TrackMeta?, Double> {
    var bestMatch: TrackMeta? = null
    var bestScore = 0.0
    tracks.forEach { track ->
        val score = if (type == "sub") subtitleTitleSimilarityScore(savedTitle, track.title)
            else titleSimilarityScore(savedTitle, track.title)
        if (score > bestScore) {
            bestScore = score
            bestMatch = track
        }
    }
    return bestMatch to bestScore
}

internal fun subtitleTitleSimilarityScore(saved: String, candidate: String): Double {
    val savedSigns = isSignsSubtitleTitle(saved)
    val candidateSigns = isSignsSubtitleTitle(candidate)
    // Shared language/source/group words must not turn a Signs choice into full dialogue (or vice versa).
    return when {
        savedSigns != candidateSigns -> 0.0
        savedSigns -> TRACK_MEMORY_MIN_SCORE +
            (1.0 - TRACK_MEMORY_MIN_SCORE) * titleSimilarityScore(saved, candidate)
        else -> titleSimilarityScore(saved, candidate)
    }
}

private fun isSignsSubtitleTitle(title: String): Boolean =
    SIGNS_SUBTITLE_PATTERN.containsMatchIn(title) && !FULL_SUBTITLE_PATTERN.containsMatchIn(title)

private val SIGNS_SUBTITLE_PATTERN = Regex(
    """(?i)\bsigns\b|\bsign\s*(?:[&+/]|and)?\s*songs?\b|\bsign\s+only\b|\bs\s*[&+/]\s*s\b""",
)
private val FULL_SUBTITLE_PATTERN = Regex("""(?i)\b(?:full|dialog(?:ue)?s?|sdh|closed\s+captions?)\b""")

/**
 * Two language tags agree if the first two letters match case-insensitively.
 * That's coarse enough to fold "en" / "eng" / "english" together — which
 * is the actual goal, since release labelling is inconsistent — without
 * making "en" and "es" or "ja" and "jp" collide.
 */
internal fun languagePrefixMatches(a: String, b: String): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    return a.lowercase().take(2) == b.lowercase().take(2)
}
