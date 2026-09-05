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
            val lastSegment = path
                .split('/')
                .lastOrNull { it.isNotBlank() && !it.equals(hash, ignoreCase = true) }
            val fileToken = lastSegment
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

internal fun MPVActivity.resumeKey(identity: ResumeIdentity): String {
    return if (identity.fileToken != null)
        "resume:${identity.hash}:${identity.fileToken}"
    else
        "resume:${identity.hash}"
}

internal fun MPVActivity.legacyResumeKey(identity: ResumeIdentity) = "resume:${identity.hash}"

internal fun MPVActivity.saveResumePosition(
    positionMs: Long = psc.position,
    durationMs: Long = psc.duration,
) = synchronized(fileReplacementLock) {
    val identity = if (shouldSavePosition && !suppressEndFileFinishForReplace)
        resumeIdentityFromSource(currentResumeSource)
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
        resumeIdentityFromSource(currentResumeSource)
    else
        null
    return identity?.let {
        val prefs = getDefaultSharedPreferences(applicationContext)
        val raw = prefs.getString(resumeKey(it), null)
        val parts = raw?.split("|").orEmpty()
        val pos = parts.getOrNull(0)?.toLongOrNull()
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        pos?.takeIf { value ->
            value >= RESUME_MIN_POSITION_MS && (dur <= 0L || value < dur - RESUME_NEAR_END_MS)
        }
    }
}

internal fun MPVActivity.clearFinishedPositions() {
    // 1) Custom resume table
    val identity = resumeIdentityFromSource(currentResumeSource)
    if (identity != null) {
        val prefs = getDefaultSharedPreferences(applicationContext)
        prefs.edit()
            .remove(resumeKey(identity))
            .remove(legacyResumeKey(identity))
            .apply()
    }
    // 2) mpv's native watch-later file
    mpvCommand(arrayOf("delete-watch-later-config"))
}

internal fun MPVActivity.pruneResumeTable() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val now = System.currentTimeMillis()
    val entries = prefs.all.asSequence()
        .filter { it.key.startsWith("resume:") }
        .mapNotNull { e ->
            val v = e.value as? String ?: return@mapNotNull null
            val parts = v.split("|")
            if (parts.size < RESUME_ENTRY_PART_COUNT) return@mapNotNull null
            val ts = parts[RESUME_ENTRY_TIMESTAMP_INDEX].toLongOrNull() ?: return@mapNotNull null
            e.key to ts
        }
        .toList()

    val toDelete = mutableListOf<String>()
    val recent = mutableListOf<Pair<String, Long>>()
    for (e in entries) {
        if (now - e.second > RESUME_TABLE_MAX_AGE_MS) toDelete.add(e.first) else recent.add(e)
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
                "(${entries.size - toDelete.size} remain)")
    }
}

/** Format a resume-table millisecond timestamp as MM:SS / H:MM:SS for the toast. */
internal fun MPVActivity.formatResumeTime(ms: Long): String =
    Utils.prettyTime((ms / MILLIS_PER_SECOND_LONG).toInt())
