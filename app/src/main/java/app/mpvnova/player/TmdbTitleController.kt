package app.mpvnova.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Main-thread state; only the bounded HTTPS request runs on the worker. */
internal class TmdbTitleController(
    context: Context,
    private val lookup: TmdbTitleLookup = TmdbTitleLookup(),
    private val onChanged: () -> Unit,
) {
    private val context = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, LinkedBlockingQueue())
    private var pending: Future<*>? = null
    private var generation = 0
    private var activeQuery: TmdbTitleQuery? = null
    private var activeToken = ""
    private var closed = false
    private var lastSkippedInput: Pair<PlayerTitlePresentation, String?>? = null
    private val cache = LinkedHashMap<TmdbTitleQuery, CachedMatch>()

    @Suppress("ReturnCount") // Disabled, ineligible, and cached misses all retain the local presentation.
    fun resolve(local: PlayerTitlePresentation, filename: String? = null): PlayerTitlePresentation {
        if (closed) return local
        val token = if (preferences.getBoolean(TmdbSettings.ENABLED, false)) TmdbSettings.token(context) else ""
        val query = if (token.isNotBlank()) TmdbTitleQuery.from(local, filename) else null
        logSkippedIdentity(local, filename, token.isNotBlank() && query == null)
        if (query != activeQuery || token != activeToken) {
            cancelPending()
            if (token != activeToken) cache.clear()
            activeQuery = query
            activeToken = token
        }
        if (query == null) return local
        val cached = cache[query]?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }
        if (cached != null) {
            val match = cached.match ?: return local
            return applyMatch(local, query, match)
        }
        if (pending == null) schedule(query, token)
        return local
    }

    private fun applyMatch(
        local: PlayerTitlePresentation, query: TmdbTitleQuery, match: TmdbTitleMatch,
    ): PlayerTitlePresentation {
        val retainYear = !query.isEpisode && local.title.trim().endsWith("(${query.year})")
        val title = if (retainYear) "${match.title} (${query.year})" else match.title
        return cleanEpisodeTitle(local.copy(title = title, episodeTitle = match.episodeTitle ?: local.episodeTitle))
    }

    private fun schedule(query: TmdbTitleQuery, token: String) {
        val requestGeneration = generation
        val description = query.logDescription()
        Log.i(LOG_TAG, "Lookup started: $description")
        pending = worker.submit {
            var outcome = "no unambiguous match"
            val match = try {
                lookup.lookup(query, token).also { if (it != null) outcome = "matched" }
            } catch (error: TmdbHttpException) {
                outcome = "HTTP ${error.status}"
                null
            } catch (_: IOException) {
                outcome = "connection failed or cancelled"
                null
            } catch (_: JSONException) {
                outcome = "invalid response"
                null
            }
            handler.post {
                if (!closed && requestGeneration == generation) {
                    Log.i(LOG_TAG, "Lookup result: $outcome; $description")
                    pending = null
                    val lifetime = if (match == null) FAILURE_CACHE_MS else SUCCESS_CACHE_MS
                    cache[query] = CachedMatch(match, SystemClock.elapsedRealtime() + lifetime)
                    if (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
                    onChanged()
                }
            }
        }
    }

    private fun logSkippedIdentity(local: PlayerTitlePresentation, filename: String?, skipped: Boolean) {
        val input = if (skipped) local to filename else null
        if (input != null && input != lastSkippedInput) {
            // Do not log raw filenames, URLs, credentials, or unvalidated display titles.
            Log.i(LOG_TAG, "Lookup skipped: missing matching movie year or explicit season/episode")
        }
        lastSkippedInput = input
    }

    private fun TmdbTitleQuery.logDescription(): String {
        val safeTitle = title.replace('\n', ' ').replace('\r', ' ')
        return "title=$safeTitle year=$year season=$season episode=$episode"
    }

    private fun cancelPending() {
        generation++
        pending?.cancel(true)
        pending = null
        worker.purge()
    }

    fun close() {
        closed = true
        cancelPending()
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        cache.clear()
    }

    private data class CachedMatch(val match: TmdbTitleMatch?, val expiresAt: Long)

    companion object {
        private const val LOG_TAG = "mpv-tmdb"
        private const val MAX_CACHE_ENTRIES = 64
        private const val FAILURE_CACHE_MS = 5 * 60 * 1000L
        private const val SUCCESS_CACHE_MS = 24 * 60 * 60 * 1000L
    }
}
