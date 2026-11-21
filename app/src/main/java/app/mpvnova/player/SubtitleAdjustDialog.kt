package app.mpvnova.player

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import app.mpvnova.player.databinding.DialogSubtitleAdjustmentBinding

private const val SUBTITLE_ADJUST_DIALOG_MAX_WIDTH_DP = 450f
private const val STEPPER_ENABLED_ALPHA = 1f
private const val STEPPER_DISABLED_ALPHA = 0.35f

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
    val initialFocus = if (controls.posDecreaseBtn.isEnabled) {
        controls.posDecreaseBtn
    } else {
        controls.posIncreaseBtn
    }
    initialFocus.requestFocus()
}

private fun MPVActivity.bindSubtitleAdjustControls(controls: DialogSubtitleAdjustmentBinding) {
    fun updateStepperButton(button: View, sibling: View, enabled: Boolean) {
        if (!enabled && button.hasFocus() && sibling.isEnabled) {
            sibling.requestFocus()
        }
        button.isEnabled = enabled
        button.alpha = if (enabled) STEPPER_ENABLED_ALPHA else STEPPER_DISABLED_ALPHA
    }

    fun refresh() {
        val posState = currentSubPosState()
        controls.posValue.text = posState.label
        updateStepperButton(controls.posDecreaseBtn, controls.posIncreaseBtn, posState.canDecrease)
        updateStepperButton(controls.posIncreaseBtn, controls.posDecreaseBtn, posState.canIncrease)

        val scaleState = currentSubScaleState()
        controls.scaleValue.text = scaleState.label
        updateStepperButton(controls.scaleDecreaseBtn, controls.scaleIncreaseBtn, scaleState.canDecrease)
        updateStepperButton(controls.scaleIncreaseBtn, controls.scaleDecreaseBtn, scaleState.canIncrease)
    }

    fun refreshRememberState() {
        controls.rememberRow.isActivated = persistSubFilters
        controls.rememberCheck.visibility = if (persistSubFilters) View.VISIBLE else View.INVISIBLE
        ViewCompat.setStateDescription(
            controls.rememberRow,
            getString(if (persistSubFilters) R.string.status_on else R.string.status_off),
        )
    }

    fun stepPos(delta: Int) {
        subPosLevel = (subPosLevel + delta).coerceIn(0, subPosSteps.lastIndex)
        applySubPosProperty()
        writeSettings()
        refresh()
    }

    fun stepScale(delta: Int) {
        subScaleLevel = (subScaleLevel + delta).coerceIn(0, subScaleSteps.lastIndex)
        applySubScaleProperty()
        writeSettings()
        refresh()
    }

    controls.posDecreaseBtn.setOnClickListener { stepPos(-1) }
    controls.posIncreaseBtn.setOnClickListener { stepPos(1) }
    controls.scaleDecreaseBtn.setOnClickListener { stepScale(-1) }
    controls.scaleIncreaseBtn.setOnClickListener { stepScale(1) }
    controls.resetBtn.setOnClickListener {
        subPosLevel = DEFAULT_SUB_POSITION_INDEX.coerceIn(0, subPosSteps.lastIndex)
        subScaleLevel = DEFAULT_SUB_SCALE_INDEX.coerceIn(0, subScaleSteps.lastIndex)
        applySubPosProperty()
        applySubScaleProperty()
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
