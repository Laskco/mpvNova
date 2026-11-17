package app.mpvnova.player

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

internal fun Set<String>.sortedFontFamilies(): List<String> = asSequence()
    .map { it to it.lowercase(Locale.ROOT) }
    .sortedBy { it.second }
    .map { it.first }
    .toList()

// Reads a font's real family name so the picker shows it and sub-font matches.
internal object SubtitleFontTable {
    internal const val MAX_FONT_FILE_BYTES = 32L * 1024L * 1024L
    private const val MAX_CACHE_ENTRIES = 64
    private const val TAG_TTCF = 0x74746366 // 'ttcf'
    private const val TAG_NAME = 0x6E616D65 // 'name'
    private const val TAG_HEAD = 0x68656164 // 'head'
    private const val MASK16 = 0xFFFF
    private const val TTC_FIRST_OFFSET_POS = 12
    private const val TABLE_RECORD_SKIP = 6
    private const val NAME_ID_FAMILY = 1
    private const val NAME_ID_TYPO_FAMILY = 16
    private const val PLATFORM_UNICODE = 0
    private const val PLATFORM_WINDOWS = 3
    private const val SCORE_TYPO = 2
    private const val SCORE_WINDOWS = 1
    private const val HEAD_MAC_STYLE_OFFSET = 44 // macStyle field offset within the 'head' table
    private const val MAC_STYLE_BOLD = 0x1
    private const val MAC_STYLE_ITALIC = 0x2

    /** A face's own bold/italic flags (from the 'head' table's macStyle), so the preview can pick
     *  the real matching face the way libass does instead of synthesizing a slant/weight. */
    data class Style(val bold: Boolean, val italic: Boolean)

    private data class CacheKey(val path: String, val size: Long, val modified: Long)
    private data class Metadata(val family: String?, val style: Style?)
    private val cache = LinkedHashMap<CacheKey, Metadata?>()

    fun familyName(file: File): String? = metadata(file)?.family

    fun style(file: File): Style? = metadata(file)?.style

    @Synchronized
    private fun metadata(file: File): Metadata? {
        val size = file.length()
        var result: Metadata? = null
        if (file.isFile && size > 0L && size <= MAX_FONT_FILE_BYTES) {
            val key = CacheKey(file.absolutePath, size, file.lastModified())
            result = if (cache.containsKey(key)) {
                cache[key]
            } else {
                val parsed = runCatching {
                    val data = file.readBytes()
                    Metadata(parse(data), parseStyle(data))
                }.getOrNull()
                cache[key] = parsed
                while (cache.size > MAX_CACHE_ENTRIES)
                    cache.remove(cache.keys.first())
                parsed
            }
        }
        return result
    }

    private fun parseStyle(data: ByteArray): Style? {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        bb.position(tableDirectoryOffset(bb))
        bb.int // sfntVersion
        val numTables = bb.short.toInt() and MASK16
        bb.position(bb.position() + TABLE_RECORD_SKIP)
        val headOffset = findTableOffset(bb, numTables, TAG_HEAD) ?: return null
        val pos = headOffset + HEAD_MAC_STYLE_OFFSET
        return if (pos < 0 || pos + 2 > data.size) {
            null
        } else {
            val macStyle = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            Style(bold = macStyle and MAC_STYLE_BOLD != 0, italic = macStyle and MAC_STYLE_ITALIC != 0)
        }
    }

    private fun parse(data: ByteArray): String? {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        bb.position(tableDirectoryOffset(bb))
        bb.int // sfntVersion
        val numTables = bb.short.toInt() and MASK16
        bb.position(bb.position() + TABLE_RECORD_SKIP) // searchRange, entrySelector, rangeShift
        val nameOffset = findNameTableOffset(bb, numTables) ?: return null
        return readBestFamily(data, bb, nameOffset)
    }

    private fun tableDirectoryOffset(bb: ByteBuffer): Int {
        return if (bb.int == TAG_TTCF) {
            bb.position(TTC_FIRST_OFFSET_POS) // tag(4) + version(4) + numFonts(4)
            bb.int
        } else {
            0
        }
    }

    private fun findNameTableOffset(bb: ByteBuffer, numTables: Int): Int? =
        findTableOffset(bb, numTables, TAG_NAME)

    private fun findTableOffset(bb: ByteBuffer, numTables: Int, tag: Int): Int? {
        repeat(numTables) {
            val recordTag = bb.int
            bb.int // checksum
            val offset = bb.int
            bb.int // length
            if (recordTag == tag) return offset
        }
        return null
    }

    private fun readBestFamily(data: ByteArray, bb: ByteBuffer, nameOffset: Int): String? {
        bb.position(nameOffset)
        bb.short // format
        val count = bb.short.toInt() and MASK16
        val stringOffset = bb.short.toInt() and MASK16
        var best: String? = null
        var bestScore = -1
        repeat(count) {
            val platformId = bb.short.toInt() and MASK16
            bb.short // encodingId
            bb.short // languageId
            val nameId = bb.short.toInt() and MASK16
            val length = bb.short.toInt() and MASK16
            val offset = bb.short.toInt() and MASK16
            if (nameId == NAME_ID_FAMILY || nameId == NAME_ID_TYPO_FAMILY) {
                val text = decodeName(data, nameOffset + stringOffset + offset, length, platformId)
                val score = scoreName(nameId, platformId)
                if (text != null && score > bestScore) {
                    best = text
                    bestScore = score
                }
            }
        }
        return best?.trim()?.ifEmpty { null }
    }

    private fun decodeName(data: ByteArray, start: Int, length: Int, platformId: Int): String? {
        if (length <= 0 || start < 0 || start + length > data.size) return null
        return when (platformId) {
            PLATFORM_WINDOWS, PLATFORM_UNICODE -> String(data, start, length, Charsets.UTF_16BE)
            else -> String(data, start, length, Charsets.ISO_8859_1)
        }
    }

    private fun scoreName(nameId: Int, platformId: Int): Int {
        var score = 0
        if (nameId == NAME_ID_TYPO_FAMILY) score += SCORE_TYPO
        if (platformId == PLATFORM_WINDOWS) score += SCORE_WINDOWS
        return score
    }
}
