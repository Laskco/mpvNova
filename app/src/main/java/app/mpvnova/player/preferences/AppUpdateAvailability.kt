package app.mpvnova.player.preferences

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdateAvailability {
    const val DEFAULT_PEEK_MAX_AGE_MS = 30L * 60L * 1000L
    private const val PEEK_THREAD_NAME = "mpvNova-update-peek"

    @Volatile private var latestRelease: ReleaseInfo? = null
    @Volatile private var lastCheckedAtMs: Long = 0L
    private val peekInFlight = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler: Handler? by lazy {
        runCatching { Looper.getMainLooper()?.let(::Handler) }.getOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun availableUpdate(context: Context): ReleaseInfo? {
        return latestRelease?.takeIf {
            isRemoteNewer(it.tagName, normalizedVersion(BuildConfig.VERSION_NAME))
        }
    }

    internal fun isIgnored(context: Context, release: ReleaseInfo): Boolean {
        val ignoredTag = defaultPreferences(context).getString(IGNORED_UPDATE_TAG_KEY, null)
        return versionsMatch(ignoredTag, release.tagName)
    }

    fun hasUnignoredUpdate(context: Context): Boolean {
        val release = availableUpdate(context) ?: return false
        return !isIgnored(context, release)
    }

    internal fun recordRelease(release: ReleaseInfo) {
        latestRelease = release
        lastCheckedAtMs = currentElapsedRealtimeMs()
        notifyStateChanged()
    }

    internal fun ignoreRelease(context: Context, release: ReleaseInfo) {
        defaultPreferences(context)
            .edit()
            .putString(IGNORED_UPDATE_TAG_KEY, release.tagName)
            .apply()
        notifyStateChanged()
    }

    fun notifyStateChanged() {
        val handler = mainHandler
        val isMainThread = handler != null &&
            runCatching { Looper.myLooper() == handler.looper }.getOrDefault(false)
        if (handler == null || isMainThread) {
            notifyListeners()
        } else {
            handler.post(::notifyListeners)
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun checkSilentlyIfStale(context: Context, maxAgeMs: Long = DEFAULT_PEEK_MAX_AGE_MS) {
        val elapsedMs = currentElapsedRealtimeMs()
        if (latestRelease != null && elapsedMs - lastCheckedAtMs < maxAgeMs) return
        if (!peekInFlight.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        var threadStarted = false
        try {
            Thread(
                {
                    try {
                        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                        runCatching {
                            fetchLatestReleaseForContext(appContext)
                        }.onSuccess(::recordRelease)
                    } finally {
                        peekInFlight.set(false)
                    }
                },
                PEEK_THREAD_NAME,
            ).apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                start()
                threadStarted = true
            }
        } finally {
            if (!threadStarted) {
                peekInFlight.set(false)
            }
        }
    }

    private fun defaultPreferences(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }

    private fun currentElapsedRealtimeMs(): Long =
        runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
}
