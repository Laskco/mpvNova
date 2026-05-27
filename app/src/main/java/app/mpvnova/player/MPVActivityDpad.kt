package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible

internal fun MPVActivity.dpadButtons(): List<View> {
    if (binding.controls.visibility != View.VISIBLE || binding.topControls.visibility != View.VISIBLE) {
        dpadControlsScratch.clear()
        return emptyList()
    }
    val views = dpadControlsScratch
    views.clear()
    if (binding.playbackSeekbar.isEnabled) {
        views += binding.playbackSeekbar
    }
    views.addFocusableChildren(binding.controlsButtonGroup)
    views.addFocusableChildren(binding.topControls)
    return views
}

private fun MutableList<View>.addFocusableChildren(group: ViewGroup) {
    for (i in 0 until group.childCount) {
        val view = group.getChildAt(i)
        if (view.isEnabled && view.isVisible && view.isFocusable) {
            this += view
        }
    }
}

internal fun MPVActivity.firstControlButtonIndex(controls: List<View>): Int {
    val firstNonSeekbar = controls.indexOfFirst { it !== binding.playbackSeekbar }
    return if (firstNonSeekbar >= 0) firstNonSeekbar else 0
}

internal fun MPVActivity.firstControlButtonView(): View? {
    findFirstFocusableChild(binding.controlsButtonGroup)?.let { return it }
    return findFirstFocusableChild(binding.topControls)
}

private fun findFirstFocusableChild(group: ViewGroup): View? {
    for (i in 0 until group.childCount) {
        val child = group.getChildAt(i)
        if (child.isEnabled && child.isVisible && child.isFocusable) {
            return child
        }
    }
    return null
}

internal fun MPVActivity.interceptDpad(ev: KeyEvent): Boolean {
    val controls = dpadButtons()
    return when {
        btnSelected == -1 && controls.isEmpty() -> interceptDpadWithoutControls(ev)
        controls.isEmpty() -> false
        btnSelected == -1 -> interceptDpadActivation(ev, controls)
        else -> interceptActiveDpad(ev, controls)
    }
}

internal fun MPVActivity.updateSelectedDpadButton() {
    // Selection lives on btnSelected, not framework focus. isSelected drives
    // state_selected in the drawable; requestFocus() would fire a11y events
    // + scheduleTraversals() per press → SW Hi10p decoder drift.
    val controls = dpadButtons()
    controls.forEachIndexed { i, child ->
        val selected = i == btnSelected
        if (child.isSelected != selected) {
            child.isSelected = selected
        }
        if (child is ChapterSeekBar) {
            child.setDpadSelected(selected)
        }
    }
}

internal fun MPVActivity.interceptKeyDown(event: KeyEvent): Boolean {
    // Override libmpv's defaults for mpvNova-specific behavior.
    var unhandled = 0

    when (event.unicodeChar.toChar()) {
        'j' -> cycleSub()
        '#' -> cycleAudio()
        else -> unhandled++
    }
    // Enter + numpad-enter must do the same thing (issue #963).
    when (event.keyCode) {
        KeyEvent.KEYCODE_CAPTIONS -> cycleSub()
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycleAudio()
        KeyEvent.KEYCODE_INFO -> toggleControls()
        KeyEvent.KEYCODE_MENU -> openPlayerDrawer()
        KeyEvent.KEYCODE_GUIDE -> openPlayerDrawer()
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> player.cyclePause()
        KeyEvent.KEYCODE_ENTER -> player.cyclePause()
        else -> unhandled++
    }

    return unhandled < 2
}

internal fun MPVActivity.onBackPressedImpl() {
    // Double-back: first press toasts, second within window exits.
    // Skipped when the playlist warning already gates the exit.
    val notYetPlayed = psc.playlistCount - psc.playlistPos - 1
    val playlistConfirmsExit = notYetPlayed > 0 && playlistExitWarning
    if (exitWithDoubleBack && !playlistConfirmsExit) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBackPressMs > DOUBLE_BACK_WINDOW_MS) {
            lastBackPressMs = now
            showToast(getString(R.string.exit_double_back_hint))
            return
        }
        lastBackPressMs = 0L
    }

    if (!playlistConfirmsExit) {
        finishWithResult(RESULT_OK, true)
        return
    }

    val restore = pauseForDialog()
    with (AlertDialog.Builder(this)) {
        setMessage(getString(R.string.exit_warning_playlist, notYetPlayed))
        setPositiveButton(R.string.dialog_yes) { dialog, _ ->
            dialog.dismiss()
            finishWithResult(RESULT_OK, true)
        }
        setNegativeButton(R.string.dialog_no) { dialog, _ ->
            dialog.dismiss()
            restore()
        }
        create().show()
    }
}
