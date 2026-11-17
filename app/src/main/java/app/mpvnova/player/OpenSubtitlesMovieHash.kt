package app.mpvnova.player

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val HASH_CHUNK_BYTES = 65536
private const val HASH_READ_BYTES = 8192
private const val HASH_HEX_WIDTH = 16

/** Read-only OSHash: size plus first/last 64 KiB little-endian words, modulo 2^64.
 * Interrupt the worker or use the overload's cancellation predicate to stop hashing.
 */
fun openSubtitlesMovieHash(file: File): String? = openSubtitlesMovieHash(file) { false }

fun openSubtitlesMovieHash(file: File, isCancelled: () -> Boolean): String? {
    fun checkActive() {
        if (Thread.currentThread().isInterrupted || isCancelled()) fail(OpenSubtitlesFailureReason.CANCELLED)
    }
    try {
        checkActive()
        RandomAccessFile(file, "r").use { input ->
            val size = input.length()
            if (size < 2L * HASH_CHUNK_BYTES) return null
            var hash = size
            val bytes = ByteArray(HASH_READ_BYTES)
            val words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (offset in longArrayOf(0L, size - HASH_CHUNK_BYTES)) {
                input.seek(offset)
                repeat(HASH_CHUNK_BYTES / HASH_READ_BYTES) {
                    checkActive()
                    input.readFully(bytes)
                    words.rewind()
                    while (words.hasRemaining()) hash += words.long
                }
            }
            checkActive()
            if (input.length() != size) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
            return java.lang.Long.toHexString(hash).padStart(HASH_HEX_WIDTH, '0')
        }
    } catch (error: OpenSubtitlesException) {
        throw error
    } catch (_: IOException) {
        checkActive()
        fail(OpenSubtitlesFailureReason.NETWORK)
    } catch (_: SecurityException) {
        fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
    }
}
