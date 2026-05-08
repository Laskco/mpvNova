package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

internal fun MPVActivity.addExternalThing(cmd: String, result: Int, data: Intent?) {
    if (result != RESULT_OK)
        return
    val path = data!!.getStringExtra("path")!!
    val resolvedPath = if (path.startsWith("content://")) translateContentUri(Uri.parse(path)) else path
    mpvCommand(arrayOf(cmd, resolvedPath, "cached"))
}

internal fun MPVActivity.topMenuItems(restoreState: StateRestoreCallback): MutableList<MenuItem> {
    return mutableListOf(
        MenuItem(R.id.audioBtn) { openExternalAudio(restoreState) },
        MenuItem(R.id.subBtn) { openExternalSubtitle(restoreState) },
        MenuItem(R.id.playlistBtn) { openPlaylistMenu(restoreState); false },
        MenuItem(R.id.backgroundBtn) { sendPlaybackToBackground(restoreState) },
        MenuItem(R.id.chapterBtn) { openChapterMenu(restoreState) },
        MenuItem(R.id.chapterPrev) { seekChapterRelative(-1); true },
        MenuItem(R.id.chapterNext) { seekChapterRelative(1); true },
        MenuItem(R.id.advancedBtn) { openAdvancedMenu(restoreState); false },
        MenuItem(R.id.orientationBtn) {
            autoRotationMode = "manual"
            cycleOrientation()
            true
        }
    )
}

private fun MPVActivity.openExternalAudio(restoreState: StateRestoreCallback): Boolean {
    openFilePickerFor(R.string.open_external_audio) { result, data ->
        addExternalThing("audio-add", result, data)
        restoreState()
    }
    return false
}

private fun MPVActivity.openExternalSubtitle(restoreState: StateRestoreCallback): Boolean {
    openFilePickerFor(R.string.open_external_sub) { result, data ->
        addExternalThing("sub-add", result, data)
        restoreState()
    }
    return false
}

private fun MPVActivity.sendPlaybackToBackground(restoreState: StateRestoreCallback): Boolean {
    restoreState()
    backgroundPlayMode = "always"
    player.paused = false
    moveTaskToBack(true)
    return false
}

private fun MPVActivity.openChapterMenu(restoreState: StateRestoreCallback): Boolean {
    val chapters = player.loadChapters()
    if (chapters.isEmpty())
        return true
    val chapterArray = chapters.map {
        val timecode = Utils.prettyTime(it.time.roundToInt())
        if (!it.title.isNullOrEmpty())
            getString(R.string.ui_chapter, it.title, timecode)
        else
            getString(R.string.ui_chapter_fallback, it.index + 1, timecode)
    }.toTypedArray()
    val selectedIndex = mpvGetPropertyInt("chapter") ?: 0
    with(AlertDialog.Builder(this)) {
        setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
            mpvSetPropertyInt("chapter", chapters[item].index)
            dialog.dismiss()
        }
        setOnDismissListener { restoreState() }
        create().show()
    }
    return false
}

internal fun MPVActivity.topMenuHiddenButtons(): MutableSet<Int> {
    val hiddenButtons = mutableSetOf<Int>()
    if (!isPlayingAudio)
        hiddenButtons.add(R.id.backgroundBtn)
    if ((mpvGetPropertyInt("chapter-list/count") ?: 0) == 0)
        hiddenButtons.add(R.id.rowChapter)
    return hiddenButtons
}
