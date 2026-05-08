package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat

internal fun MPVActivity.safeResolveUri(uri: Uri?): String? {
    return if (uri != null && uri.isHierarchical && !uri.isRelative) resolveUri(uri) else null
}

internal fun MPVActivity.pathFromSendIntent(intent: Intent): String? {
    val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    val textUri = intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Uri.parse(it.trim()) }
    return safeResolveUri(streamUri ?: textUri)
}

internal fun MPVActivity.pathFromMultipleSendIntent(intent: Intent): String? {
    val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    if (uris.isNullOrEmpty())
        return null

    val paths = uris.mapNotNull { uri -> safeResolveUri(uri) }
    return when {
        paths.size == 1 -> paths[0]
        paths.isNotEmpty() -> memoryPlaylistPath(paths)
        else -> null
    }
}

internal fun memoryPlaylistPath(paths: List<String>): String {
    val memoryUri = "memory://#EXTM3U\n${paths.joinToString("\n")}\n"
    Log.v(MPV_ACTIVITY_TAG, "Created memory playlist URI (${paths.size})")
    return memoryUri
}

internal fun MPVActivity.addOnloadOption(key: String, value: String) {
    onloadCommands.add(arrayOf("set", "file-local-options/${key}", value))
}

internal fun MPVActivity.addIntentSubtitles(launchExtras: Bundle) {
    if (!launchExtras.containsKey("subs"))
        return
    val subList = getParcelableArray<Uri>(launchExtras, "subs")
    val subsToEnable = getParcelableArray<Uri>(launchExtras, "subs.enable")
    for (suburi in subList) {
        val subfile = resolveUri(suburi) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"
        Log.v(MPV_ACTIVITY_TAG, "Adding subtitles from intent extras: $subfile")
        onloadCommands.add(arrayOf("sub-add", subfile, flag))
    }
}

internal fun MPVActivity.applyIntentStartPosition(launchExtras: Bundle) {
    val intentPositionMs = launchExtras.getInt("position", 0).toLong()
    val effectivePositionMs = effectiveIntentStartPosition(launchExtras, intentPositionMs)
    pendingStartPositionMs = effectivePositionMs
    if (effectivePositionMs <= 0L)
        return

    addOnloadOption("start", "${effectivePositionMs / MPV_MILLIS_PER_SECOND_FLOAT}")
    if (effectivePositionMs >= RESUME_TOAST_MIN_POSITION_MS) {
        pendingResumeToastMs = effectivePositionMs
        Log.v(
            MPV_ACTIVITY_TAG,
            "resume: queued toast for ${effectivePositionMs}ms " +
                "(source=${if (intentPositionMs > 0L) "intent" else "table"})"
        )
    }
}

private fun MPVActivity.effectiveIntentStartPosition(launchExtras: Bundle, intentPositionMs: Long): Long {
    val intentDurationMs = launchExtras.getInt("duration", 0).toLong()
    val intentNearEnd = intentDurationMs > 0L &&
        intentPositionMs >= intentDurationMs - RESUME_NEAR_END_MS
    return when {
        intentPositionMs > 0L && !intentNearEnd -> intentPositionMs
        intentPositionMs <= 0L -> loadResumePosition() ?: 0L
        else -> 0L
    }
}
