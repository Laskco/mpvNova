package app.mpvnova.player

import android.view.KeyEvent
import android.view.View

internal fun MPVActivity.interceptDpadWithoutControls(ev: KeyEvent): Boolean {
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (ev.action == KeyEvent.ACTION_DOWN) {
                showControls()
                val controls = dpadButtons()
                if (controls.isNotEmpty()) {
                    activateDpadSelection(ev, controls)
                    requestFirstControlFocusIfNeeded()
                }
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            when (ev.action) {
                KeyEvent.ACTION_DOWN -> {
                    showControls()
                    btnSelected = 0
                    // No requestFocus — setDpadSelected drives the highlight;
                    // framework focus would trigger window-wide traversal.
                    updateSelectedDpadButton()
                    seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                }
                KeyEvent.ACTION_UP -> commitPendingSeekbarSeek()
            }
            true
        }
        else -> false
    }
}

internal fun MPVActivity.activateDpadSelection(ev: KeyEvent, controls: List<View>) {
    btnSelected = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) firstControlButtonIndex(controls) else 0
    updateSelectedDpadButton()
}

internal fun MPVActivity.requestFirstControlFocusIfNeeded() {
    // No framework requestFocus — isSelected drives the highlight via
    // state_selected. Just defer a refresh until after the visibility-pass layout.
    binding.controls.post {
        if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
            updateSelectedDpadButton()
        }
    }
}

internal fun MPVActivity.interceptDpadActivation(ev: KeyEvent, controls: List<View>): Boolean {
    if (ev.keyCode != KeyEvent.KEYCODE_DPAD_UP && ev.keyCode != KeyEvent.KEYCODE_DPAD_DOWN)
        return false
    if (ev.action == KeyEvent.ACTION_DOWN) {
        activateDpadSelection(ev, controls)
        requestFirstControlFocusIfNeeded()
        showControls()
    }
    return true
}

internal fun MPVActivity.interceptActiveDpad(ev: KeyEvent, controls: List<View>): Boolean {
    val selectedView = controls.getOrNull(btnSelected)
    val seekbarSelected = selectedView === binding.playbackSeekbar
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
            handleVerticalDpad(ev, seekbarSelected, controls)
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT ->
            handleHorizontalDpad(ev, seekbarSelected, controls)
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
            handleCenterDpad(ev, seekbarSelected, controls)
        else -> false
    }
}

internal fun MPVActivity.handleVerticalDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    if (ev.action == KeyEvent.ACTION_DOWN) {
        if (seekbarSelected)
            commitPendingSeekbarSeek()
        btnSelected = nextSelectionForVerticalDpad(ev, seekbarSelected, controls)
        updateSelectedDpadButton()
        if (btnSelected == -1) hideControlsFade() else showControls()
    }
    return true
}

@Suppress("ReturnCount")
private fun MPVActivity.nextSelectionForVerticalDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>,
): Int {
    val current = controls.getOrNull(btnSelected)
    if (current === binding.topMenuBtn || current === binding.topPiPBtn) {
        // Top control: DOWN → seekbar, UP → exit.
        return if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 0 else -1
    }
    val isUp = ev.keyCode == KeyEvent.KEYCODE_DPAD_UP
    if (seekbarSelected) {
        // Seekbar: DOWN → first bottom button. UP hides, or jumps to top icons
        // when dpad_up_jumps_to_top_controls is on. Prefer PiP (leftmost).
        if (!isUp) return if (controls.size > 1) 1 else -1
        if (!dpadUpJumpsToTopControls) return -1
        return controls.indexOf(binding.topPiPBtn).takeIf { it >= 0 }
            ?: controls.indexOf(binding.topMenuBtn).takeIf { it >= 0 }
            ?: -1
    }
    // Bottom button: UP → seekbar, DOWN → hide.
    return if (isUp) 0 else -1
}

internal fun MPVActivity.handleHorizontalDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    when (ev.action) {
        KeyEvent.ACTION_DOWN -> {
            if (seekbarSelected) {
                seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                keepVisibleControlsFresh()
            } else {
                val direction = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                val count = controls.size
                btnSelected = (count + btnSelected + direction) % count
                updateSelectedDpadButton()
                // Skip showControls() — already visible. Just bump the timer:
                // at ~10 presses/sec the alpha/clock/post work would starve
                // the SW Hi10p decoder.
                refreshVisibleControlsTimeout()
            }
        }
        KeyEvent.ACTION_UP -> {
            if (seekbarSelected) {
                commitPendingSeekbarSeek()
                keepVisibleControlsFresh()
            } else {
                // Selection/visibility already set by ACTION_DOWN — just bump the timer.
                refreshVisibleControlsTimeout()
            }
        }
    }
    return true
}

internal fun MPVActivity.handleCenterDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    if (seekbarSelected)
        return false

    return when (ev.action) {
        KeyEvent.ACTION_DOWN -> {
            if (ev.repeatCount == 0)
                scheduleDpadLongClick(controls.getOrNull(btnSelected))
            showControls()
            true
        }
        KeyEvent.ACTION_UP -> {
            val view = controls.getOrNull(btnSelected)
            cancelPendingDpadLongClick()
            if (!dpadLongClickPerformed)
                view?.performClick()
            dpadLongClickPerformed = false
            showControls()
            true
        }
        else -> true
    }
}

private fun MPVActivity.scheduleDpadLongClick(view: View?) {
    cancelPendingDpadLongClick()
    dpadLongClickPerformed = false
    if (view == null || !view.isLongClickable)
        return

    val runnable = Runnable {
        if (pendingDpadLongClickView === view && view.performLongClick()) {
            dpadLongClickPerformed = true
            showControls()
        }
        pendingDpadLongClickView = null
        pendingDpadLongClickRunnable = null
    }
    pendingDpadLongClickView = view
    pendingDpadLongClickRunnable = runnable
    view.postDelayed(runnable, DPAD_LONG_PRESS_MS)
}

private fun MPVActivity.cancelPendingDpadLongClick() {
    val view = pendingDpadLongClickView
    val runnable = pendingDpadLongClickRunnable
    if (view != null && runnable != null)
        view.removeCallbacks(runnable)
    pendingDpadLongClickView = null
    pendingDpadLongClickRunnable = null
}
