package app.mpvnova.player

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
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
    private const val TAG_OTTO = 0x4F54544F // 'OTTO'
    private const val TAG_TRUE = 0x74727565 // 'true'
    private const val TAG_TYP1 = 0x74797031 // 'typ1'
    private const val SFNT_VERSION_1 = 0x00010000
    private const val TAG_NAME = 0x6E616D65 // 'name'
    private const val TAG_HEAD = 0x68656164 // 'head'
    private const val TTC_HEADER_BYTES = 12
    private const val TTC_FACE_COUNT_OFFSET = 8
    private const val SFNT_HEADER_BYTES = 12
    private const val TABLE_RECORD_BYTES = 16
    private const val NAME_HEADER_BYTES = 6
    private const val NAME_RECORD_BYTES = 12
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
    private data class TableSpan(val offset: Long, val length: Long)
    private val cache = LinkedHashMap<CacheKey, Metadata?>()

    fun familyName(file: File): String? = metadata(file)?.family

    fun style(file: File): Style? = metadata(file)?.style

    @Synchronized
    fun invalidate(file: File) {
        val path = file.absolutePath
        cache.keys.removeAll { it.path == path }
    }

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
                    RandomAccessFile(file, "r").use(::parse)
                }.getOrNull()
                cache[key] = parsed
                while (cache.size > MAX_CACHE_ENTRIES)
                    cache.remove(cache.keys.first())
                parsed
            }
        }
        return result
    }

    private fun parse(file: RandomAccessFile): Metadata? {
        val size = file.length()
        val directory = readDirectory(file, size) ?: return null
        var name: TableSpan? = null
        var head: TableSpan? = null
        while (directory.hasRemaining() && (name == null || head == null)) {
            val tag = directory.int
            directory.int // checksum
            val offset = directory.uint() // TTC table offsets are relative to the whole file.
            val length = directory.uint()
            if (tag == TAG_NAME && name == null) name = TableSpan(offset, length)
            if (tag == TAG_HEAD && head == null) head = TableSpan(offset, length)
        }
        val family = name?.takeIf { containsSpan(size, it.offset, it.length) }
            ?.let { readBestFamily(file, it) }
        val style = head?.takeIf { containsSpan(size, it.offset, it.length) }
            ?.let { readStyle(file, it) }
        return Metadata(family, style)
    }

    private fun readDirectory(file: RandomAccessFile, size: Long): ByteBuffer? {
        if (size !in SFNT_HEADER_BYTES.toLong()..MAX_FONT_FILE_BYTES) return null
        val header = file.readSpan(0L, SFNT_HEADER_BYTES)
        val faceOffset = if (header.getInt(0) == TAG_TTCF) firstTtcFaceOffset(file, header, size) else 0L
        return faceOffset?.takeIf { containsSpan(size, it, SFNT_HEADER_BYTES.toLong()) }?.let { offset ->
            val face = if (offset == 0L) header else file.readSpan(offset, SFNT_HEADER_BYTES)
            when (face.int) {
                SFNT_VERSION_1, TAG_OTTO, TAG_TRUE, TAG_TYP1 -> {
                    val directoryOffset = offset + SFNT_HEADER_BYTES
                    val directoryLength = face.ushort() * TABLE_RECORD_BYTES
                    if (containsSpan(size, directoryOffset, directoryLength.toLong())) {
                        file.readSpan(directoryOffset, directoryLength)
                    } else null
                }
                else -> null
            }
        }
    }

    private fun firstTtcFaceOffset(file: RandomAccessFile, header: ByteBuffer, size: Long): Long? {
        val count = header.getInt(TTC_FACE_COUNT_OFFSET).toLong() and UINT32_MASK
        val offsetsEnd = TTC_HEADER_BYTES + count * Int.SIZE_BYTES
        return if (count != 0L && offsetsEnd <= size) {
            file.readSpan(TTC_HEADER_BYTES.toLong(), Int.SIZE_BYTES).uint().takeIf { it >= offsetsEnd }
        } else null
    }

    private fun readStyle(file: RandomAccessFile, table: TableSpan): Style? {
        if (!containsSpan(table.length, HEAD_MAC_STYLE_OFFSET.toLong(), Short.SIZE_BYTES.toLong())) return null
        val macStyle = file.readSpan(table.offset + HEAD_MAC_STYLE_OFFSET, Short.SIZE_BYTES).ushort()
        return Style(bold = macStyle and MAC_STYLE_BOLD != 0, italic = macStyle and MAC_STYLE_ITALIC != 0)
    }

    private fun readBestFamily(file: RandomAccessFile, table: TableSpan): String? {
        if (table.length < NAME_HEADER_BYTES) return null
        val header = file.readSpan(table.offset, NAME_HEADER_BYTES)
        header.short // format
        val count = header.ushort()
        val stringOffset = header.ushort()
        val recordsLength = count * NAME_RECORD_BYTES
        return if (stringOffset in (NAME_HEADER_BYTES + recordsLength)..table.length) {
            val records = file.readSpan(table.offset + NAME_HEADER_BYTES, recordsLength)
            selectBestFamily(file, table, records, stringOffset)
        } else null
    }

    private fun selectBestFamily(
        file: RandomAccessFile,
        table: TableSpan,
        records: ByteBuffer,
        stringOffset: Int,
    ): String? {
        var best: String? = null
        var bestScore = -1
        while (records.hasRemaining()) {
            val platformId = records.ushort()
            records.short // encodingId
            records.short // languageId
            val nameId = records.ushort()
            val length = records.ushort()
            val start = (stringOffset + records.ushort()).toLong()
            if (nameId == NAME_ID_FAMILY || nameId == NAME_ID_TYPO_FAMILY) {
                val score = scoreName(nameId, platformId)
                if (score > bestScore && length > 0 && containsSpan(table.length, start, length.toLong())) {
                    val charset = when (platformId) {
                        PLATFORM_WINDOWS, PLATFORM_UNICODE -> Charsets.UTF_16BE
                        else -> Charsets.ISO_8859_1
                    }
                    best = String(file.readSpan(table.offset + start, length).array(), charset)
                    bestScore = score
                }
            }
        }
        return best?.trim()?.ifEmpty { null }
    }

    private fun scoreName(nameId: Int, platformId: Int): Int {
        var score = 0
        if (nameId == NAME_ID_TYPO_FAMILY) score += SCORE_TYPO
        if (platformId == PLATFORM_WINDOWS) score += SCORE_WINDOWS
        return score
    }
}

private const val UINT16_MASK = 0xFFFF
private const val UINT32_MASK = 0xFFFF_FFFFL

private fun containsSpan(size: Long, offset: Long, length: Long): Boolean =
    offset >= 0 && length >= 0 && offset <= size && length <= size - offset

private fun RandomAccessFile.readSpan(offset: Long, length: Int): ByteBuffer {
    seek(offset)
    return ByteBuffer.wrap(ByteArray(length).also { readFully(it) })
}

private fun ByteBuffer.ushort(): Int = short.toInt() and UINT16_MASK

private fun ByteBuffer.uint(): Long = int.toLong() and UINT32_MASK
