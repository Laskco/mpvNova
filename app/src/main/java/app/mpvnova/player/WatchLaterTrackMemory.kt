package app.mpvnova.player

import android.util.Log
import java.io.File
import java.io.IOException

private val WATCH_LATER_ENTRY_NAME = Regex("[0-9a-fA-F]{32}")
private const val MAX_WATCH_LATER_ENTRY_BYTES = 1024L * 1024

internal fun clearWatchLaterTracks(filesDir: File, kind: RememberedTrackKind): Boolean {
    val directory = File(filesDir.canonicalFile, "watch_later")
    return when {
        !directory.exists() -> true
        directory.canonicalFile != directory -> false
        else -> clearWatchLaterDirectory(directory, kind)
    }
}

private fun clearWatchLaterDirectory(directory: File, kind: RememberedTrackKind): Boolean {
    val entries = directory.listFiles { _, name -> WATCH_LATER_ENTRY_NAME.matches(name) } ?: return false
    var complete = true
    for (entry in entries) {
        try {
            when {
                entry.canonicalFile != entry -> complete = false
                entry.isFile -> clearEntryTracks(entry, kind)
            }
        } catch (error: IOException) {
            Log.w("mpvnova-watch-later", "Couldn't clear a saved track choice", error)
            complete = false
        }
    }
    return complete
}

private fun clearEntryTracks(file: File, kind: RememberedTrackKind) {
    val modified = file.lastModified()
    val size = file.length()
    if (size > MAX_WATCH_LATER_ENTRY_BYTES) throw IOException("Oversized watch-later entry")
    // Keep all bytes outside the removed option lines, including non-UTF-8 paths.
    val charset = Charsets.ISO_8859_1
    val source = file.readText(charset)
    val cleaned = removeWatchLaterTrackOptions(source, kind)
    if (cleaned == source) return

    val replacement = File.createTempFile("track-memory-", ".tmp", file.parentFile)
    try {
        replacement.writeText(cleaned, charset)
        // mpv can compare the resume entry's timestamp to the original media file.
        val unchanged = file.lastModified() == modified && file.length() == size
        if (!unchanged || !replacement.setLastModified(modified) || !replacement.renameTo(file)) {
            throw IOException("Couldn't replace watch-later entry")
        }
    } finally {
        replacement.delete()
    }
}

internal fun removeWatchLaterTrackOptions(source: String, kind: RememberedTrackKind): String =
    source.splitToSequence('\n').filterNot { line ->
        val option = line.trimStart().removePrefix("--")
        '=' in option && option.substringBefore('=').trimEnd() in kind.resumeKeys
    }.joinToString("\n")
