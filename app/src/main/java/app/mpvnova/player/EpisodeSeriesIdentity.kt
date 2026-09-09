package app.mpvnova.player

import java.util.Locale

// Only used between metadata sources for the same file and explicit season/episode.
// This does not relax TMDB's matching or guess aliases for database searches.
internal fun compatibleEpisodeSeries(primary: String, candidate: String): Boolean {
    val primaryYear = SERIES_YEAR.find(primary)?.groupValues?.get(1)
    val candidateYear = SERIES_YEAR.find(candidate)?.groupValues?.get(1)
    if (primaryYear != null && candidateYear != null && primaryYear != candidateYear) return false
    return sameSeriesTitle(primary.replace(SERIES_YEAR, ""), candidate.replace(SERIES_YEAR, "")) ||
        isExpandedEpisodeSeries(primary, candidate) || isExpandedEpisodeSeries(candidate, primary)
}

internal fun isExpandedEpisodeSeries(primary: String, candidate: String): Boolean {
    val primaryWords = seriesWords(primary)
    val candidateWords = seriesWords(candidate)
    // A bracketed release group is not a longer series name.
    return candidate.none { it == '[' || it == ']' } && primaryWords.isNotEmpty() &&
        candidateWords.endsWith(" $primaryWords")
}

private fun seriesWords(value: String): String = cleanTitleBrackets(value)
    .replace(SERIES_YEAR, "").lowercase(Locale.ROOT).replace(SERIES_SEPARATORS, " ").trim()

private val SERIES_YEAR = Regex("""\s*\((\d{4})\)$""")
private val SERIES_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
