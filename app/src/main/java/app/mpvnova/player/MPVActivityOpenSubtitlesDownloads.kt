package app.mpvnova.player

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import app.mpvnova.player.databinding.DialogSettingsChoiceBinding
import java.io.File
import java.util.concurrent.Executors

private val SUBTITLE_REMOVAL_WORKER = Executors.newSingleThreadExecutor { task ->
    Thread(task, "OpenSubtitles-Remove").apply { isDaemon = true }
}

internal fun discardOpenSubtitlesDownload(file: File, cacheDir: File) {
    SUBTITLE_REMOVAL_WORKER.execute { OpenSubtitlesDownloadUi.discard(file, cacheDir) }
}

internal fun openSubtitlesFirstTrackId(file: File): Int? {
    val count = mpvGetPropertyInt("track-list/count") ?: return null
    val path = file.absolutePath
    return (0 until count).firstNotNullOfOrNull { index ->
        val prefix = "track-list/$index"
        if (mpvGetPropertyString("$prefix/type") == "sub" &&
            mpvGetPropertyString("$prefix/external-filename") == path) {
            mpvGetPropertyInt("$prefix/id")
        } else null
    }
}

internal fun MPVActivity.completeOpenSubtitlesAdd(requestId: Long, status: Long) {
    val file = pendingOpenSubtitlesCommands.remove(requestId) ?: return
    pendingOpenSubtitlesAdds.remove(file)
    if (status < 0L) {
        pendingOpenSubtitlesRemovals.remove(file)
        pendingOpenSubtitlesRemoveCommands.remove(file)
        openSubtitlesPlaybackFiles.remove(file)
        openSubtitlesPlaybackDownloads.entries.removeAll { it.value.first == file }
        if (pendingOpenSubtitlesFocus == file) pendingOpenSubtitlesFocus = null
        discardOpenSubtitlesDownload(file, cacheDir)
        if (!isFinishing && !isDestroyed) showToast(getString(R.string.os_downloads_load_failed))
    }
    finishOpenSubtitlesRemovals()
    refreshSubPickerAfterTrackChange()
}

internal fun MPVActivity.finishOpenSubtitlesRemovals() {
    if (pendingOpenSubtitlesAdds.isEmpty() && pendingOpenSubtitlesRemovals.isEmpty()) return
    val paths = (pendingOpenSubtitlesAdds + pendingOpenSubtitlesRemovals).map { it.absolutePath }.toSet()
    val snapshot = openSubtitlesTrackSnapshot(paths)
    pendingOpenSubtitlesAdds.removeAll { snapshot[it.absolutePath].orEmpty().isNotEmpty() }
    if (pendingOpenSubtitlesRemovals.isEmpty()) return
    val removed = mutableListOf<File>()
    for (file in pendingOpenSubtitlesRemovals) {
        if (file in pendingOpenSubtitlesAdds) continue
        val tracks = snapshot[file.absolutePath].orEmpty()
        if (tracks.isEmpty()) removed += file
        else if (pendingOpenSubtitlesRemoveCommands.add(file)) {
            tracks.forEach { mpvCommand(arrayOf("async", "sub-remove", it.toString())) }
        }
    }
    for (file in removed) {
        pendingOpenSubtitlesRemovals.remove(file)
        pendingOpenSubtitlesRemoveCommands.remove(file)
        openSubtitlesPlaybackFiles.remove(file)
        openSubtitlesPlaybackDownloads.entries.removeAll { it.value.first == file }
        if (pendingOpenSubtitlesFocus == file) pendingOpenSubtitlesFocus = null
        discardOpenSubtitlesDownload(file, cacheDir)
    }
}

private fun MPVActivity.openSubtitlesTrackSnapshot(paths: Set<String>): Map<String, List<Int>> {
    val snapshot = mutableMapOf<String, MutableList<Int>>()
    val count = mpvGetPropertyInt("track-list/count") ?: 0
    for (index in 0 until count) {
        val prefix = "track-list/$index"
        if (mpvGetPropertyString("$prefix/type") == "sub") {
            val path = mpvGetPropertyString("$prefix/external-filename")
            if (path != null && path in paths) {
                mpvGetPropertyInt("$prefix/id")?.let { id ->
                    snapshot.getOrPut(path) { mutableListOf() }.add(id)
                }
            }
        }
    }
    return snapshot
}

private fun MPVActivity.removeOpenSubtitlesDownload(id: Int, file: File) {
    if (openSubtitlesPlaybackDownloads[id]?.first != file) return
    pendingOpenSubtitlesRemovals += file
    finishOpenSubtitlesRemovals()
}

internal fun MPVActivity.showOpenSubtitlesDownloads(
    restore: StateRestoreCallback = keepPlaybackForDialog(),
    onDismiss: () -> Unit,
) {
    val binding = DialogSettingsChoiceBinding.inflate(layoutInflater)
    binding.choiceTitle.setText(R.string.os_downloads_title)
    binding.choiceScroll.maxHeightPx = Utils.convertDp(this, DOWNLOAD_LIST_MAX_HEIGHT_DP)
    binding.choiceCancelBtn.setText(R.string.os_search_close)
    val dialog = AlertDialog.Builder(this).setView(binding.root).create()
    dialog.setOnDismissListener {
        try { onDismiss() } finally { restore() }
    }
    binding.choiceCancelBtn.setOnClickListener { dialog.dismiss() }
    val downloads = openSubtitlesPlaybackDownloads.toMap()
    if (downloads.isEmpty()) {
        binding.choiceMessage.setText(R.string.os_downloads_empty)
        binding.choiceMessage.visibility = View.VISIBLE
    }
    for ((id, download) in downloads) {
        val (file, result) = download
        val row = layoutInflater.inflate(R.layout.dialog_setting_option_item, binding.choiceRows, false)
        row.findViewById<TextView>(R.id.optionTitleText).text = result.release.ifBlank { result.fileName }
        row.findViewById<TextView>(R.id.optionDetailText).text = getString(R.string.os_downloads_remove)
        row.findViewById<ImageView>(R.id.optionCheck).apply {
            setImageResource(R.drawable.ic_style_delete)
            setContentDescription(getString(R.string.os_downloads_remove))
            visibility = View.VISIBLE
        }
        row.setOnClickListener {
            removeOpenSubtitlesDownload(id, file)
            dialog.dismiss()
        }
        binding.choiceRows.addView(row)
    }
    UiFont.applyToViewTree(binding.root)
    showWidePlayerDialog(dialog, PlayerDialogLayout(widthFraction = 0.55f, maxWidthDp = 560f))
    binding.choiceRows.getChildAt(0)?.post { binding.choiceRows.getChildAt(0)?.requestFocus() }
}

private const val DOWNLOAD_LIST_MAX_HEIGHT_DP = 240f
