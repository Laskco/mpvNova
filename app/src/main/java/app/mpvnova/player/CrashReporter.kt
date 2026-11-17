package app.mpvnova.player

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-only crash reporter. Writes a text report to persistent app storage,
 * then chains to the previous handler so the OS still sees the crash.
 * "Export support bundle" zips this directory.
 */
internal object CrashReporter {
    private const val TAG = "mpvNova-crash"
    private const val MAX_CRASH_FILES = 10
    private val publicationLock = Any()

    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            @Suppress("TooGenericExceptionCaught")
            try {
                writeCrashFile(appContext, thread, throwable)
            } catch (write: Throwable) {
                // Catch Throwable: secondary exception would kill the JVM
                // before the chained handler runs, losing the original crash.
                Log.e(TAG, "Failed to write crash report", write)
            } finally {
                // The previous handler surfaces the original crash to the OS.
                previous?.uncaughtException(thread, throwable)
            }
        }
        installed = true
    }

    private fun writeCrashFile(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIRECTORY).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report = buildCrashReport(stamp, thread, throwable)
        val temporary = File.createTempFile(".crash-$stamp-", ".pending", dir)
        val file = File(dir, temporary.nameWithoutExtension.removePrefix(".") + ".txt")
        try {
            temporary.outputStream().use { output ->
                output.write(report.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            synchronized(publicationLock) {
                if (file.exists()) throw IOException("Crash report already exists: ${file.name}")
                Os.rename(temporary.absolutePath, file.absolutePath)
                pruneOldCrashes(dir, file)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun buildCrashReport(stamp: String, thread: Thread, throwable: Throwable): String {
        val mpvLog = MpvLogRingBuffer.snapshot()
        // Thread.threadId() is unavailable on older Android versions.
        @Suppress("DEPRECATION")
        val threadId = thread.id
        return buildString {
            appendLine("mpvNova crash report")
            appendLine("Timestamp: $stamp")
            appendLine(
                "App version: ${BuildConfig.VERSION_NAME} " +
                    "(${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"
            )
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS?.joinToString().orEmpty()}")
            appendLine("Thread: ${thread.name} (id=$threadId, priority=${thread.priority})")
            appendLine()
            appendLine("--- Stack trace ---")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            appendLine(sanitizeMpvLogText(sw.toString().trimEnd()))
            appendLine()
            appendLine("--- Recent mpv log (last ${mpvLog.size} lines) ---")
            if (mpvLog.isEmpty()) appendLine()
            else mpvLog.forEach { appendLine(it) }
        }
    }

    /** Keep at most [MAX_CRASH_FILES] crash files; older ones dropped silently. */
    private fun pruneOldCrashes(dir: File, newest: File) {
        val existing = dir.listFiles()?.filter { it != newest && it.isFile && it.name.startsWith("crash-") }
            ?: return
        if (existing.size < MAX_CRASH_FILES) return
        existing
            .sortedBy { it.lastModified() }
            .take(existing.size - (MAX_CRASH_FILES - 1))
            .forEach { it.delete() }
    }

    private const val CRASH_DIRECTORY = "diagnostics/crashes"
}
