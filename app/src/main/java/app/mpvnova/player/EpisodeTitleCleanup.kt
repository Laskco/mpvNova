package app.mpvnova.player

internal fun cleanEpisodeTitle(presentation: PlayerTitlePresentation): PlayerTitlePresentation {
    val value = VlcTitleResolver.titleSourceFromExtra(presentation.episodeTitle)
        ?: return presentation.copy(episodeTitle = null)
    val cleaned = VlcTitleResolver.titleSourceFromExtra(EpisodeTitlePrefix.remove(value, presentation))
    return if (cleaned == presentation.episodeTitle) presentation else presentation.copy(episodeTitle = cleaned)
}

private object EpisodeTitlePrefix {
    private const val MAX_TITLE_LENGTH = 1024
    private const val GAP = "[\\s\\p{Zs}._\\p{Pd}/|:]*"
    private const val START = "(?<![\\p{L}\\p{N}])"
    private const val NUMBER = "([0-9]{1,4})(?:[._]?v[0-9]+(?:\\.[0-9]+)?)?"
    private val seasonMarkers = listOf(
        Regex("${START}S([0-9]{1,4})${GAP}x?E$GAP$NUMBER", RegexOption.IGNORE_CASE),
        Regex("${START}Season$GAP([0-9]{1,4})$GAP(?:Episode|Ep|E)$GAP$NUMBER", RegexOption.IGNORE_CASE),
        Regex("$START([0-9]{1,4})[xX]$NUMBER"),
    )
    private val episodeMarker = Regex("$START(?:Episode|Ep|E)$GAP$NUMBER", RegexOption.IGNORE_CASE)
    private val bareMarker = Regex("$START$NUMBER", RegexOption.IGNORE_CASE)
    private val markers = seasonMarkers + episodeMarker + bareMarker
    private const val PREFIX_SEPARATORS = " ._-:/|[]{}\u2010\u2011\u2012\u2013\u2014\u2212"
    private const val SUFFIX_SEPARATORS = " ._-:/|\u2010\u2011\u2012\u2013\u2014\u2212"
    private val leadingGroup = Regex("""^\[[^\[\]]+\]\s*""")
    private val seriesBoundary = Regex("""\s+[-\p{Pd}:|/]\s+|[._]""")
    private val partialEpisode = Regex("""^[\p{L}\p{N}]|^\.[0-9]+(?=$|[\p{Pd}:.\[\]]|\s+[-:\[\]])""")
    private val episodeRange = Regex(
        """(?i)^(?:(?:\s*(?:[-\p{Pd}~+&,/]|to\b)\s*|\s+)(?:S[0-9]+[ ._-]*)?E(?:p(?:isode)?)?[ ._-]*[0-9]|""" +
            """[-~+&][0-9]|\s*(?:[-\p{Pd}~+&,/]|to\b)\s+[0-9]+(?:\s+[-:]|$))""",
    )

    @Suppress("ReturnCount") // Unknown identity and conflicting markers must leave the original text untouched.
    fun remove(value: String, presentation: PlayerTitlePresentation): String? {
        if (presentation.episode == null || value.length > MAX_TITLE_LENGTH) return value
        // A marked season/episode takes precedence over a bare episode number or release year.
        for (pattern in markers) {
            val bare = pattern === bareMarker
            val match = pattern.findAll(value).firstOrNull {
                val reversed = bare && it.range.first == 0 && removeSeriesPrefix(
                    removeDelimiter(value.substring(it.range.last + 1)), presentation.title,
                ) != null
                matchesTitle(value.substring(0, it.range.first), presentation.title, bare) ||
                    reversed
            } ?: continue
            val hasSeason = pattern in seasonMarkers
            val season = if (hasSeason) match.groupValues[1].toIntOrNull() else null
            val episode = match.groupValues[if (hasSeason) 2 else 1].toIntOrNull()
            if (episode != presentation.episode ||
                (season != null && season != presentation.season)
            ) return value
            return remainingTitle(value, match, presentation.title)
        }
        return value
    }

    private fun remainingTitle(original: String, match: MatchResult, series: String): String? {
        var suffix = original.substring(match.range.last + 1)
        // Do not turn ranges, fractional episodes, or malformed markers into a single episode title.
        if (partialEpisode.containsMatchIn(suffix)) return original
        val opening = original.substring(0, match.range.first).trimEnd().lastOrNull()
        val closing = when (opening) { '[' -> ']'; '(' -> ')'; '{' -> '}'; else -> null }
        if (closing != null && suffix.trimStart().startsWith(closing)) {
            suffix = suffix.trimStart().drop(1)
        }
        val title = removeDelimiter(suffix)
        return if (episodeRange.containsMatchIn(suffix)) original
        else (removeSeriesPrefix(title, series) ?: title).ifBlank { null }
    }

    private fun removeDelimiter(value: String): String {
        val trimmed = value.trim()
        // Consume the boundary, not punctuation belonging to the title (e.g. ": ...And Then").
        return if (trimmed.isNotEmpty() && trimmed.first() in SUFFIX_SEPARATORS) trimmed.drop(1).trim() else trimmed
    }

    private fun removeSeriesPrefix(value: String, series: String): String? =
        seriesBoundary.findAll(value).firstNotNullOfOrNull { boundary ->
            if (sameSeriesTitle(value.substring(0, boundary.range.first), series)) {
                value.substring(boundary.range.last + 1).trim().takeIf { it.isNotEmpty() }
            } else null
        }

    private fun matchesTitle(prefix: String, title: String, bare: Boolean): Boolean {
        val candidate = prefix.trim { it.isWhitespace() || it in PREFIX_SEPARATORS || it == '(' }
        if (candidate.isEmpty()) return !bare
        val series = title.trim { it.isWhitespace() || it in PREFIX_SEPARATORS }
        return matchesGroupedTitle(prefix, series) ||
            if (bare) sameSeriesTitle(candidate, series) else compatibleEpisodeSeries(series, candidate)
    }

    private fun matchesGroupedTitle(prefix: String, series: String): Boolean {
        // Strip bracketed group prefixes only while checking against the known series identity.
        var ungrouped = prefix.trimStart()
        while (leadingGroup.containsMatchIn(ungrouped)) {
            ungrouped = ungrouped.replaceFirst(leadingGroup, "")
            val remainder = ungrouped.trim { it.isWhitespace() || it in PREFIX_SEPARATORS }
            if (sameSeriesTitle(remainder, series)) return true
        }
        return false
    }
}
