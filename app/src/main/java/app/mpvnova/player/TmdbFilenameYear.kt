package app.mpvnova.player

/** Recover a movie year without changing the display title or guessing a different movie. */
internal object TmdbFilenameYear {
    private const val MAX_FILENAME_LENGTH = 1024
    private val extension = Regex("""(?i)\.(mkv|mp4|m4v|webm|avi|mov|ts|m2ts)$""")
    private val year = Regex("""(?:^|[ ._\-(\[])((?:18|19|20|21)\d{2})(?=$|[ ._\-)\]])""")
    private val separators = Regex("[._]+")
    private val releaseSuffix = Regex(
        """(?i)^(?:UHD|BluRay|Blu-ray|BDRip|BRRip|WEB-DL|WEBRip|HDTV|REMUX|TS|TC|CAM|""" +
            """2160p|1080p|720p|480p|x264|x265|HEVC|H264|H265|AV1|HDR|DV|MULTI)(?:$|[ ._\-\[])""",
    )

    @Suppress("ReturnCount") // Never extract years from paths or unrecognized filename formats.
    fun matchingYear(title: String, filename: String?): Int? {
        val name = filename?.takeIf { it.length <= MAX_FILENAME_LENGTH }
            ?.let(VlcTitleResolver::titleSourceFromExtra) ?: return null
        val pathLike = name.contains('/') || name.contains('\\')
        if (pathLike || !extension.containsMatchIn(name)) return null
        val stem = name.replace(extension, "")
        val candidates = year.findAll(stem).mapNotNull { match ->
            val prefix = stem.substring(0, match.range.first).replace(separators, " ")
                .let(::cleanTitleBrackets).trim(' ', '-')
            val suffix = stem.substring(match.range.last + 1).trimStart(' ', '.', '_', '-', ')', ']')
            val releaseTail = suffix.isEmpty() || releaseSuffix.containsMatchIn(suffix)
            match.groupValues[1].toIntOrNull().takeIf { releaseTail && sameSeriesTitle(prefix, title) }
        }.toList()
        return candidates.singleOrNull()
    }
}
