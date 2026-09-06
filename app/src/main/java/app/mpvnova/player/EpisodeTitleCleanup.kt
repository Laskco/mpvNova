package app.mpvnova.player

import java.util.Locale

internal fun cleanEpisodeTitle(presentation: PlayerTitlePresentation): PlayerTitlePresentation {
    val value = presentation.episodeTitle?.trim() ?: return presentation
    val cleaned = EpisodeTitlePrefix.remove(value, presentation)
    return if (cleaned == presentation.episodeTitle) presentation else presentation.copy(episodeTitle = cleaned)
}

private object EpisodeTitlePrefix {
    private val seasonEpisode = Regex(
        """^(.*?)\b(?:Season\s+(\d+)\s*[/|:\-]?\s*Episode\s+(\d+)|S(\d+)E(\d+))\s*[-:]\s*(.+)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val episodeOnly = Regex(
        """^(.*?)\bEpisode\s+(\d+)\s*[-:]\s*(.+)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")

    @Suppress("MagicNumber") // Episode-only capture groups: prefix, episode, title.
    fun remove(value: String, presentation: PlayerTitlePresentation): String {
        val full = seasonEpisode.matchEntire(value)
        val episode = episodeOnly.matchEntire(value)
        return when {
            full != null -> removeFullPrefix(value, full, presentation)
            episode != null && episode.groupValues[2].toIntOrNull() == presentation.episode &&
                matchesTitle(episode.groupValues[1], presentation.title) -> episode.groupValues[3].trim()
            else -> value
        }
    }

    @Suppress("MagicNumber") // Capture groups: prefix, long season/episode, compact season/episode, title.
    private fun removeFullPrefix(
        original: String, match: MatchResult, presentation: PlayerTitlePresentation,
    ): String {
        val season = match.groupValues[2].toIntOrNull() ?: match.groupValues[4].toIntOrNull()
        val episode = match.groupValues[3].toIntOrNull() ?: match.groupValues[5].toIntOrNull()
        val sameEpisode = season == presentation.season && episode == presentation.episode
        return if (sameEpisode && matchesTitle(match.groupValues[1], presentation.title)) {
            match.groupValues[6].trim()
        } else original
    }

    private fun matchesTitle(prefix: String, title: String): Boolean {
        val prefixWords = words(prefix)
        val titleWords = words(title)
        // Permit a leading qualifier (e.g. "Agent") only at a complete word boundary.
        return prefixWords.isEmpty() || prefixWords == titleWords ||
            (titleWords.isNotEmpty() && prefixWords.endsWith(" $titleWords"))
    }

    private fun words(value: String): String = value.lowercase(Locale.ROOT).replace(wordSeparators, " ").trim()
}
