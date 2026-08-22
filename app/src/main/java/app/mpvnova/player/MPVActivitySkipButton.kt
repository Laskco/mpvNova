package app.mpvnova.player

import android.view.View
import android.widget.RelativeLayout
import androidx.core.view.updateLayoutParams

private const val SKIP_BUTTON_BOTTOM_MARGIN_DP = 112f
private const val SKIP_BUTTON_CONTROLS_GAP_DP = 20f
internal const val SKIP_BUTTON_SELECTION_INDEX = -2

/**
 * The "Skip Intro/Outro/Recap" prompt used by [SkipSegmentsMode.BUTTON]. It stays out of the
 * normal controls list so DOWN still enters the player controls. UP from the seekbar selects it,
 * matching Nuvio's explicit focus routing without trapping the control bar.
 */

internal val MPVActivity.skipButtonVisible: Boolean
    get() = binding.skipSegmentBtn.visibility == View.VISIBLE

internal fun MPVActivity.syncSkipButtonHighlight() {
    val controlsVisible = binding.controls.visibility == View.VISIBLE
    binding.skipSegmentBtn.isSelected = skipButtonVisible &&
        (!controlsVisible || btnSelected == SKIP_BUTTON_SELECTION_INDEX)
}

/** Show the Skip button for [seg]; idempotent while already shown for the same segment. */
internal fun MPVActivity.showSkipButton(seg: SkipSegment) {
    val previousKey = currentSkipButtonSegment?.key()
    currentSkipButtonSegment = seg
    if (previousKey != seg.key()) {
        dismissedSkipSegmentKeys.remove(seg.key())
        autoHiddenSkipSegmentKeys.remove(seg.key())
    }
    val label = skipSegmentLabel(seg.type)
    binding.skipSegmentBtnText.setTextIfChanged(label)
    binding.skipSegmentBtn.contentDescription = getString(R.string.skip_button_label, label)
    refreshSkipButtonVisibility()
}

internal fun MPVActivity.hideSkipButton() {
    if (currentSkipButtonSegment == null) return
    currentSkipButtonSegment = null
    eventUiHandler.removeCallbacks(skipButtonAutoHideRunnable)
    if (btnSelected == SKIP_BUTTON_SELECTION_INDEX) btnSelected = -1
    binding.skipSegmentBtn.isSelected = false
    binding.skipSegmentBtn.setVisibilityIfChanged(View.GONE)
}

/** OK / tap on the Skip button: skip the active segment. */
internal fun MPVActivity.skipFromButton() {
    val seg = currentSkipButtonSegment ?: return
    autoSkippedSegmentKeys.add(seg.key())
    rewoundSkipSegmentKeys.remove(seg.key())
    performSegmentSkip(seg)
    hideSkipButton()
}

/** Dismiss the prompt without skipping; mark handled so it doesn't immediately re-pop. */
internal fun MPVActivity.dismissSkipButton() {
    val seg = currentSkipButtonSegment ?: return
    dismissedSkipSegmentKeys.add(seg.key())
    eventUiHandler.removeCallbacks(skipButtonAutoHideRunnable)
    if (btnSelected == SKIP_BUTTON_SELECTION_INDEX) btnSelected = -1
    binding.skipSegmentBtn.isSelected = false
    binding.skipSegmentBtn.setVisibilityIfChanged(View.GONE)
}

internal fun MPVActivity.autoHideSkipButton() {
    val seg = currentSkipButtonSegment ?: return
    autoHiddenSkipSegmentKeys.add(seg.key())
    if (btnSelected == SKIP_BUTTON_SELECTION_INDEX) btnSelected = -1
    binding.skipSegmentBtn.isSelected = false
    binding.skipSegmentBtn.setVisibilityIfChanged(View.GONE)
}

internal fun MPVActivity.bindSkipButton() {
    binding.skipSegmentBtn.setOnClickListener { skipFromButton() }
}

internal fun MPVActivity.refreshSkipButtonVisibility() {
    val seg = currentSkipButtonSegment ?: return
    val controlsVisible = binding.controls.visibility == View.VISIBLE
    val key = seg.key()
    val shouldShow = (key !in autoSkippedSegmentKeys || key in rewoundSkipSegmentKeys) &&
        (controlsVisible ||
            (key !in dismissedSkipSegmentKeys && key !in autoHiddenSkipSegmentKeys))

    eventUiHandler.removeCallbacks(skipButtonAutoHideRunnable)
    if (!shouldShow) {
        if (btnSelected == SKIP_BUTTON_SELECTION_INDEX) btnSelected = -1
        binding.skipSegmentBtn.isSelected = false
        binding.skipSegmentBtn.setVisibilityIfChanged(View.GONE)
        return
    }

    updateSkipButtonPlacement()
    binding.skipSegmentBtn.setVisibilityIfChanged(View.VISIBLE)
    syncSkipButtonHighlight()
    val autoHideMs = skipButtonDisplayMode.autoHideMs
    if (!controlsVisible && autoHideMs != null) {
        eventUiHandler.postDelayed(skipButtonAutoHideRunnable, autoHideMs)
    }
}

internal fun MPVActivity.updateSkipButtonPlacement() {
    currentSkipButtonSegment?.let { seg ->
        if (
            seg.key() in autoSkippedSegmentKeys &&
            seg.key() !in rewoundSkipSegmentKeys
        ) {
            binding.skipSegmentBtn.setVisibilityIfChanged(View.GONE)
        }
    }
    if (binding.skipSegmentBtn.visibility != View.VISIBLE) return

    val controlsVisible = binding.controls.visibility == View.VISIBLE
    val baseMargin = Utils.convertDp(activityContext, SKIP_BUTTON_BOTTOM_MARGIN_DP)
    val controlsGap = Utils.convertDp(activityContext, SKIP_BUTTON_CONTROLS_GAP_DP)
    val controlsHeight = if (controlsVisible) binding.controls.height else 0
    val controlsBottomMargin = (binding.controls.layoutParams as? RelativeLayout.LayoutParams)
        ?.bottomMargin
        ?: 0
    val bottomMargin = if (controlsVisible && controlsHeight > 0) {
        controlsHeight + controlsBottomMargin + controlsGap
    } else {
        baseMargin
    }

    binding.skipSegmentBtn.updateLayoutParams<RelativeLayout.LayoutParams> {
        this.bottomMargin = bottomMargin
    }

    if (controlsVisible && controlsHeight == 0) {
        binding.controls.post { updateSkipButtonPlacement() }
    }
}

internal fun MPVActivity.selectPlaybackSeekbarFromSkipButton() {
    val controls = dpadButtons()
    btnSelected = controls.indexOf(binding.playbackSeekbar).takeIf { it >= 0 }
        ?: firstControlButtonIndex(controls)
    binding.skipSegmentBtn.isSelected = false
    updateSelectedDpadButton()
    keepVisibleControlsFresh()
}
