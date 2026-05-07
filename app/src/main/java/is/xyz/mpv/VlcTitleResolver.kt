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
            File(trimmed).name
        }

        return percentDecode(candidate).trim().takeIf { it.isNotBlank() }
    }

    fun titleFromFileName(fileName: String?): String? {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        val end = name.lastIndexOf(".")
        return if (end <= 0) name else name.substring(0, end)
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
        return libTitle
    }

    fun resolve(itemTitle: String?, mediaTitle: String?, fileName: String?, isStream: Boolean): String? {
        return itemTitle
            ?: metaTitle(mediaTitle, fileName, isStream)
            ?: titleFromFileName(fileName)
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
}
