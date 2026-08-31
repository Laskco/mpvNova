package app.mpvnova.player

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
}
