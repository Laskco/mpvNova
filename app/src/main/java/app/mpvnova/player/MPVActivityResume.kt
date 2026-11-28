package app.mpvnova.player

import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.util.Locale
import java.lang.IllegalArgumentException

internal fun MPVActivity.resumeSourceFromIntent(intent: Intent?, filepath: String?): String? {
    return intent?.data?.toString() ?: filepath
}

internal fun MPVActivity.resumeIdentityFromSource(source: String?): ResumeIdentity? {
    return source?.takeIf { it.isNotBlank() }?.let { resumeSource ->
        val path = try {
            Uri.parse(resumeSource).path ?: resumeSource
        } catch (_: IllegalArgumentException) {
            resumeSource
        }
        RESUME_HASH_REGEX.find(path)?.let { match ->
            val hash = match.groupValues[1].lowercase(Locale.US)
            val fileToken = lastResumePathSegment(path, hash)
                ?.lowercase(Locale.US)
                ?.replace(FILE_EXTENSION_REGEX, "")
                ?.replace(NON_ALNUM_REGEX, "-")
                ?.trim('-')
                ?.take(RESUME_FILE_TOKEN_MAX_LENGTH)
                ?.takeIf { it.length >= RESUME_FILE_TOKEN_MIN_LENGTH }
            ResumeIdentity(hash, fileToken)
        }
    }
}

internal fun lastResumePathSegment(path: String, hash: String): String? {
    var end = path.length
    while (end > 0) {
        val delimiter = path.lastIndexOf('/', end - 1)
        val segment = path.substring(delimiter + 1, end)
        if (segment.isNotBlank() && !segment.equals(hash, ignoreCase = true)) return segment
        end = delimiter
    }
    return null
}

internal fun MPVActivity.resumeKey(identity: ResumeIdentity): String {
    return if (identity.fileToken != null)
        "resume:${identity.hash}:${identity.fileToken}"
    else
        "resume:${identity.hash}"
}

internal fun MPVActivity.legacyResumeKey(identity: ResumeIdentity) = "resume:${identity.hash}"

internal fun MPVActivity.cachedResumeIdentity(): ResumeIdentity? {
    val source = currentResumeSource
    if (source != cachedResumeIdentitySource) {
        cachedResumeIdentitySource = source
        cachedResumeIdentity = resumeIdentityFromSource(source)
    }
    return cachedResumeIdentity
}

internal fun MPVActivity.saveResumePosition(
    positionMs: Long = psc.position,
    durationMs: Long = psc.duration,
) = synchronized(fileReplacementLock) {
    val identity = if (shouldSavePosition && !suppressEndFileFinishForReplace)
        cachedResumeIdentity()
    else
        null
    if (identity == null) return@synchronized
    val pos = positionMs
    val dur = durationMs
    if (dur <= 0L)
        return@synchronized

    val prefs = getDefaultSharedPreferences(applicationContext)
    val key = resumeKey(identity)
    val legacyKey = legacyResumeKey(identity)
    if (pos < RESUME_MIN_POSITION_MS) {
        prefs.edit().remove(key).remove(legacyKey).apply()
    } else if (pos >= dur - RESUME_NEAR_END_MS) {
        // User effectively finished — don't preserve a "stuck at 99%"
        // position that'll resume to nothing on next launch.
        prefs.edit().remove(key).remove(legacyKey).apply()
    } else {
        val entry = "$pos|$dur|${System.currentTimeMillis()}"
        val editor = prefs.edit().putString(key, entry)
        if (legacyKey != key)
            editor.remove(legacyKey)
        editor.apply()
    }
}

internal fun MPVActivity.loadResumePosition(): Long? {
    val identity = if (shouldSavePosition)
        cachedResumeIdentity()
    else
        null
    return identity?.let {
        val prefs = getDefaultSharedPreferences(applicationContext)
        val raw = prefs.getString(resumeKey(it), null)
        val pos = raw?.substringBefore('|')?.toLongOrNull()
        val dur = raw?.substringAfter('|', "")?.substringBefore('|')?.toLongOrNull() ?: 0L
        pos?.takeIf { value ->
            value >= RESUME_MIN_POSITION_MS && (dur <= 0L || value < dur - RESUME_NEAR_END_MS)
        }
    }
}

