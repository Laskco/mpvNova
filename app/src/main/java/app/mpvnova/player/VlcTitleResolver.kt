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
        val candidate = if (hasUriScheme(trimmed)) {
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
        val trimmed = path?.trim()?.takeIf { it.isNotBlank() }
        return trimmed?.let { value ->
            val rawQuery = if (hasUriScheme(value)) {
                runCatching { URI(value).rawQuery }.getOrNull()
            } else {
                value.substringAfter('?', missingDelimiterValue = "")
            }
            rawQuery?.takeUnless { it.isBlank() }?.split('&')?.firstNotNullOfOrNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    val key = percentDecode(part.substring(0, separator).replace('+', ' '))
                        .lowercase(Locale.ROOT)
                    val title = percentDecode(part.substring(separator + 1).replace('+', ' ')).trim()
                    if (key == "title" || key == "name")
                        displayTitleFromCandidate(title)
                    else
                        null
                }
            }
        }
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
        return when {
            !fileName.isNullOrBlank() && libTitle == fileName -> null
            isStream && libTitle.lowercase(Locale.ROOT).contains("://") -> null
            looksLikeIntentQueryMetadata(libTitle) -> null
            else -> displayTitleFromCandidate(libTitle)
        }
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
        val releaseTag = RELEASE_TAG_PATTERN.find(trimmed)
        val displayTitle = when {
            seasonEpisode != null -> trimmed.substring(0, seasonEpisode.range.last + 1)
            releaseTag != null && releaseTag.range.first > 0 -> trimmed.substring(0, releaseTag.range.first)
            else -> trimmed
        }
        return normalizeReleaseTitle(displayTitle)
    }

    private fun normalizeReleaseTitle(value: String): String? {
        return value
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '_', '-')
            .takeIf { it.isNotBlank() }
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
        fun percentEncodedByte(at: Int): Int? {
            return if (value[at] == '%' && at + PERCENT_BYTE_HEX_LENGTH < value.length) {
                val high = value[at + 1].digitToIntOrNull(HEX_RADIX)
                val low = value[at + 2].digitToIntOrNull(HEX_RADIX)
                if (high != null && low != null) (high shl BYTE_NIBBLE_SHIFT) + low else null
            } else {
                null
            }
        }
        while (index < value.length) {
            val char = value[index]
            val byte = percentEncodedByte(index)
            if (byte != null) {
                if (bytes == null) bytes = ByteArrayOutputStream()
                bytes?.write(byte)
                index += PERCENT_ESCAPE_LENGTH
                continue
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
        "(?i)(?:^|[ ._\\-\\[(])(?:" +
            "2160p|1080p|720p|480p|web[-_. ]?dl|webrip|bluray|bdrip|hdrip|" +
            "nf|cr|amzn|hulu|dsnp|multi|repack|proper|x264|x265|" +
            "h[ ._-]?264|h[ ._-]?265|hevc|av1|aac|eac3|ddp?5[ ._-]?1|flac" +
            ")(?=$|[ ._\\-\\])])"
    )

    private const val PERCENT_BYTE_HEX_LENGTH = 2
    private const val PERCENT_ESCAPE_LENGTH = 3
    private const val HEX_RADIX = 16
    private const val BYTE_NIBBLE_SHIFT = 4
}

private fun hasUriScheme(value: String): Boolean {
    val colon = value.indexOf(':')
    return colon > 0 && value[0].isLetter() && (1 until colon).all { index ->
        val char = value[index]
        char.isLetterOrDigit() || char == '+' || char == '-' || char == '.'
    }
}
