package app.mpvnova.player.preferences

import android.app.Dialog
import android.app.Activity
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.mpvnova.player.R
import app.mpvnova.player.initScreensaverTimeInput
import app.mpvnova.player.screensaverInputToSeconds
import app.mpvnova.player.databinding.DialogScreensaverLogoBinding
import app.mpvnova.player.databinding.DialogScreensaverTimeInputBinding
import app.mpvnova.player.databinding.DialogSettingsChoiceBinding
import app.mpvnova.player.databinding.DialogSettingsConfirmationBinding
import app.mpvnova.player.databinding.DialogSettingsInputBinding
import app.mpvnova.player.databinding.DialogSettingsProgressBinding

private const val DIALOG_WIDTH_FRACTION = 0.5f
private const val DIALOG_MAX_WIDTH_DP = 560f
private const val CHOICE_LIST_MAX_HEIGHT_DP = 300f
private const val DISABLED_CHOICE_ALPHA = 0.58f

// A single-choice dialog styled like the mpv.conf editor (app-themed shell, styled rows),
// so the screensaver settings match the rest of the app instead of the default picker.
internal fun Fragment.showSettingsChoiceDialog(title: CharSequence, items: List<SettingsChoiceItem>) {
    showSettingsChoiceDialog(requireActivity(), layoutInflater, title, items)
}

internal fun Activity.showSettingsChoiceDialog(
    title: CharSequence,
    items: List<SettingsChoiceItem>,
    message: CharSequence? = null,
) {
    showSettingsChoiceDialog(this, layoutInflater, title, items, message)
}

private fun showSettingsChoiceDialog(
    activity: Activity,
    inflater: LayoutInflater,
    title: CharSequence,
    items: List<SettingsChoiceItem>,
    message: CharSequence? = null,
) {
    val binding = DialogSettingsChoiceBinding.inflate(inflater)
    binding.choiceTitle.text = title
    binding.choiceMessage.text = message
    binding.choiceMessage.isVisible = !message.isNullOrBlank()
    binding.choiceScroll.maxHeightPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, CHOICE_LIST_MAX_HEIGHT_DP, activity.resources.displayMetrics
    ).toInt()
    val dialog = AlertDialog.Builder(activity).setView(binding.root).create()
    var selectedRow: View? = null
    for (item in items) {
        val row = inflater.inflate(R.layout.dialog_setting_option_item, binding.choiceRows, false)
        row.findViewById<TextView>(R.id.optionTitleText).text = item.title
        val detailView = row.findViewById<TextView>(R.id.optionDetailText)
        if (item.detail.isNullOrEmpty()) detailView.isVisible = false else detailView.text = item.detail
        row.findViewById<ImageView>(R.id.optionCheck).visibility =
            if (item.checked) View.VISIBLE else View.INVISIBLE
        if (item.checked) selectedRow = row
        row.isEnabled = item.enabled
        row.isFocusable = item.enabled
        row.alpha = if (item.enabled) 1f else DISABLED_CHOICE_ALPHA
        if (item.enabled) {
            row.setOnClickListener {
                item.onClick()
                dialog.dismiss()
            }
        }
        binding.choiceRows.addView(row)
    }
    binding.choiceCancelBtn.setOnClickListener { dialog.dismiss() }
    dialog.show()
    dialog.styleAsTvPanel()
    val focusRow = selectedRow?.takeIf { it.isEnabled } ?: (0 until binding.choiceRows.childCount)
        .map(binding.choiceRows::getChildAt)
        .firstOrNull(View::isEnabled)
    focusRow?.post { focusRow.requestFocus() }
}

internal fun Fragment.showSettingsInputDialog(
    title: CharSequence,
    message: CharSequence?,
    initialValue: String,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    onConfirm: (String) -> Unit,
) {
    val binding = DialogSettingsInputBinding.inflate(layoutInflater)
    binding.inputTitle.text = title
    binding.inputMessage.text = message ?: ""
    binding.inputMessage.isVisible = !message.isNullOrBlank()
    binding.inputValue.inputType = inputType
    binding.inputValue.setText(initialValue)
    binding.inputValue.selectAll()
    val dialog = AlertDialog.Builder(requireActivity()).setView(binding.root).create()
    binding.inputCancelBtn.setOnClickListener { dialog.dismiss() }
    binding.inputOkBtn.setOnClickListener {
        onConfirm(binding.inputValue.text?.toString().orEmpty())
        dialog.dismiss()
    }
    dialog.show()
    dialog.styleAsTvPanel()
    binding.inputValue.requestFocus()
}

