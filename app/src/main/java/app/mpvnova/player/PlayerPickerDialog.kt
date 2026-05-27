package app.mpvnova.player

import app.mpvnova.player.databinding.DialogPlayerPickerBinding
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.showPlayerPickerDialog(
    @StringRes titleRes: Int,
    contentView: View,
    restoreState: StateRestoreCallback,
    layout: PlayerDialogLayout,
    onOk: () -> Unit
) {
    lateinit var dialog: AlertDialog
    val binding = DialogPlayerPickerBinding.inflate(layoutInflater)
    binding.pickerTitle.setText(titleRes)
    binding.pickerContent.addView(contentView)
    binding.cancelBtn.setOnClickListener { dialog.cancel() }
    binding.okBtn.setOnClickListener {
        onOk()
        dialog.dismiss()
    }
    handleInsetsAsPadding(binding.root)
    dialog = with(AlertDialog.Builder(this)) {
        setView(binding.root)
        setOnDismissListener { restoreState(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(dialog, layout)
}

internal fun MPVActivity.showSubDelayPicker(
    restoreState: StateRestoreCallback,
    layout: PlayerDialogLayout
) {
    val picker = SubDelayDialog(SUB_DELAY_MIN_SEC, SUB_DELAY_MAX_SEC)
    val pickerView = picker.buildView(layoutInflater)
    picker.delay1 = player.subDelay ?: 0.0
    picker.delay2 = if (player.secondarySid != -1) player.secondarySubDelay else null

    showPlayerPickerDialog(
        titleRes = R.string.sub_delay,
        contentView = pickerView,
        restoreState = restoreState,
        layout = layout,
    ) {
        picker.delay1?.let { player.subDelay = it }
        picker.delay2?.let { player.secondarySubDelay = it }
    }
}
