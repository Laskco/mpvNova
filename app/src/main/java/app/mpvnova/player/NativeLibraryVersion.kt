package app.mpvnova.player

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

internal object NativeLibraryVersion {
    fun find(file: File, marker: String): String? {
        val markerBytes = marker.toByteArray(Charsets.ISO_8859_1)
        file.inputStream().buffered(VERSION_SCAN_BUFFER_SIZE).use { stream ->
            return scan(stream, markerBytes)
        }
    }

    private fun scan(stream: InputStream, markerBytes: ByteArray): String? {
        val versionBytes = ByteArrayOutputStream()
        val buffer = ByteArray(VERSION_SCAN_BUFFER_SIZE + markerBytes.size)
        var foundMarker = false
        var carry = 0

        while (versionBytes.size() < VERSION_SCAN_MAX_BYTES) {
            val read = stream.read(buffer, carry, VERSION_SCAN_BUFFER_SIZE)
            if (read == -1) break
            val limit = carry + read
            val startIndex = if (foundMarker) 0 else indexOfBytes(buffer, limit, markerBytes)
            if (startIndex >= 0) {
                foundMarker = true
                if (appendUntilLineEnd(buffer, startIndex, limit, versionBytes))
                    return versionBytes.versionString()
            }
            carry = if (foundMarker) 0 else minOf(limit, markerBytes.size)
            if (carry > 0) buffer.copyInto(buffer, 0, limit - carry, limit)
        }

        return versionBytes.versionStringOrNull()
    }

    private fun appendUntilLineEnd(
        chunk: ByteArray, startIndex: Int, limit: Int, output: ByteArrayOutputStream,
    ): Boolean {
        var index = startIndex
        while (index < limit && output.size() < VERSION_SCAN_MAX_BYTES) {
            val byte = chunk[index]
            if (byte.isVersionLineEnd())
                return true
            output.write(byte.toInt())
            index++
        }
        return false
    }

    private fun ByteArrayOutputStream.versionStringOrNull(): String? {
        return if (size() > 0) versionString() else null
    }

    private fun ByteArrayOutputStream.versionString(): String {
        return toString(Charsets.ISO_8859_1.name()).trim()
    }

    private fun Byte.isVersionLineEnd(): Boolean {
        return this == NUL_BYTE || this == CARRIAGE_RETURN_BYTE || this == LINE_FEED_BYTE
    }

    private fun indexOfBytes(haystack: ByteArray, length: Int, needle: ByteArray): Int {
        val limit = length - needle.size
        var i = 0
        outer@ while (i <= limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return -1
    }

    private const val VERSION_SCAN_BUFFER_SIZE = 16_384
    private const val VERSION_SCAN_MAX_BYTES = 240
    private const val NUL_BYTE = 0.toByte()
    private const val CARRIAGE_RETURN_BYTE = '\r'.code.toByte()
    private const val LINE_FEED_BYTE = '\n'.code.toByte()
}
