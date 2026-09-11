package app.mpvnova.player

import java.util.Locale

internal object AnimeReleaseTitle {
    private const val MAX_FILENAME_LENGTH = 1024
    private const val MAX_CACHE_ENTRIES = 32
    private val cache = LinkedHashMap<String, List<Pair<String, String>>>()
    private val episodeMarker = Regex(
        """(?i)(?:[ ._][-\u2012-\u2014][ ._]*\d{1,4}|(?:^|[ ._-])S\d{1,2}E\d{1,3}(?:E\d{1,3})?(?:v\d+)?\b|""" +
            """(?:^|[ ._-])E(?:p(?:isode)?)?[ ._-]*\d{1,4}|\b\d{1,2}x\d{1,3}\b|\u7b2c\d{1,4}\u8a71)""",
    )
    private val extension = Regex("""(?i)\.(mkv|mp4|m4v|webm|avi|mov|ts|m2ts)$""")
    private val numericTitle = Regex("""^(?:18|19|20|21)\d{2}(?=[ ._])""")
    private val numericMovieRelease = Regex(
        """[ ._]((?:18|19|20|21)\d{2})(?=[ ._]+(?:2160p|1080p|720p|480p|UHD|BluRay|WEB-DL|BD)\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val bracketedEpisodeTitle = Regex(
        """^\[([^\]]+)](?=\s*(?:-\s*)?(?:[\[(]\d{1,4}(?:v\d+)?[\])]|""" +
            """(?:E(?:p(?:isode)?)?[ ._-]*)?\d{1,4}(?:v\d+)?(?:\s|\[|$)|\u7b2c\d{1,4}\u8a71))""",
        RegexOption.IGNORE_CASE,
    )
    private val bracketedEpisode = Regex("""[\[(](\d{1,4})(v\d+)?[\])]""", RegexOption.IGNORE_CASE)
    private val titleBlock = Regex("""^\[([^\[\]]+)]""")
    private val releaseEvidence = setOf(
        "release_group", "video_resolution", "video_term", "audio_term", "source", "file_checksum",
    )

    fun parse(candidate: String?): PlayerTitlePresentation? {
        val name = candidate?.takeIf { it.length <= MAX_FILENAME_LENGTH }
            ?.let(VlcTitleResolver::titleSourceFromExtra)?.takeIf(::isReleaseFilename) ?: return null
        return parseRelease(name)
    }

    fun cleanSource(candidate: String?, filename: String?): String? {
        val name = listOfNotNull(candidate, filename).firstOrNull {
            it.length <= MAX_FILENAME_LENGTH && isReleaseFilename(it)
        } ?: return candidate
        val group = elements(name).filter { it.first == "release_group" }.singleOrNull()?.second
        val cleaned = stripReleaseGroupPrefix(candidate, group)
        val packed = packedTitle(name, group)
        return if (packed != null && cleaned?.startsWith("[$packed]") == true) {
            packed + cleaned.removePrefix("[$packed]")
        } else cleaned
    }

    @Synchronized
    private fun elements(name: String): List<Pair<String, String>> {
        if (!cache.containsKey(name)) {
            val result = runCatching { AnitomyNg.parse(normalizePackedRelease(name)) }.getOrDefault(emptyList())
            if (cache.size >= MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
            cache[name] = result
        }
        return cache.getValue(name)
    }

    private fun isReleaseFilename(name: String): Boolean {
        // This is a release-filename fallback, not a parser for ordinary display titles or URLs.
        val isPath = name.contains('/') || name.contains('\\')
        return !isPath && extension.containsMatchIn(name)
    }

    private fun normalizeNumericMovieYear(name: String): String {
        // Give the parser an explicit release year when a numeric movie title resembles a year.
        val releaseYear = numericMovieRelease.find(name)?.groups?.get(1)
            ?.takeIf { numericTitle.containsMatchIn(name) && !episodeMarker.containsMatchIn(name) }
        return releaseYear?.let { name.replaceRange(it.range, "(${it.value})") } ?: name
    }

    private fun parseRelease(name: String): PlayerTitlePresentation? {
        val normalized = normalizeNumericMovieYear(normalizePackedRelease(name))
        val group = elements(normalized).filter { it.first == "release_group" }.singleOrNull()?.second
        val withoutGroup = stripReleaseGroupPrefix(normalized, group) ?: normalized
        val packed = packedTitle(normalized, group)
        // A sole bracketed title before an absolute episode is not a release group.
        val bracketed = findBracketedTitle(withoutGroup, normalized)
        val parseName = bracketed?.let { withoutGroup.replaceRange(it.range, it.groupValues[1]) } ?: normalized
        val elements = elements(normalizeBracketedEpisode(parseName))
        fun single(kind: String) = elements.filter { it.first == kind }.map { it.second }.singleOrNull()
        val episode = single("episode")?.toIntOrNull()
        val title = single("title")?.let(::cleanTitleBrackets)
            ?.takeIf { it.isNotBlank() }
            ?.let { restoreQuotedTitle(normalized, it) }
            ?.let { restoreTitleBrackets(it, bracketed, packed) }
        val season = single("season")?.toIntOrNull()
        val year = single("year")?.takeIf { it.toIntOrNull() != null }
        if (title == null || !hasReliableEpisode(name, elements)) return null
        val titleWithYear = if (year != null && !title.endsWith("($year)")) "$title ($year)" else title
        return PlayerTitlePresentation(
            titleWithYear,
            season,
            episode,
            single("episode_title")?.let(::cleanTitleBrackets)?.takeIf { it.isNotBlank() },
        )
    }

    private fun packedTitle(name: String, group: String?): String? {
        val prefix = group?.let { "[$it]" } ?: return null
        return name.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
            ?.let { titleBlock.find(it)?.groupValues?.get(1) }
    }

    private fun normalizeBracketedEpisode(name: String): String {
        val marker = bracketedEpisode.findAll(name).singleOrNull() ?: return name
        val episodes = elements(name).filter { it.first == "episode" }.map { it.second }
        val number = marker.groupValues[1]
        val episodeStyle = episodes.size == 1 || marker.value.startsWith('[') ||
            (number.length > 1 && number.startsWith('0'))
        val identified = episodeStyle && number in episodes && marker.range.first > 0 &&
            !episodeMarker.containsMatchIn(name.substring(0, marker.range.first))
        // An explicit marker keeps a sequel's title number from becoming a second episode.
        return if (identified) name.replaceRange(marker.range, " E$number${marker.groupValues[2]} ") else name
    }

    private fun findBracketedTitle(name: String, original: String): MatchResult? {
        val marker = bracketedEpisodeTitle.find(name) ?: return null
        val title = elements(original).filter { it.first == "title" }.singleOrNull()?.second
        val remainder = name.substring(marker.range.last + 1).trimStart()
        return marker.takeUnless { name == original && title != null && remainder.startsWith(title) }
    }

    private fun hasReliableEpisode(name: String, elements: List<Pair<String, String>>): Boolean {
        val hasEpisode = elements.any { it.first == "episode" }
        return !hasEpisode || episodeMarker.containsMatchIn(name) ||
            elements.any { it.first in releaseEvidence } || bracketedEpisode.containsMatchIn(name)
    }

    private fun restoreQuotedTitle(name: String, title: String): String {
        val quotePairs = listOf('\u300c' to '\u300d', '\u300e' to '\u300f')
        val unclosed = quotePairs.firstOrNull { (open, close) ->
            title.count { it == open } > title.count { it == close }
        } ?: return title
        val start = name.indexOf(title)
        val end = if (start >= 0) name.indexOf(unclosed.second, start + title.length) else -1
        return if (end >= 0) name.substring(start, end + 1) else title
    }
}

private fun restoreTitleBrackets(title: String, bracketed: MatchResult?, packed: String?): String =
    if (packed == null && bracketed != null && title == bracketed.groupValues[1]) "[$title]" else title

private val PACKED_RELEASE = Regex(
    """^\[([^\[\]]+)]\[([^\[\]]+)]\[(\d{1,4}(?:v\d+)?)\](?=\[)""", RegexOption.IGNORE_CASE,
)

private fun normalizePackedRelease(name: String): String {
    val match = PACKED_RELEASE.find(name)?.takeIf { it.groupValues[1].count(Char::isLetter) >= 2 }
    // Explicit episode syntax prevents numeric titles in packed release blocks becoming episodes.
    return match?.let {
        val (group, title, episode) = it.destructured
        name.replaceRange(it.range, "[$group] $title E$episode ")
    } ?: name
}

// Only remove the parser's identified leading group, not every bracketed part of a title.
internal fun stripReleaseGroupPrefix(candidate: String?, group: String?): String? {
    if (candidate == null || group == null || group.count(Char::isLetter) < 2) return candidate
    val prefix = Regex(
        """^\s*(?:\[${Regex.escape(group)}\]|\(${Regex.escape(group)}\))\s*""", RegexOption.IGNORE_CASE,
    )
    return prefix.find(candidate)?.let { match ->
        val remainder = candidate.substring(match.range.last + 1)
        val marker = RELEASE_EPISODE_MARKER.find(remainder) ?: BARE_EPISODE_MARKER.find(remainder)
        val titlePart = marker?.let { remainder.substring(0, it.range.first) } ?: remainder
        remainder.takeIf { titlePart.any(Char::isLetterOrDigit) }
    } ?: candidate
}

private val RELEASE_EPISODE_MARKER = Regex(
    """(?i)(?:\bS\d{1,2}E\d{1,3}(?:E\d{1,3})?\b|(?:^|\s)-\s*\d{1,4}(?:v\d+)?\b|""" +
        """\bE(?:p(?:isode)?)?[ ._-]*\d{1,4}\b|[\[(]\d{1,4}(?:v\d+)?[\])]|\u7b2c\d{1,4}\u8a71)""",
)
private val BARE_EPISODE_MARKER = Regex("""(?i)^\s*\d{1,4}(?:v\d+)?(?=$|\s|\[)""")

internal fun sameSeriesTitle(first: String, second: String): Boolean =
    titleIdentity(first) == titleIdentity(second)

private fun titleIdentity(value: String): String = cleanTitleBrackets(value)
    .filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
