package app.mpvnova.player

import android.app.Dialog
import android.graphics.Rect
import android.view.Gravity
import android.view.Window
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import app.mpvnova.player.databinding.DialogCustomPresetNameBinding

internal const val CUSTOM_PRESET_NAME_MAX_CHARS = 12

internal fun normalizedCustomPresetName(value: String): String =
    value.trim().take(CUSTOM_PRESET_NAME_MAX_CHARS)

internal fun MPVActivity.showCustomPresetNameDialog(
    currentName: String?,
    chrome: PlayerDialogChrome,
    onSave: (String) -> Unit,
) {
    val onInputShown = preparePlayerTextInput(topPlayerDialog)
    val binding = DialogCustomPresetNameBinding.inflate(layoutInflater)
    val editing = !currentName.isNullOrBlank()
    binding.customPresetDialogTitle.setText(
        if (editing) R.string.custom_preset_rename_title else R.string.custom_preset_save_title,
    )
    binding.customPresetNameInput.setText(currentName.orEmpty())
    binding.customPresetNameInput.setSelection(binding.customPresetNameInput.length())

    fun updateCount() {
        binding.customPresetNameCount.text = getString(
            R.string.custom_preset_name_count,
            binding.customPresetNameInput.length(),
            CUSTOM_PRESET_NAME_MAX_CHARS,
        )
    }
    binding.customPresetNameInput.addTextChangedListener(afterTextChanged = { updateCount() })
    updateCount()

    val dialog = AlertDialog.Builder(this).setView(binding.root).create()
    binding.customPresetCancelBtn.setOnClickListener { dialog.dismiss() }
    binding.customPresetSaveBtn.setOnClickListener {
        val name = normalizedCustomPresetName(binding.customPresetNameInput.text?.toString().orEmpty())
        if (name.isBlank()) {
            binding.customPresetNameInput.error = getString(R.string.custom_preset_name_required)
        } else {
            onSave(name)
            dialog.dismiss()
        }
    }
    UiFont.applyToViewTree(binding.root)
    showWidePlayerDialog(dialog, CUSTOM_PRESET_NAME_DIALOG_LAYOUT, chrome)
    onInputShown(dialog)
}

internal fun MPVActivity.preparePlayerTextInput(backgroundDialog: Dialog?): (AlertDialog) -> Unit {
    val activitySoftInputMode = window.attributes.softInputMode
    val backgroundWindowState = backgroundDialog?.window?.freezeForPresetKeyboard()
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    return { dialog -> trackPresetKeyboard(dialog, backgroundDialog, backgroundWindowState, activitySoftInputMode) }
}

@Suppress("DEPRECATION")
private fun MPVActivity.trackPresetKeyboard(
    dialog: AlertDialog,
    editorDialog: Dialog?,
    editorWindowState: EditorWindowState?,
    activitySoftInputMode: Int,
) {
    val dialogWindow = dialog.window ?: return
    dialogWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    val decor = dialogWindow.decorView
    val visibleFrame = Rect()
    val screenHeight = resources.displayMetrics.heightPixels
    val keyboardThreshold = (screenHeight * KEYBOARD_VISIBLE_THRESHOLD_FRACTION).toInt()
    val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        decor.getWindowVisibleDisplayFrame(visibleFrame)
        val keyboardHeight = (screenHeight - visibleFrame.bottom).coerceAtLeast(0)
        val offset = if (keyboardHeight > keyboardThreshold) {
            -(keyboardHeight / KEYBOARD_DIALOG_OFFSET_DIVISOR)
        } else {
            0
        }
        if (dialogWindow.attributes.y != offset) {
            dialogWindow.attributes = dialogWindow.attributes.apply { y = offset }
        }
    }
    decor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    dialog.setOnDismissListener {
        if (decor.viewTreeObserver.isAlive) {
            decor.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
        eventUiHandler.postDelayed(
            {
                window.setSoftInputMode(activitySoftInputMode)
                if (editorDialog?.isShowing == true && editorWindowState != null) {
                    editorDialog.window?.restoreAfterPresetKeyboard(editorWindowState)
                }
            },
            SOFT_INPUT_RESTORE_DELAY_MS,
        )
    }
}

private fun Window.freezeForPresetKeyboard(): EditorWindowState {
    val current = attributes
    val state = EditorWindowState(
        softInputMode = current.softInputMode,
        flags = current.flags,
        gravity = current.gravity,
        x = current.x,
        y = current.y,
    )
    val location = IntArray(2)
    decorView.getLocationOnScreen(location)
    val horizontalGravity = current.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
    attributes = current.apply {
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        gravity = Gravity.TOP or horizontalGravity
        y = location[1]
    }
    return state
}

private fun Window.restoreAfterPresetKeyboard(state: EditorWindowState) {
    attributes = attributes.apply {
        softInputMode = state.softInputMode
        flags = state.flags
        gravity = state.gravity
        x = state.x
        y = state.y
    }
}

private data class EditorWindowState(
    val softInputMode: Int,
    val flags: Int,
    val gravity: Int,
    val x: Int,
    val y: Int,
)

private const val KEYBOARD_VISIBLE_THRESHOLD_FRACTION = 0.18f
private const val KEYBOARD_DIALOG_OFFSET_DIVISOR = 2
private const val SOFT_INPUT_RESTORE_DELAY_MS = 350L
