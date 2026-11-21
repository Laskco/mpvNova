package app.mpvnova.player

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import app.mpvnova.player.databinding.DialogSubtitleWidthBinding

private const val SUBTITLE_WIDTH_DIALOG_MAX_WIDTH_DP = 450f
private const val WIDTH_STEPPER_ENABLED_ALPHA = 1f
private const val WIDTH_STEPPER_DISABLED_ALPHA = 0.35f

internal fun MPVActivity.applySubMarginX(styleEnabled: Boolean) {
    if (!subStylePreservingCompanionTrack && isSubWidthOn()) {
        mpvSetPropertyInt("sub-margin-x", subWidthMarginX(subWidthSteps[subWidthLevel]))
        return
    }
    val restored = (if (styleEnabled) subStyleExtras.sideMargin?.toString() else null)
        ?: subStyleSavedDefaults?.get("sub-margin-x")
    restored?.let { mpvSetPropertyString("sub-margin-x", it) }
}

internal fun MPVActivity.applySubWidthProperty() {
    if (isSubWidthOn()) snapshotSubStyleBaselineIfNeeded()
    val styleEnabled = customSubStyleEnabled && !subStylePreservingCompanionTrack
    applySubMarginX(styleEnabled)
    applyAssStyleOverrides(styleEnabled)
}

internal fun MPVActivity.refreshSubWidthAssOverrides() {
    if (isSubWidthOn() || subStyleAppliedAssOverrides != null) {
        applyAssStyleOverrides(customSubStyleEnabled && !subStylePreservingCompanionTrack)
    }
}

internal fun MPVActivity.adjustSubWidth(delta: Int): MediaPickerDialog.ValueState {
    val maxLevel = subWidthSteps.lastIndex
    subWidthLevel = (subWidthLevel + delta).coerceIn(0, maxLevel)
    applySubWidthProperty()
    writeSettings()
    showToast(getString(R.string.btn_sub_width), getSubWidthLabel())
    return currentSubWidthState()
}

internal fun MPVActivity.showSubtitleWidthDialog() {
    val controls = DialogSubtitleWidthBinding.inflate(layoutInflater)
    val restore = keepPlaybackForDialog()
    controls.rememberCheck.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

    bindSubtitleWidthControls(controls)

    val dialog = AlertDialog.Builder(this)
        .setView(controls.root)
        .setOnDismissListener { restore() }
        .create()
    controls.doneBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(
        dialog,
        videoAdjustmentDialogLayout().copy(maxWidthDp = SUBTITLE_WIDTH_DIALOG_MAX_WIDTH_DP),
    )
    val initialFocus = if (controls.widthDecreaseBtn.isEnabled) {
        controls.widthDecreaseBtn
    } else {
        controls.widthIncreaseBtn
    }
    initialFocus.requestFocus()
}

private fun MPVActivity.bindSubtitleWidthControls(controls: DialogSubtitleWidthBinding) {
    fun updateStepperButton(button: View, sibling: View, enabled: Boolean) {
        if (!enabled && button.hasFocus() && sibling.isEnabled) {
            sibling.requestFocus()
        }
        button.isEnabled = enabled
        button.alpha = if (enabled) WIDTH_STEPPER_ENABLED_ALPHA else WIDTH_STEPPER_DISABLED_ALPHA
    }

    fun refresh() {
        val state = currentSubWidthState()
        controls.widthValue.text = state.label
        updateStepperButton(controls.widthDecreaseBtn, controls.widthIncreaseBtn, state.canDecrease)
        updateStepperButton(controls.widthIncreaseBtn, controls.widthDecreaseBtn, state.canIncrease)
    }

    fun refreshRememberState() {
        controls.rememberRow.isActivated = persistSubFilters
        controls.rememberCheck.visibility = if (persistSubFilters) View.VISIBLE else View.INVISIBLE
        ViewCompat.setStateDescription(
            controls.rememberRow,
            getString(if (persistSubFilters) R.string.status_on else R.string.status_off),
        )
    }

    fun stepWidth(delta: Int) {
        subWidthLevel = (subWidthLevel + delta).coerceIn(0, subWidthSteps.lastIndex)
        applySubWidthProperty()
        writeSettings()
        refresh()
    }

    controls.widthDecreaseBtn.setOnClickListener { stepWidth(-1) }
    controls.widthIncreaseBtn.setOnClickListener { stepWidth(1) }
    controls.resetBtn.setOnClickListener {
        subWidthLevel = DEFAULT_SUB_WIDTH_INDEX.coerceIn(0, subWidthSteps.lastIndex)
        applySubWidthProperty()
        writeSettings()
        refresh()
    }
    controls.rememberRow.setOnClickListener {
        persistSubFilters = !persistSubFilters
        if (persistSubFilters) {
            saveUserTrackPick("sub", player.sid)
        }
        writeSettings()
        refreshRememberState()
    }

    refresh()
    refreshRememberState()
}
