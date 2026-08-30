package app.mpvnova.player

import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Bundle
import java.io.FileNotFoundException

internal fun MPVActivity.prepareMediaTitleFromIntent(intent: Intent?, filepath: String?) {
    pendingPlayerTitleSource = titleSourceFromIntentExtras(intent)
    pendingItemTitle = pendingPlayerTitleSource
        ?.let(VlcTitleResolver::itemTitleFromExtra)
        ?: VlcTitleResolver.queryTitleFromPathLike(filepath)
    pendingFileName = VlcTitleResolver.fileNameFromPathLike(filepath)
}

internal fun MPVActivity.titleSourceFromIntentExtras(intent: Intent?): String? {
    val extras = intent?.extras ?: return null
    return VLC_TITLE_EXTRA_KEYS.firstNotNullOfOrNull { key ->
        val title = if (extras.containsKey(key)) {
            extras.getString(key) ?: extras.getCharSequence(key)?.toString()
        } else {
            null
        }
        VlcTitleResolver.titleSourceFromExtra(title)
    }
}

internal fun MPVActivity.titleFromIntentExtras(intent: Intent?): String? {
    return titleSourceFromIntentExtras(intent)?.let(VlcTitleResolver::itemTitleFromExtra)
}

internal fun MPVActivity.resolveVlcStyleVideoTitle(): String? {
    currentItemTitle?.let { return it }
    val path = currentMpvPath()
    val fileName = pendingFileName ?: VlcTitleResolver.fileNameFromPathLike(path)
    return VlcTitleResolver.resolve(
        itemTitle = null,
        mediaTitle = psc.meta.mediaTitle,
        fileName = fileName,
        isStream = isNetworkStreamPath(path)
    )
}

internal fun MPVActivity.parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data?.let { resolveUri(it) }
        else -> intent.getStringExtra("filepath")
    }
}

internal fun MPVActivity.resolveUri(data: Uri): String? {
    val filepath = when (data.scheme) {
        "file" -> data.path
        "content" -> translateContentUri(data)
        // mpv supports data URIs but needs data:// to pass it through correctly
        "data" -> "data://${data.schemeSpecificPart}"
        "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh",
        "tcp", "udp", "lavf", "ftp"
        -> encodeUnsafeNetworkUrlCharacters(data.toString())
        else -> null
    }

    if (filepath == null)
        Log.e(MPV_ACTIVITY_TAG, "unknown scheme: ${data.scheme}")
    return filepath
}

internal fun encodeUnsafeNetworkUrlCharacters(value: String): String {
    if (value.none(::isUnsafeNetworkUrlCharacter)) return value

    return buildString(value.length) {
        value.forEach { character ->
            if (isUnsafeNetworkUrlCharacter(character)) {
                val code = character.code
                append('%')
                append(URL_HEX_DIGITS[code ushr URL_NIBBLE_BITS])
                append(URL_HEX_DIGITS[code and URL_LOW_NIBBLE_MASK])
            } else {
                append(character)
            }
        }
    }
}

private fun isUnsafeNetworkUrlCharacter(character: Char): Boolean =
    character.code <= ASCII_CONTROL_OR_SPACE_MAX || character.code == ASCII_DELETE

private const val URL_HEX_DIGITS = "0123456789ABCDEF"
private const val URL_NIBBLE_BITS = 4
private const val URL_LOW_NIBBLE_MASK = 0x0f
private const val ASCII_CONTROL_OR_SPACE_MAX = 0x20
private const val ASCII_DELETE = 0x7f

internal fun MPVActivity.translateContentUri(uri: Uri): String {
    Log.v(MPV_ACTIVITY_TAG, "Resolving content URI: $uri")
    try {
        contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            Utils.findRealPath(fd.fd)?.let { return it }
        }
    } catch (e: FileNotFoundException) {
        Log.v(MPV_ACTIVITY_TAG, "Content URI is not backed by a readable file descriptor", e)
    } catch (e: SecurityException) {
        Log.v(MPV_ACTIVITY_TAG, "No permission to inspect content URI file descriptor", e)
    }
    return uri.toString()
}

internal fun MPVActivity.parseIntentExtras(extras: Bundle?) {
    onloadCommands.clear()
    val launchExtras = extras ?: Bundle.EMPTY

    if (resumeIdentityFromSource(currentResumeSource) != null)
        addOnloadOption("resume-playback", "no")

    // Note: these only apply to the first file, it's not clear what the semantics for a
    // playlist should be.
    if (launchExtras.getByte("decode_mode") == 2.toByte())
        addOnloadOption("hwdec", "no")

    addIntentSubtitles(launchExtras)
    applyIntentStartPosition(launchExtras)
    parseSkipSegments(launchExtras)
}

internal fun MPVActivity.trackSwitchNotification(f: () -> TrackData) {
    val (track_id, track_type) = f()
    val trackPrefix = when (track_type) {
        "audio" -> getString(R.string.track_audio)
        "sub"   -> getString(R.string.track_subs)
        "video" -> "Video"
        else    -> "???"
    }

    val detail = if (track_id == -1) {
        getString(R.string.track_off)
    } else {
        player.tracks[track_type]?.firstOrNull{ it.mpvId == track_id }?.name ?: "???"
    }
    showToast(trackPrefix, detail, true)
}
