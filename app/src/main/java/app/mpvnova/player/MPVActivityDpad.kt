package app.mpvnova.player

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible

internal fun MPVActivity.dpadButtons(): List<View> {
    if (
        binding.controls.visibility != View.VISIBLE ||
        (!topActionsInPlayerBar && binding.topControls.visibility != View.VISIBLE)
    ) {
        dpadControlsScratch.clear()
        return emptyList()
    }
    val views = dpadControlsScratch
    views.clear()
    if (binding.playbackSeekbar.isEnabled && binding.playbackSeekbar.isVisible) {
        views += binding.playbackSeekbar
    }
    views.addFocusableChildren(binding.controlsButtonGroup)
    if (!topActionsInPlayerBar) {
        views.addFocusableChildren(binding.topControls)
    }
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

internal fun MPVActivity.upperPlayerBarControlIndex(controls: List<View>): Int =
    if (playerUiCustomization.seekbarPosition == PlayerSeekbarPosition.BELOW) {
        firstControlButtonIndex(controls)
    } else {
        controls.indexOf(binding.playbackSeekbar).takeIf { it >= 0 } ?: firstControlButtonIndex(controls)
    }

internal fun MPVActivity.topControlJumpIndex(controls: List<View>): Int =
    if (!dpadUpJumpsToTopControls || topActionsInPlayerBar) {
        -1
    } else {
        controls.indexOf(binding.topPiPBtn).takeIf { it >= 0 }
            ?: controls.indexOf(binding.topMenuBtn).takeIf { it >= 0 }
            ?: -1
    }

internal fun MPVActivity.activateOrHideControlsFromVerticalDpad(
    ev: KeyEvent,
    controls: List<View>,
): Boolean {
    if (ev.action != KeyEvent.ACTION_DOWN) return true
    if (ev.keyCode == KeyEvent.KEYCODE_DPAD_UP && binding.playbackSeekbar !in controls) {
        hideControlsFade()
    } else {
        activateDpadSelection(ev, controls)
        requestFirstControlFocusIfNeeded()
        showControls()
    }
    return true
}

internal fun MPVActivity.interceptDpad(ev: KeyEvent): Boolean {
    val controls = dpadButtons()
    if (controls.isEmpty() && btnSelected == SKIP_BUTTON_SELECTION_INDEX) {
        btnSelected = -1
        syncSkipButtonHighlight()
    }
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
    syncSkipButtonHighlight()
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

internal fun MPVActivity.parkPlayerFrameworkFocus() {
    // Player control highlights are driven by btnSelected. Keep Android focus on
    // the neutral surface so a dismissed dialog cannot also highlight a button.
    binding.outside.isFocusable = true
    binding.outside.requestFocus()
}

internal fun MPVActivity.interceptKeyDown(event: KeyEvent): Boolean {
    if (event.isRepeatedPlayerConfirmKey()) return true

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
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> togglePauseFromUser()
        KeyEvent.KEYCODE_ENTER -> togglePauseFromUser()
        else -> unhandled++
    }

    return unhandled < 2
}

internal fun KeyEvent.isRepeatedPlayerConfirmKey(): Boolean =
    action == KeyEvent.ACTION_DOWN && repeatCount > 0 && when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> false
    }
