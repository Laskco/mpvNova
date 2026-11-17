package app.mpvnova.player

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal fun installSubtitleFont(input: InputStream, destination: File): File? {
    val target = destination.absoluteFile
    val parent = target.parentFile ?: return null
    parent.mkdirs()
    val temporary = File.createTempFile(".subtitle-font-", ".tmp", parent)
    return try {
        val copied = temporary.outputStream().use { input.copyFontTo(it) }
        if (copied && SubtitleFontTable.familyName(temporary) != null) {
            Os.rename(temporary.path, target.path)
            SubtitleFontTable.invalidate(target)
            destination
        } else {
            null
        }
    } catch (_: ErrnoException) {
        null
    } finally {
        temporary.delete()
    }
}

private fun InputStream.copyFontTo(output: OutputStream): Boolean {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var written = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return written > 0L
        if (read == 0 || read > SubtitleFontTable.MAX_FONT_FILE_BYTES - written) return false
        output.write(buffer, 0, read)
        written += read
    }
}
