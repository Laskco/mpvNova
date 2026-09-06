package app.mpvnova.player

import java.util.Locale

internal object AnimeReleaseTitle {
    private const val MAX_FILENAME_LENGTH = 1024
    private const val MAX_CACHE_ENTRIES = 32
    private val cache = LinkedHashMap<String, PlayerTitlePresentation?>()
    private val episodeMarker = Regex("""(?i)(?:\s-\s*\d{1,4}|\bS\d{1,2}E\d{1,3}\b|\bEp(?:isode)?[ ._-]*\d{1,4})""")
    private val extension = Regex("""(?i)\.(mkv|mp4|m4v|webm|avi|mov|ts|m2ts)$""")

    @Synchronized
    fun parse(candidate: String?): PlayerTitlePresentation? {
        val name = candidate?.takeIf { it.length <= MAX_FILENAME_LENGTH }
            ?.let(VlcTitleResolver::titleSourceFromExtra)?.takeIf(::isReleaseFilename) ?: return null
        if (!cache.containsKey(name)) {
            val result = runCatching { parseRelease(name) }.getOrNull()
            if (cache.size >= MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
            cache[name] = result
        }
        return cache[name]
    }

    private fun isReleaseFilename(name: String): Boolean {
        // This is a release-filename fallback, not a parser for ordinary display titles or URLs.
        val isPath = name.contains('/') || name.contains('\\')
        return !isPath && extension.containsMatchIn(name) && episodeMarker.containsMatchIn(name)
    }

    private fun parseRelease(name: String): PlayerTitlePresentation? {
        val elements = AnitomyNg.parse(name)
        fun single(kind: String) = elements.filter { it.first == kind }.map { it.second }.singleOrNull()
        val episode = single("episode")?.toIntOrNull()
        val title = single("title")?.let(::cleanTitleBrackets)
            ?.takeIf { it.isNotBlank() }
        if (episode == null || title == null) return null
        val season = single("season")?.toIntOrNull()
        val year = single("year")?.takeIf { it.toIntOrNull() != null }
        val titleWithYear = if (year != null && !title.contains(year)) "$title ($year)" else title
        return PlayerTitlePresentation(
            titleWithYear,
            season,
            episode,
            single("episode_title")?.let(::cleanTitleBrackets)?.takeIf { it.isNotBlank() },
        )
    }
}

internal fun sameSeriesTitle(first: String, second: String): Boolean =
    titleIdentity(first) == titleIdentity(second)

private fun titleIdentity(value: String): String = cleanTitleBrackets(value)
    .filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
