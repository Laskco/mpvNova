package app.mpvnova.player

internal data class PlayerTitlePresentation(
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
)

internal object PlayerTitleResolver {
    fun resolve(
        displayTitle: String?,
        sourceTitle: String?,
        mediaTitle: String?,
        fileName: String?,
    ): PlayerTitlePresentation? {
        return displayTitle?.trim()?.takeIf { it.isNotBlank() }?.let { fallbackTitle ->
            val candidates = listOfNotNull(sourceTitle, fallbackTitle, mediaTitle, fileName)
                .mapNotNull(::episodeTitleParts)
            val release = AnimeReleaseTitle.parse(sourceTitle) ?: AnimeReleaseTitle.parse(fileName)
            val primaryParts = episodeTitleParts(sourceTitle)
                ?: episodeTitleParts(fallbackTitle)
                ?: candidates.firstOrNull()
            if (primaryParts == null) {
                if (release != null && acceptsRelease(sourceTitle, release)) {
                    release.copy(episodeTitle = release.episodeTitle
                        ?: embeddedEpisodeTitle(mediaTitle, release.title, sourceTitle, fileName))
                } else {
                    cleanTitleBrackets(fallbackTitle).takeIf { it.isNotBlank() }
                        ?.let { PlayerTitlePresentation(it) }
                }
            } else {
                val episodeTitle = candidates.firstNotNullOfOrNull { candidate ->
                    candidate.episodeTitle.takeIf {
                        candidate.season == primaryParts.season &&
                            candidate.episode == primaryParts.episode &&
                            sameSeriesTitle(candidate.seriesTitle, primaryParts.seriesTitle)
                    }
                }
                PlayerTitlePresentation(
                    title = primaryParts.seriesTitle.ifBlank { fallbackTitle },
                    season = primaryParts.season,
                    episode = primaryParts.episode,
                    episodeTitle = episodeTitle ?: embeddedEpisodeTitle(
                        mediaTitle, primaryParts.seriesTitle, sourceTitle, fileName),
                )
            }
        }?.let(::cleanEpisodeTitle)
    }

    private fun acceptsRelease(sourceTitle: String?, release: PlayerTitlePresentation): Boolean =
        sourceTitle.isNullOrBlank() || AnimeReleaseTitle.parse(sourceTitle) != null ||
            sameSeriesTitle(sourceTitle, release.title)

    private fun embeddedEpisodeTitle(
        mediaTitle: String?, seriesTitle: String, sourceTitle: String?, fileName: String?,
    ): String? {
        val title = mediaTitle?.trim()?.takeIf { it.isNotBlank() && it.length <= MAX_EMBEDDED_TITLE_LENGTH }
            ?: return null
        val duplicatesSource = title == fileName || title == sourceTitle || sameSeriesTitle(title, seriesTitle)
        return title.takeUnless { duplicatesSource || isReleaseMetadata(it) }
            ?.let(::cleanTitleBrackets)?.takeIf { it.any(Char::isLetter) }
    }

    private fun isReleaseMetadata(title: String): Boolean {
        val isPath = title.contains("://") || title.contains('\\')
        val isFilename = FINAL_MEDIA_EXTENSION_PATTERN.containsMatchIn(title) ||
            SEASON_EPISODE_CAPTURE_PATTERN.containsMatchIn(title)
        val isCredit = RELEASE_TAG_PATTERN.containsMatchIn(title) || ENCODER_CREDIT_PATTERN.containsMatchIn(title)
        return isPath || isFilename || isCredit
    }

    private fun episodeTitleParts(candidate: String?): EpisodeTitleParts? {
        val decoded = VlcTitleResolver.titleSourceFromExtra(candidate)
            ?.substringBefore('?')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val withoutExtension = decoded.replace(FINAL_MEDIA_EXTENSION_PATTERN, "")
        return SEASON_EPISODE_CAPTURE_PATTERN.find(withoutExtension)?.let { seasonEpisode ->
            val seriesTitle = normalizeTitle(
                withoutExtension.substring(0, seasonEpisode.range.first)
            ) ?: return@let null
            val suffix = withoutExtension.substring(seasonEpisode.range.last + 1)
            val releaseTag = RELEASE_TAG_PATTERN.find(suffix)
            val episodeTitle = normalizeTitle(
                if (releaseTag == null) suffix else suffix.substring(0, releaseTag.range.first)
            )?.takeUnless(::looksLikeMediaExtension)
            EpisodeTitleParts(
                seriesTitle = seriesTitle,
                season = seasonEpisode.groupValues[1].toInt(),
                episode = seasonEpisode.groupValues[2].toInt(),
                episodeTitle = episodeTitle,
            )
        }
    }

    private fun normalizeTitle(value: String): String? {
        return value
            .replace(RELEASE_SEPARATOR_PATTERN, " ")
            .let(::cleanTitleBrackets)
            .trim(' ', '-', '_', '.')
            .replace(RELEASE_WHITESPACE_PATTERN, " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun looksLikeMediaExtension(value: String): Boolean =
        value.lowercase() in MEDIA_EXTENSIONS

    private data class EpisodeTitleParts(
        val seriesTitle: String,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?,
    )

    private val SEASON_EPISODE_CAPTURE_PATTERN =
        Regex("""(?i)(?:^|[ ._\-\[(])S(\d{1,2})E(\d{1,3})(?:E\d{1,3})?(?=$|[ ._\-\])])""")
    private val RELEASE_SEPARATOR_PATTERN = Regex("[._]+")
    private val RELEASE_WHITESPACE_PATTERN = Regex("\\s+")
    private val RELEASE_TAG_PATTERN = Regex(
        "(?i)(?:^|[ ._\\-\\[(])(?:" +
            "2160p|1080p|720p|480p|web[-_. ]?dl|webrip|blu[-_. ]?ray|bluray|bd|bdrip|hdrip|remux|" +
            "nf|cr|amzn|hulu|dsnp|multi|repack|proper|x264|x265|" +
            "h[ ._-]?264|h[ ._-]?265|hevc|av1|aac|eac3|ddp?5[ ._-]?1|flac" +
            ")(?=$|[ ._\\-\\])])"
    )
    private val FINAL_MEDIA_EXTENSION_PATTERN =
        Regex("(?i)\\.(?:mkv|mp4|m4v|webm|avi|mov|ts|m2ts)$")
    private val MEDIA_EXTENSIONS = setOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "m2ts")
    private const val MAX_EMBEDDED_TITLE_LENGTH = 512
    private val ENCODER_CREDIT_PATTERN = Regex(
        """(?i)\b(?:encoded|encoding|ripped|rip|muxed|uploaded|release)\s+by\b|\bwww\.|\.(?:com|net|org)\b""",
    )
}
