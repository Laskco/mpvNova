package app.mpvnova.player

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import app.mpvnova.player.databinding.DialogSubtitleAdjustmentBinding

private const val SUBTITLE_ADJUST_DIALOG_MAX_WIDTH_DP = 450f
private const val STEPPER_ENABLED_ALPHA = 1f
private const val STEPPER_DISABLED_ALPHA = 0.38f

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

internal fun MPVActivity.showSubtitleAdjustDialog() {
    val controls = DialogSubtitleAdjustmentBinding.inflate(layoutInflater)
    val restore = keepPlaybackForDialog()
    controls.rememberCheck.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

    bindSubtitleAdjustControls(controls)

    val dialog = AlertDialog.Builder(this)
        .setView(controls.root)
        .setOnDismissListener { restore() }
        .create()
    controls.doneBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(
        dialog,
        videoAdjustmentDialogLayout().copy(maxWidthDp = SUBTITLE_ADJUST_DIALOG_MAX_WIDTH_DP),
    )
    val initialFocus = if (controls.posDecreaseBtn.isClickable) {
        controls.posDecreaseBtn
    } else {
        controls.posIncreaseBtn
    }
    initialFocus.requestFocus()
}

private fun MPVActivity.bindSubtitleAdjustControls(controls: DialogSubtitleAdjustmentBinding) {
    fun step(applyChange: () -> Unit) {
        applyChange()
        writeSettings()
        refreshSubtitleAdjustControls(controls)
    }

    fun stepPos(delta: Int) = step {
        subPosLevel = (subPosLevel + delta).coerceIn(0, subPosSteps.lastIndex)
        applySubPosProperty()
    }
    fun stepScale(delta: Int) = step {
        subScaleLevel = (subScaleLevel + delta).coerceIn(0, subScaleSteps.lastIndex)
        applySubScaleProperty()
    }
    fun stepWidth(delta: Int) = step {
        subWidthLevel = (subWidthLevel + delta).coerceIn(0, subWidthSteps.lastIndex)
        applySubWidthProperty()
    }

    controls.posDecreaseBtn.setOnClickListener { stepPos(-1) }
    controls.posIncreaseBtn.setOnClickListener { stepPos(1) }
    controls.scaleDecreaseBtn.setOnClickListener { stepScale(-1) }
    controls.scaleIncreaseBtn.setOnClickListener { stepScale(1) }
    controls.widthDecreaseBtn.setOnClickListener { stepWidth(-1) }
    controls.widthIncreaseBtn.setOnClickListener { stepWidth(1) }
    controls.resetBtn.setOnClickListener {
        step {
            subPosLevel = DEFAULT_SUB_POSITION_INDEX.coerceIn(0, subPosSteps.lastIndex)
            subScaleLevel = DEFAULT_SUB_SCALE_INDEX.coerceIn(0, subScaleSteps.lastIndex)
            subWidthLevel = DEFAULT_SUB_WIDTH_INDEX.coerceIn(0, subWidthSteps.lastIndex)
            applySubPosProperty()
            applySubScaleProperty()
            applySubWidthProperty()
        }
    }
    controls.rememberRow.setOnClickListener {
        persistSubFilters = !persistSubFilters
        if (persistSubFilters) saveUserTrackPick("sub", player.sid)
        writeSettings()
        refreshSubtitleAdjustRemember(controls)
    }

    refreshSubtitleAdjustControls(controls)
    refreshSubtitleAdjustRemember(controls)
}

private fun MPVActivity.refreshSubtitleAdjustControls(controls: DialogSubtitleAdjustmentBinding) {
    val posState = currentSubPosState()
    controls.posValue.text = posState.label
    updateStepperButton(controls.posDecreaseBtn, posState.canDecrease)
    updateStepperButton(controls.posIncreaseBtn, posState.canIncrease)

    val scaleState = currentSubScaleState()
    controls.scaleValue.text = scaleState.label
    updateStepperButton(controls.scaleDecreaseBtn, scaleState.canDecrease)
    updateStepperButton(controls.scaleIncreaseBtn, scaleState.canIncrease)

    val widthState = currentSubWidthState()
    controls.widthValue.text = widthState.label
    updateStepperButton(controls.widthDecreaseBtn, widthState.canDecrease)
    updateStepperButton(controls.widthIncreaseBtn, widthState.canIncrease)
}

private fun MPVActivity.refreshSubtitleAdjustRemember(controls: DialogSubtitleAdjustmentBinding) {
    controls.rememberRow.isActivated = persistSubFilters
    controls.rememberCheck.visibility = if (persistSubFilters) View.VISIBLE else View.INVISIBLE
    ViewCompat.setStateDescription(
        controls.rememberRow,
        getString(if (persistSubFilters) R.string.status_on else R.string.status_off),
    )
}

private fun updateStepperButton(button: ImageButton, enabled: Boolean) {
    // Keep focus stable and column D-pad navigation intact when a +/- button reaches its limit.
    button.isEnabled = true
    button.isClickable = enabled
    button.isFocusable = true
    button.alpha = if (enabled) STEPPER_ENABLED_ALPHA else STEPPER_DISABLED_ALPHA
    button.imageTintList = ColorStateList.valueOf(
        ContextCompat.getColor(
            button.context,
            if (enabled) R.color.tv_text else R.color.tint_disabled,
        ),
    )
}
