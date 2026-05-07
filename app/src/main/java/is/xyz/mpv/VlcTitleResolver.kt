package app.mpvnova.player

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale

object VlcTitleResolver {
    fun itemTitleFromExtra(title: String?): String? {
        return title
            ?.takeIf { it.isNotBlank() }
            ?.let { percentDecode(it).trim() }
            ?.let(::displayTitleFromCandidate)
            ?.takeIf { it.isNotBlank() }
    }

    fun fileNameFromPathLike(path: String?): String? {
        val trimmed = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val candidate = if (hasScheme(trimmed)) {
            val uri = runCatching { URI(trimmed) }.getOrNull()
            if (uri != null && !uri.isOpaque && !uri.scheme.isNullOrBlank()) {
                uri.rawPath
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: trimmed
            } else {
                trimmed
            }
        } else {
            File(trimmed.substringBefore('?')).name
        }

        return percentDecode(candidate).trim().takeIf { it.isNotBlank() }
    }

    fun queryTitleFromPathLike(path: String?): String? {
        val trimmed = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val rawQuery = if (hasScheme(trimmed)) {
            runCatching { URI(trimmed).rawQuery }.getOrNull()
        } else {
            trimmed.substringAfter('?', missingDelimiterValue = "")
        } ?: return null
        if (rawQuery.isBlank())
            return null

        for (part in rawQuery.split('&')) {
            val separator = part.indexOf('=')
            if (separator <= 0)
                continue
            val key = percentDecode(part.substring(0, separator).replace('+', ' '))
                .lowercase(Locale.ROOT)
            if (key != "title" && key != "name")
                continue
            val value = percentDecode(part.substring(separator + 1).replace('+', ' ')).trim()
            displayTitleFromCandidate(value)?.let { return it }
        }
        return null
    }

    fun titleFromFileName(fileName: String?): String? {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        val end = name.lastIndexOf(".")
        val withoutExtension = if (end <= 0) name else name.substring(0, end)
        return displayTitleFromCandidate(withoutExtension)
    }

    fun metaTitle(title: String?, fileName: String?, isStream: Boolean): String? {
        val libTitle = title
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { percentDecode(it).trim() }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (!fileName.isNullOrBlank() && libTitle == fileName)
            return null
        if (isStream && libTitle.lowercase(Locale.ROOT).contains("://"))
            return null
        if (looksLikeIntentQueryMetadata(libTitle))
            return null
        return displayTitleFromCandidate(libTitle)
    }

    fun resolve(itemTitle: String?, mediaTitle: String?, fileName: String?, isStream: Boolean): String? {
        return itemTitle
            ?: metaTitle(mediaTitle, fileName, isStream)
            ?: titleFromFileName(fileName)
    }

    private fun looksLikeIntentQueryMetadata(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower.contains("torrent_name=") ||
                lower.contains("media_id=") ||
                lower.contains("?name=") ||
                lower.contains("&name=")
    }

    private fun displayTitleFromCandidate(candidate: String): String? {
        val trimmed = candidate.trim().takeIf { it.isNotBlank() } ?: return null
        val seasonEpisode = SEASON_EPISODE_PATTERN.find(trimmed)
        if (seasonEpisode != null)
            return normalizeReleaseTitle(trimmed.substring(0, seasonEpisode.range.last + 1))

        val releaseTag = RELEASE_TAG_PATTERN.find(trimmed)
        if (releaseTag != null && releaseTag.range.first > 0)
            return normalizeReleaseTitle(trimmed.substring(0, releaseTag.range.first))

        return normalizeReleaseTitle(trimmed)
    }

    private fun normalizeReleaseTitle(value: String): String? {
        return value
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '_', '-')
            .takeIf { it.isNotBlank() }
    }

    private fun hasScheme(value: String): Boolean {
        val colon = value.indexOf(':')
        if (colon <= 0) return false
        if (!value[0].isLetter()) return false
        for (i in 1 until colon) {
            val char = value[i]
            if (!char.isLetterOrDigit() && char != '+' && char != '-' && char != '.')
                return false
        }
        return true
    }

    private fun percentDecode(value: String): String {
        val decoded = StringBuilder(value.length)
        var bytes: ByteArrayOutputStream? = null
        var index = 0
        fun flushBytes() {
            val pending = bytes ?: return
            decoded.append(pending.toByteArray().toString(StandardCharsets.UTF_8))
            bytes = null
        }
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val high = value[index + 1].digitToIntOrNull(16)
                val low = value[index + 2].digitToIntOrNull(16)
                if (high != null && low != null) {
                    if (bytes == null) bytes = ByteArrayOutputStream()
                    bytes?.write((high shl 4) + low)
                    index += 3
                    continue
                }
            }
            flushBytes()
            decoded.append(char)
            index++
        }
        flushBytes()
        return decoded.toString()
    }

    private val SEASON_EPISODE_PATTERN =
        Regex("""(?i)(?:^|[ ._\-\[(])S\d{1,2}E\d{1,3}(?:E\d{1,3})?(?=$|[ ._\-\])])""")

    private val RELEASE_TAG_PATTERN = Regex(
        """(?i)(?:^|[ ._\-\[(])(?:2160p|1080p|720p|480p|web[-_. ]?dl|webrip|bluray|bdrip|hdrip|nf|cr|amzn|hulu|dsnp|multi|repack|proper|x264|x265|h[ ._-]?264|h[ ._-]?265|hevc|av1|aac|eac3|ddp?5[ ._-]?1|flac)(?=$|[ ._\-\])])"""
    )
}
