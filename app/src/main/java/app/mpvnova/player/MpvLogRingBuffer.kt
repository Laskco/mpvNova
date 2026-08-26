package app.mpvnova.player

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Bounded in-memory ring buffer of recent mpv log lines. Captures everything
 * the native log bridge dispatches for the lifetime of the process so that
 * a support bundle (or a crash report) can ship the last N lines without
 * the user having to set up adb or run logcat.
 *
 * Registered once at process start via [install]. Snapshot reads briefly share
 * the same lock used to append a line.
 */
internal object MpvLogRingBuffer {
    private const val DEFAULT_CAPACITY = 2000
    private val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val lock = Any()
    private val lines = ArrayDeque<String>(DEFAULT_CAPACITY)

    private val observer = MpvLogObserver { prefix, level, text ->
        if (level > MpvLogLevel.MPV_LOG_LEVEL_INFO)
            return@MpvLogObserver
        val stamp = synchronized(timestamp) { timestamp.format(Date()) }
        val levelLabel = levelLabel(level)
        val formatted = "$stamp [$levelLabel] $prefix: ${sanitizeMpvLogText(text)}"
        synchronized(lock) {
            if (lines.size >= DEFAULT_CAPACITY)
                lines.removeFirst()
            lines.addLast(formatted)
        }
    }

    private var installed = false

    fun install() {
        synchronized(lock) {
            if (installed) return
            installed = true
        }
        addMpvLogObserver(observer)
    }

    /** Snapshot of the current buffer, oldest first. Safe to call from any thread. */
    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    /** Snapshot rendered as a single string with line breaks, ready to write to a file. */
    fun snapshotText(): String = snapshot().joinToString(separator = "\n")

    fun latestEnabledFeatures(): String? = synchronized(lock) {
        lines.lastOrNull { it.contains("List of enabled features:", ignoreCase = true) }
            ?.let { line -> line.substringAfter(": ", missingDelimiterValue = line) }
    }

    private fun levelLabel(level: Int): String = when (level) {
        MpvLogLevel.MPV_LOG_LEVEL_FATAL -> "fatal"
        MpvLogLevel.MPV_LOG_LEVEL_ERROR -> "error"
        MpvLogLevel.MPV_LOG_LEVEL_WARN -> "warn"
        MpvLogLevel.MPV_LOG_LEVEL_INFO -> "info"
        MpvLogLevel.MPV_LOG_LEVEL_V -> "v"
        MpvLogLevel.MPV_LOG_LEVEL_DEBUG -> "debug"
        MpvLogLevel.MPV_LOG_LEVEL_TRACE -> "trace"
        else -> "log"
    }
}