internal fun Fragment.showSettingsConfirmationDialog(
    title: CharSequence,
    message: CharSequence,
    confirmText: CharSequence,
    onConfirm: () -> Unit,
) {
    showSettingsConfirmationDialog(requireActivity(), layoutInflater, title, message, confirmText, onConfirm)
}

internal fun Activity.showSettingsConfirmationDialog(
    title: CharSequence,
    message: CharSequence,
    confirmText: CharSequence,
    onConfirm: () -> Unit,
) {
    showSettingsConfirmationDialog(this, layoutInflater, title, message, confirmText, onConfirm)
}

internal fun Activity.showSettingsProgressDialog(message: CharSequence): AlertDialog {
    val binding = DialogSettingsProgressBinding.inflate(layoutInflater)
    binding.progressMessage.text = message
    return AlertDialog.Builder(this)
        .setView(binding.root)
        .create()
        .also { dialog ->
            dialog.setCancelable(false)
            dialog.show()
            dialog.styleAsTvPanel()
        }
}

private fun showSettingsConfirmationDialog(
    activity: Activity,
    inflater: LayoutInflater,
    title: CharSequence,
    message: CharSequence,
    confirmText: CharSequence,
    onConfirm: () -> Unit,
) {
    val binding = DialogSettingsConfirmationBinding.inflate(inflater)
    binding.confirmationTitle.text = title
    binding.confirmationMessage.text = message
    binding.confirmationConfirmBtn.text = confirmText
    val dialog = AlertDialog.Builder(activity).setView(binding.root).create()
    binding.confirmationCancelBtn.setOnClickListener { dialog.dismiss() }
    binding.confirmationConfirmBtn.setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }
    dialog.show()
    dialog.styleAsTvPanel()
    binding.confirmationCancelBtn.requestFocus()
}

// The custom-logo actions as a button dialog, mirroring the mpv.conf editor's button row.
internal fun Fragment.showScreensaverLogoDialog(
    hasCustom: Boolean,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    val binding = DialogScreensaverLogoBinding.inflate(layoutInflater)
    binding.logoMessage.setText(
        if (hasCustom) R.string.pref_screensaver_logo_custom else R.string.pref_screensaver_logo_default
    )
    binding.logoResetBtn.isVisible = hasCustom
    val dialog = AlertDialog.Builder(requireActivity()).setView(binding.root).create()
    binding.logoChooseBtn.setOnClickListener { dialog.dismiss(); onChoose() }
    binding.logoResetBtn.setOnClickListener { dialog.dismiss(); onReset() }
    binding.logoCancelBtn.setOnClickListener { dialog.dismiss() }
    dialog.show()
    dialog.styleAsTvPanel()
    binding.logoChooseBtn.requestFocus()
}

// A numeric idle-time input styled like the mpv.conf editor, shared by settings + the drawer.
internal fun Fragment.showScreensaverTimeInputDialog(currentSeconds: Int, onOk: (Int) -> Unit) {
    val binding = DialogScreensaverTimeInputBinding.inflate(layoutInflater)
    initScreensaverTimeInput(binding, currentSeconds)
    val dialog = AlertDialog.Builder(requireActivity()).setView(binding.root).create()
    binding.timeCancelBtn.setOnClickListener { dialog.dismiss() }
    binding.timeOkBtn.setOnClickListener {
        onOk(screensaverInputToSeconds(binding))
        dialog.dismiss()
    }
    dialog.show()
    dialog.styleAsTvPanel()
    binding.minutesInput.requestFocus()
}

// Transparent, width-bounded window so only the shell shows (correct theme colours) and the
// content wraps to its own height, keeping the button row on-screen.
internal fun Dialog.styleAsTvPanel() {
    val metrics = context.resources.displayMetrics
    val maxWidthPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, DIALOG_MAX_WIDTH_DP, metrics
    ).toInt()
    window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        decorView.setPadding(0, 0, 0, 0)
        setLayout(
            minOf((metrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt(), maxWidthPx),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }
}
