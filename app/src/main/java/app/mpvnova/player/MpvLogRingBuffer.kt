package app.mpvnova.player

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private const val PERSIST_INTERVAL_MS = 3_000L
    private const val LOG_DIRECTORY = "diagnostics/mpv"
    private const val CURRENT_LOG = "current-session.log"
    private const val PREVIOUS_LOG = "previous-session.log"
    private val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val lock = Any()
    private val lines = ArrayDeque<String>(DEFAULT_CAPACITY)
    private val persistenceExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "mpv-log-writer").apply { isDaemon = true }
    }
    private var currentLogFile: File? = null
    private var previousLogFile: File? = null
    private var generation = 0L
    private var persistenceScheduled = false

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
            generation += 1
            schedulePersistenceLocked()
        }
    }

    private var installed = false

    fun install(context: Context) {
        synchronized(lock) {
            if (installed) return
            installed = true
            val directory = File(context.filesDir, LOG_DIRECTORY)
            currentLogFile = File(directory, CURRENT_LOG)
            previousLogFile = File(directory, PREVIOUS_LOG)
            persistenceExecutor.execute {
                runCatching {
                    directory.mkdirs()
                    val current = currentLogFile ?: return@runCatching
                    val previous = previousLogFile ?: return@runCatching
                    // Preserve the last useful playback log when an intervening process never
                    // started mpv and therefore left an empty current-session file.
                    if (current.isFile && current.length() > 0L) {
                        previous.delete()
                        if (!current.renameTo(previous)) {
                            current.copyTo(previous, overwrite = true)
                            current.delete()
                        }
                    }
                    if (current.isFile) current.delete()
                    current.createNewFile()
                }
            }
        }
        addMpvLogObserver(observer)
    }

    /** Snapshot of the current buffer, oldest first. Safe to call from any thread. */
    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    /** Snapshot rendered as a single string with line breaks, ready to write to a file. */
    fun snapshotText(): String = snapshot().joinToString(separator = "\n")

    fun previousSessionText(): String = runCatching {
        previousLogFile?.takeIf(File::isFile)?.readText().orEmpty()
    }.getOrDefault("")

    fun latestEnabledFeatures(): String? = synchronized(lock) {
        lines.lastOrNull { it.contains("List of enabled features:", ignoreCase = true) }
            ?.let { line -> line.substringAfter(": ", missingDelimiterValue = line) }
    }

    private fun schedulePersistenceLocked() {
        if (persistenceScheduled || currentLogFile == null) return
        persistenceScheduled = true
        persistenceExecutor.schedule(::persistSnapshot, PERSIST_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun persistSnapshot() {
        val (snapshotGeneration, snapshot) = synchronized(lock) {
            generation to lines.toList()
        }
        // Formatting a full buffer can be comparatively expensive during verbose playback.
        // Keep it off the append lock so native log delivery is never held up by string work.
        val content = snapshot.joinToString(separator = "\n", postfix = "\n")
        runCatching { currentLogFile?.writeText(content) }
        synchronized(lock) {
            if (generation == snapshotGeneration) {
                persistenceScheduled = false
            } else {
                persistenceExecutor.schedule(::persistSnapshot, PERSIST_INTERVAL_MS, TimeUnit.MILLISECONDS)
            }
        }
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
