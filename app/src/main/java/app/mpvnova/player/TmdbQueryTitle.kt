package app.mpvnova.player

import java.text.Normalizer

/** Query construction for already-cleaned presentation titles, never raw paths or release filenames. */
internal object TmdbQueryTitle {
    private const val MAX_QUERY_LENGTH = 256
    private const val MIN_RELEASE_YEAR = 1800
    private const val MAX_RELEASE_YEAR = 2199
    private val trailingYear = Regex(""" +\(([0-9]{4})\)$""")
    private val spaces = Regex("""[\s\p{Z}]+""")
    private val encodedByte = Regex("""%[0-9a-fA-F]{2}""")
    private val uri = Regex(
        """(?i)(?:[a-z][a-z0-9+.-]*:/|\bwww\.|^(?:magnet|mailto|data|urn|file|content):|^[a-z]:\S)""",
    )
    private val mediaExtension = Regex("""(?i)\.(?:mkv|mp4|m4v|webm|avi|mov|ts|m2ts)(?:$|[ ?#])""")
    private val releaseMetadata = Regex(
        """(?i)(?:^|[ ._\[(-])(?:2160p|1080p|720p|480p|x264|x265|h[ ._-]?26[45]|""" +
            """HEVC|WEB[ ._-]?DL|WEBRip|Blu[ ._-]?Ray|BDRip|REMUX)(?=$|[ ._\])\-])""",
    )
    private val seasonSuffix = Regex(
        """(?i)(?:^|[ ._\[(-])(?:S[0-9]{1,4}(?:E[0-9]{1,4})?|""" +
            """Season\s+[0-9]{1,4}|[0-9]{1,4}(?:st|nd|rd|th)\s+Season)\s*[\])]?$""",
    )

    @Suppress("ReturnCount") // Ambiguous identities must retain the local presentation without a lookup.
    fun from(local: PlayerTitlePresentation, filename: String? = null): TmdbTitleQuery? {
        val title = normalize(local.title) ?: return null
        val season = local.season
        val episode = local.episode
        val isEpisode = season != null || episode != null
        val completeEpisode = season?.let { it >= 0 } == true && episode?.let { it > 0 } == true
        if (isEpisode && !completeEpisode) return null

        val yearMatch = trailingYear.find(title)
        val explicitYear = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        // Keep the same bounded year vocabulary as TmdbFilenameYear; never turn (0002) into a year.
        if (explicitYear != null && explicitYear !in MIN_RELEASE_YEAR..MAX_RELEASE_YEAR) return null
        val name = yearMatch?.let { title.substring(0, it.range.first).trim() } ?: title
        if (!isSearchName(name)) return null

        // Preserve the existing explicit TV-year identity contract: dropping it can select a remake.
        // Never infer a premiere year from season numbers or recover TV years from a raw filename.
        if (isEpisode) return TmdbTitleQuery(name, explicitYear, season, episode)
        return movieQuery(name, explicitYear, filename)
    }

    private fun isSearchName(name: String): Boolean = normalize(name) == name &&
        listOf(trailingYear, releaseMetadata, seasonSuffix).none { it.containsMatchIn(name) }

    private fun movieQuery(name: String, explicitYear: Int?, filename: String?): TmdbTitleQuery? {
        val filenameYear = TmdbFilenameYear.matchingYear(name, filename)
        if (explicitYear != null && filenameYear != null && explicitYear != filenameYear) return null
        return (explicitYear ?: filenameYear)?.let { TmdbTitleQuery(name, it, null, null) }
    }

    @Suppress("ReturnCount") // Fail closed on source-like input before producing an outbound query.
    fun normalize(value: String): String? {
        if (value.length !in 1..MAX_QUERY_LENGTH || hasUnsafeCodePoint(value)) return null
        // The shared sanitizer removes recognized source-query/credential suffixes and rejects URLs
        // and opaque stream IDs. Only its cleaned result may become an outbound title.
        val cleaned = VlcTitleResolver.titleSourceFromExtra(value) ?: return null
        if (hasUnsafeCodePoint(cleaned)) return null
        val title = Normalizer.normalize(cleaned, Normalizer.Form.NFC).replace(spaces, " ").trim()
        if (title.isEmpty() || title.length > MAX_QUERY_LENGTH || encodedByte.containsMatchIn(title)) return null
        // Require a fixed point after the source sanitizer's bounded decoding passes.
        if (VlcTitleResolver.titleSourceFromExtra(title) != title) return null
        if ('\\' in title || uri.containsMatchIn(title) || mediaExtension.containsMatchIn(title)) return null
        // One interior slash is legitimate title punctuation (Fate/stay night, 50/50). Absolute,
        // traversal and multi-component paths remain rejected. A bare relative pair is inherently
        // indistinguishable from a title, hence the already-cleaned-presentation input contract.
        if (!hasSafeSlash(title)) return null
        return title.takeIf(::hasSearchableText)
    }

    private fun hasSafeSlash(title: String): Boolean {
        if ('/' !in title) return true
        val parts = title.split('/')
        return parts.size == 2 && parts.none { it.trim() in setOf("", ".", "..", "~") }
    }

    /** Code-point aware: permits numeric titles and letters outside the BMP without folding scripts. */
    fun hasSearchableText(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            if (Character.isLetterOrDigit(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun hasUnsafeCodePoint(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            if (unsafeCodePoint(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun unsafeCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        val unsafeFormat = type == Character.FORMAT.toInt() && codePoint !in listOf('\u200C'.code, '\u200D'.code)
        val invalidCharacter = codePoint in '\uD800'.code..'\uDFFF'.code || codePoint == '\uFFFD'.code
        val lineBoundary = type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt()
        return invalidCharacter || lineBoundary || (Character.isISOControl(codePoint) || unsafeFormat)
    }
}
