package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Chapter navigation + the chapter-marker drawing on the seekbar + the
 * full-list chapter picker dialog.
 *
 * Chapter skip toasts cover three cases:
 *   - normal skip → "Skipped to MM:SS - <title>"
 *   - at/past the boundary → "No next chapter" / "No previous chapter"
 *   - file has no chapters → "No chapter markers in this file"
 *
 * All three are emitted when the caller passes showFeedback = true (the
 * default), so the on-screen chapter button and the remote-mapped
 * shortcut both behave the same.
 */

internal fun MPVActivity.seekChapterRelative(direction: Int, showFeedback: Boolean = true) {
    val chapters = cachedChapters.ifEmpty {
        player.loadChapters().also { cachedChapters = it }
    }
    if (chapters.isEmpty()) {
        mpvCommand(arrayOf("add", "chapter", direction.toString()))
        if (showFeedback) showChapterUnavailableToast()
        return
    }

    val referenceTime = pendingChapterSeekTime
        ?: mpvGetPropertyDouble("time-pos/full")
        ?: (psc.position / MPV_MILLIS_PER_SECOND_DOUBLE)

    val target = if (direction > 0) {
        chapters.firstOrNull { it.time > referenceTime + CHAPTER_SKIP_EPSILON_SEC }
    } else {
        chapters.lastOrNull { it.time < referenceTime - CHAPTER_SKIP_EPSILON_SEC }
    }

    if (target == null) {
        pendingChapterSeekTime = null
        mpvCommand(arrayOf("add", "chapter", direction.toString()))
        if (showFeedback) showChapterBoundaryToast(direction)
        return
    }

    pendingChapterSeekTime = target.time
    eventUiHandler.removeCallbacks(clearPendingChapterSeek)
    eventUiHandler.postDelayed(clearPendingChapterSeek, CHAPTER_SEEK_MEMORY_MS)

    mpvCommand(arrayOf("seek", target.time.toString(), "absolute+exact"))
    val targetMs = (target.time * MPV_MILLIS_PER_SECOND_DOUBLE).roundToLong().coerceAtLeast(0L)
    setPlaybackSeekbarProgress(seekbarProgressFromMillis(targetMs))
    updatePlaybackTimeline(targetMs, forceTextUpdate = true)
    if (showFeedback) {
        showChapterSkipToast(target, targetMs)
    }
}

private fun MPVActivity.showChapterSkipToast(chapter: MPVView.Chapter, targetMs: Long) {
    val chapterTitle = chapter.title?.takeIf { it.isNotBlank() }
        ?: "${getString(R.string.chapter_button)} ${chapter.index + 1}"
    showToast(
        getString(R.string.btn_next_chapter),
        getString(
            R.string.toast_next_chapter_detail,
            Utils.prettyTime((targetMs / MILLIS_PER_SECOND_LONG).toInt()),
            chapterTitle
        ),
        cancel = false
    )
}

private fun MPVActivity.showChapterBoundaryToast(direction: Int) {
    val detail = if (direction > 0)
        getString(R.string.toast_no_next_chapter)
    else
        getString(R.string.toast_no_previous_chapter)
    showToast(
        getString(R.string.btn_next_chapter),
        detail,
        cancel = false
    )
}

private fun MPVActivity.showChapterUnavailableToast() {
    showToast(
        getString(R.string.btn_next_chapter),
        getString(R.string.toast_no_chapters_in_file),
        cancel = false
    )
}

internal fun MPVActivity.updateChapterMarkers() {
    val duration = psc.durationSec
    val chapters = player.loadChapters()
    cachedChapters = chapters
    val hasChapters = chapters.isNotEmpty()

    binding.nextChapterBtn.visibility = if (hasChapters) View.VISIBLE else View.GONE

    if (!hasChapters || duration <= 0) {
        binding.playbackSeekbar.clearChapters()
        return
    }

    binding.playbackSeekbar.setChapters(chapters.map { it.time }, duration.toDouble())
}

internal fun MPVActivity.showChapterPickerDialog() {
    val chapters = player.loadChapters()
    if (chapters.isEmpty()) return
    val restore = keepPlaybackForDialog()
    val items = chapters.map { ch ->
        val timecode = Utils.prettyTime(ch.time.roundToInt())
        val title = ch.title?.takeIf { it.isNotBlank() }
            ?: "${getString(R.string.chapter_button)} ${ch.index + 1}"
        ChapterPickerDialog.Item(ch.index, title, timecode)
    }
    val selected = mpvGetPropertyInt("chapter") ?: 0
    val impl = ChapterPickerDialog(items, selected)
    lateinit var dialog: AlertDialog
    impl.onItemPicked = { item ->
        mpvSetPropertyInt("chapter", item.index)
        dialog.dismiss()
    }
    impl.onCancelClick = { dialog.cancel() }
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater))
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.46f,
            maxWidthDp = 560f,
            heightFraction = 0.62f,
            maxHeightDp = 540f,
        )
    )
}
