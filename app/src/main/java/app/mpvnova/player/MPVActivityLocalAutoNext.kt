package app.mpvnova.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.provider.DocumentsContractCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.io.File
import java.util.Locale

internal const val PREF_LOCAL_AUTO_NEXT = "local_auto_next"
internal const val PICKER_RESULT_LOCAL_FILE = "local_file"
private const val EXTERNAL_STORAGE_DOCUMENTS = "com.android.externalstorage.documents"

internal fun localAutoNextComponent(context: Context): ComponentName =
    ComponentName(context.packageName, "app.mpvnova.player.LocalAutoNextPicker")

// Validate the user's local selection before it goes into an Intent. A link outside
// the selected directory can still be played, but must not authorize directory expansion.
internal fun File.canQueueFromSelectedDirectory(): Boolean {
    if (!isAbsolute) return false
    val parent = parentFile?.canonicalFileOrNull()
    val selected = canonicalFileOrNull()
    val sameParent = parent != null && selected?.parentFile == parent
    return sameParent && selected.isFile
}

internal data class LocalAutoNextLaunch(
    val path: String,
    val sourceTitle: String?,
    val itemTitle: String?,
    val fileName: String?,
)

internal fun MPVActivity.prepareLocalAutoNext(filepath: String, appending: Boolean = false) {
    val enabled = getDefaultSharedPreferences(applicationContext).getBoolean(PREF_LOCAL_AUTO_NEXT, true)
    // Check the component on this launch, not an extra or the Activity's original component.
    // The private alias and the exported player can both deliver onNewIntent to this instance.
    val fromPicker = intent.component == localAutoNextComponent(this)
    val autoQueue = enabled && !appending && fromPicker && isLocalAutoNextMedia(filepath) &&
        (intent.data?.let { isLocalFileInDocumentTree(it, filepath) } ?: true)
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
    // Document providers that cannot resolve to a local file retain single-file playback.
    if (!filepath.startsWith('/')) return false
    val extension = filepath.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
    return LOCAL_AUTO_NEXT_EXTENSION_OPTIONS.any { option ->
        mpvGetPropertyString(option)?.splitToSequence(',')?.any { it == extension } == true
    }
}

// An arbitrary document provider can return an fd backed by an unrelated directory.
// Only expand Android's shared-storage trees when the resolved file matches the document ID.
private fun isLocalFileInDocumentTree(uri: Uri, resolvedPath: String): Boolean {
    if (uri.authority != EXTERNAL_STORAGE_DOCUMENTS || !DocumentsContractCompat.isTreeUri(uri)) return false
    return runCatching {
        isLocalFileInStorageTree(
            DocumentsContract.getDocumentId(uri),
            DocumentsContract.getTreeDocumentId(uri),
            resolvedPath,
            Environment.getExternalStorageDirectory().absolutePath,
        )
    }.getOrDefault(false)
}

internal fun isLocalFileInStorageTree(
    document: String,
    tree: String,
    resolvedPath: String,
    primaryRoot: String,
): Boolean {
    val volume = document.substringBefore(':')
    val matchingVolume = ':' in document && ':' in tree && volume == tree.substringBefore(':')
    val namedVolume = volume.isNotEmpty() && !isDotPathSegment(volume) && '/' !in volume
    if (!matchingVolume || !namedVolume) return false
    val filePath = document.substringAfter(':')
    val fileSegmentsValid = filePath.splitToSequence('/').none { it.isEmpty() || isDotPathSegment(it) }
    val treePath = tree.substringAfter(':').trimEnd('/')
    val insideTree = treePath.isEmpty() || filePath.startsWith("$treePath/")
    val storageRoot = if (volume == "primary") primaryRoot else "/storage/$volume"
    return fileSegmentsValid && insideTree && resolvedPath == "$storageRoot/$filePath"
}

private fun isDotPathSegment(segment: String): Boolean = segment == "." || segment == ".."

private val LOCAL_AUTO_NEXT_EXTENSION_OPTIONS = listOf("options/video-exts", "options/audio-exts")
