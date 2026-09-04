package app.mpvnova.player

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import app.mpvnova.player.databinding.DialogPlayerShieldFallbackBinding
import app.mpvnova.player.databinding.DialogShieldFallbackItemBinding

internal fun MPVActivity.pickPreferredDecoderMode() {
    val restore = keepPlaybackForDialog()
    val options = preferredDecoderModeOptions(shieldDecoderModeEnabled)
    val currentMode = normalizedPreferredDecoderMode(preferredDecoderMode, shieldDecoderModeEnabled)
    val items = options.map { option ->
        MediaPickerDialog.Item(
            label = preferredDecoderPickerLabel(option),
            tag = option.value,
            selected = option.value == currentMode,
            enabled = option.enabled,
        )
    }
    val impl = preferredDecoderPickerDialog ?: MediaPickerDialog().also {
        preferredDecoderPickerDialog = it
    }
    lateinit var dialog: AlertDialog
    impl.onItemClick = { idx ->
        if (options[idx].enabled) {
            val mode = options[idx].value
            preferredDecoderMode = mode
            getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString("preferred_decoder_mode", mode)
                .apply()
            if (!autoDecoderFallback) {
                sessionDecoderMode = mode
                player.applyDecoderMode(mode)
                updateDecoderButton()
            }
            refreshDrawerRowsIfVisible(DrawerTab.VIDEO)
            dialog.dismiss()
        }
    }

    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(
            impl.buildView(
                inflater,
                MediaPickerDialog.Options(
                    title = getString(R.string.pref_preferred_decoder_mode_title),
                    items = items,
                ),
            )
        )
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.62f,
            maxWidthDp = 760f,
        )
    )
}

private fun MPVActivity.preferredDecoderPickerLabel(
    option: PreferredDecoderModeOption,
): CharSequence = if (!option.enabled && option.value == MPVView.DECODER_MODE_GNEXT_DIRECT) {
    getString(option.titleRes) + "\n" +
        getString(R.string.decoder_mode_gnext_direct_shield_warning)
} else {
    getString(option.titleRes)
}

internal fun MPVActivity.pickShieldDecoderFallback() {
    val restore = keepPlaybackForDialog()
    val inflater = LayoutInflater.from(this)
    val binding = DialogPlayerShieldFallbackBinding.inflate(inflater)
    lateinit var dialog: AlertDialog
    val selected = shieldDecoderFallback.toShieldDecoderFallback()

    SHIELD_FALLBACK_OPTIONS.forEach { option ->
        binding.fallbackOptions.addView(
            buildShieldFallbackRow(inflater, binding, option, option.value == selected) {
                shieldDecoderFallback = option.value
                getDefaultSharedPreferences(applicationContext)
                    .edit()
                    .putString("shield_decoder_fallback", option.value)
                    .apply()
                if (currentDecoderUiMode() == MPVView.DECODER_MODE_SHIELD_H10P) {
                    player.applyShieldHi10pFallback(option.value)
                    updateDecoderButton()
                }
                refreshDrawerRowsIfVisible(DrawerTab.VIDEO)
                dialog.dismiss()
            }
        )
    }
    binding.cancelBtn.setOnClickListener { dialog.cancel() }

    dialog = with(AlertDialog.Builder(this)) {
        setView(binding.root)
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.56f,
            maxWidthDp = 720f,
        )
    )
    val selectedIndex = SHIELD_FALLBACK_OPTIONS.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    binding.fallbackOptions.getChildAt(selectedIndex)?.requestFocus()
}

private fun buildShieldFallbackRow(
    inflater: LayoutInflater,
    parent: DialogPlayerShieldFallbackBinding,
    option: ShieldFallbackOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) = DialogShieldFallbackItemBinding.inflate(inflater, parent.fallbackOptions, false).apply {
    fallbackTitle.setText(option.titleRes)
    fallbackDesc.setText(option.descRes)
    fallbackCheck.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.INVISIBLE
    root.isActivated = isSelected
    root.setOnClickListener { onSelect() }
}.root
