package app.mpvnova.player

import android.view.KeyEvent
import android.view.View
import androidx.core.view.isVisible

internal fun MPVActivity.interceptDpadWithoutControls(ev: KeyEvent): Boolean {
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (ev.action == KeyEvent.ACTION_DOWN) {
                showControls()
                val controls = dpadButtons()
                if (controls.isNotEmpty()) {
                    activateDpadSelection(ev, controls)
                    requestFirstControlFocusIfNeeded(ev)
                }
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (ev.action == KeyEvent.ACTION_DOWN) {
                showControls()
                btnSelected = 0
                updateSelectedDpadButton()
                binding.playbackSeekbar.requestFocus()
                seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
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

internal fun MPVActivity.requestFirstControlFocusIfNeeded(ev: KeyEvent) {
    if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        firstControlButtonView()?.requestFocus()
    binding.controls.post {
        if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
            updateSelectedDpadButton()
            if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                firstControlButtonView()?.requestFocus()
        }
    }
}

internal fun MPVActivity.interceptDpadActivation(ev: KeyEvent, controls: List<View>): Boolean {
    if (ev.keyCode !in arrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        return false
    if (ev.action == KeyEvent.ACTION_DOWN) {
        activateDpadSelection(ev, controls)
        requestFirstControlFocusIfNeeded(ev)
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
        when {
            ev.keyCode == KeyEvent.KEYCODE_DPAD_UP && !seekbarSelected -> btnSelected = 0
            ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && seekbarSelected && controls.size > 1 -> btnSelected = 1
            else -> btnSelected = -1
        }
        updateSelectedDpadButton()
        if (btnSelected == -1) hideControlsFade() else showControls()
    }
    return true
}

internal fun MPVActivity.handleHorizontalDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    if (ev.action == KeyEvent.ACTION_DOWN) {
        if (seekbarSelected) {
            seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
        } else {
            val direction = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
            val count = controls.count()
            btnSelected = (count + btnSelected + direction) % count
            updateSelectedDpadButton()
        }
        showControls()
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
