package app.mpvnova.player

import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.io.File
import java.util.Locale

internal const val PREF_LOCAL_AUTO_NEXT = "local_auto_next"

internal data class LocalAutoNextLaunch(
    val path: String,
    val sourceTitle: String?,
    val itemTitle: String?,
    val fileName: String?,
)

internal fun MPVActivity.prepareLocalAutoNext(filepath: String, appending: Boolean = false) {
    val enabled = getDefaultSharedPreferences(applicationContext).getBoolean(PREF_LOCAL_AUTO_NEXT, true)
    val externalResult = intent.getBooleanExtra(EXTRA_EXTERNAL_PLAYER_RESULT, false)
    val localFile = isLocalAutoNextMedia(filepath)
    val autoQueue = enabled && !externalResult && !appending && localFile
    localAutoNextLaunch = if (autoQueue) {
        LocalAutoNextLaunch(filepath, pendingPlayerTitleSource, pendingItemTitle, pendingFileName)
    } else null
    val directoryMode = localAutoNextDirectoryMode
        ?: (mpvGetPropertyString("directory-mode") ?: "auto").also { localAutoNextDirectoryMode = it }
    // Let mpv own ordering, playlist advancement, and watch-later state. External callers
    // own their next-episode flow; never expand their single-item playback into a folder.
    mpvSetPropertyString("autocreate-playlist", if (autoQueue) "same" else "no")
    mpvSetPropertyString("directory-mode", if (autoQueue) "ignore" else directoryMode)
}

internal fun MPVActivity.restoreLocalAutoNextLaunchTitle() {
    val launch = localAutoNextLaunch ?: return
    // Playlist expansion can START_FILE twice before FILE_LOADED. Retain the launch
    // metadata for that first real file, but never carry it into subsequent episodes.
    if (mpvGetPropertyString("path") == launch.path) {
        pendingPlayerTitleSource = launch.sourceTitle
        pendingItemTitle = launch.itemTitle
        pendingFileName = launch.fileName
    }
}

private fun isLocalAutoNextMedia(filepath: String): Boolean {
    val file = File(filepath)
    val extension = file.extension.lowercase(Locale.ROOT)
    val mediaExtensions = listOf("video-exts", "audio-exts").flatMap {
        mpvGetPropertyString("options/$it")?.split(',').orEmpty()
    }
    return filepath.startsWith('/') && file.isFile && extension in mediaExtensions
}