internal fun MPVActivity.applyPreLoadResumePlaybackPolicy() = synchronized(fileReplacementLock) {
    val suppress = suppressWatchLaterResumeForNextLoad
    suppressWatchLaterResumeForNextLoad = false
    if (suppress) {
        if (savedResumePlaybackOption == null) {
            savedResumePlaybackOption = mpvGetPropertyBoolean("resume-playback") ?: true
        }
        mpvSetPropertyBoolean("resume-playback", false)
    } else {
        restoreWatchLaterResumeAfterLoad()
    }
}

internal fun MPVActivity.restoreWatchLaterResumeAfterLoad() = synchronized(fileReplacementLock) {
    val saved = savedResumePlaybackOption ?: return@synchronized
    savedResumePlaybackOption = null
    mpvSetPropertyBoolean("resume-playback", saved)
}

internal fun MPVActivity.clearFinishedPositions() {
    // 1) Custom resume table
    val identity = cachedResumeIdentity()
    if (identity != null) {
        val prefs = getDefaultSharedPreferences(applicationContext)
        prefs.edit()
            .remove(resumeKey(identity))
            .remove(legacyResumeKey(identity))
            .apply()
    }
    // 2) mpv's native watch-later file: prefer the path captured at START_FILE
    // because on playlist advancement END_FILE may already expose the next item's path,
    // while on standalone EOF the current path is already cleared.
    val targetPath = lastStartedMpvPath?.takeIf { it.isNotBlank() }
        ?: mpvGetPropertyString("path")?.takeIf { it.isNotBlank() }
    if (!targetPath.isNullOrBlank()) {
        mpvCommand(arrayOf("delete-watch-later-config", targetPath))
    } else {
        mpvCommand(arrayOf("delete-watch-later-config"))
    }
}

private val resumeTablePrunedThisProcess = java.util.concurrent.atomic.AtomicBoolean(false)

internal fun MPVActivity.pruneResumeTableAsync() {
    if (!resumeTablePrunedThisProcess.compareAndSet(false, true)) return
    val appCtx = applicationContext
    Thread(
        { pruneResumeTable(appCtx) },
        "mpvNova-resume-prune",
    ).apply {
        isDaemon = true
        start()
    }
}

internal fun MPVActivity.pruneResumeTable() {
    pruneResumeTable(applicationContext)
}

private fun pruneResumeTable(appContext: android.content.Context) {
    val prefs = getDefaultSharedPreferences(appContext)
    val now = System.currentTimeMillis()
    val toDelete = mutableListOf<String>()
    val recent = mutableListOf<Pair<String, Long>>()
    for ((key, value) in prefs.all) {
        val timestamp = (value as? String)?.takeIf { key.startsWith("resume:") }
            ?.let(::resumeEntryTimestamp) ?: continue
        if (now - timestamp > RESUME_TABLE_MAX_AGE_MS) toDelete.add(key) else recent.add(key to timestamp)
    }
    if (recent.size > RESUME_TABLE_MAX_ENTRIES) {
        val sorted = recent.sortedBy { it.second }
        val excess = sorted.size - RESUME_TABLE_MAX_ENTRIES
        for (i in 0 until excess) toDelete.add(sorted[i].first)
    }
    if (toDelete.isNotEmpty()) {
        val editor = prefs.edit()
        for (k in toDelete) editor.remove(k)
        editor.apply()
        Log.v(MPV_ACTIVITY_TAG, "resume: pruned ${toDelete.size} stale entries " +
                "(${minOf(recent.size, RESUME_TABLE_MAX_ENTRIES)} remain)")
    }
}

internal fun resumeEntryTimestamp(entry: String): Long? {
    val first = entry.indexOf('|')
    val second = if (first < 0) -1 else entry.indexOf('|', first + 1)
    if (second < 0) return null
    val end = entry.indexOf('|', second + 1).takeIf { it >= 0 } ?: entry.length
    return entry.substring(second + 1, end).toLongOrNull()
}

/** Format a resume-table millisecond timestamp as MM:SS / H:MM:SS for the toast. */
internal fun MPVActivity.formatResumeTime(ms: Long): String =
    Utils.prettyTime((ms / MILLIS_PER_SECOND_LONG).toInt())
