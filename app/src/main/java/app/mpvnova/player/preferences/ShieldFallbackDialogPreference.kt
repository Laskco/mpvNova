package app.mpvnova.player.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import app.mpvnova.player.MPVView
import app.mpvnova.player.R
import app.mpvnova.player.SHIELD_FALLBACK_OPTIONS
import app.mpvnova.player.ShieldFallbackOption
import app.mpvnova.player.shieldFallbackOption
import app.mpvnova.player.databinding.DialogPlayerShieldFallbackBinding
import app.mpvnova.player.databinding.DialogShieldFallbackItemBinding
import app.mpvnova.player.toShieldDecoderFallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Picker for the Hi10P fallback tunings. A plain ListPreference can't
 * render the per-option descriptions readably on TV, so this builds the same
 * title-plus-description rows the in-player option dialogs use.
 */
class ShieldFallbackDialogPreference(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    override fun onClick() {
        val inflater = LayoutInflater.from(context)
        val binding = DialogPlayerShieldFallbackBinding.inflate(inflater)
        lateinit var dialog: AlertDialog
        val selected = currentValue()
        SHIELD_FALLBACK_OPTIONS.forEach { option ->
            binding.fallbackOptions.addView(
                buildOptionRow(inflater, binding, option, option.value == selected) { dialog.dismiss() }
            )
        }
        binding.cancelBtn.setOnClickListener { dialog.cancel() }
        dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
        }
        val selectedIndex = SHIELD_FALLBACK_OPTIONS.indexOfFirst { it.value == selected }.coerceAtLeast(0)
        binding.fallbackOptions.getChildAt(selectedIndex)?.requestFocus()
    }

    private fun buildOptionRow(
        inflater: LayoutInflater,
        parent: DialogPlayerShieldFallbackBinding,
        option: ShieldFallbackOption,
        isSelected: Boolean,
        dismiss: () -> Unit,
    ): View {
        val item = DialogShieldFallbackItemBinding.inflate(inflater, parent.fallbackOptions, false)
        item.fallbackTitle.setText(option.titleRes)
        item.fallbackDesc.setText(option.descRes)
        item.fallbackCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        item.root.isActivated = isSelected
        item.root.setOnClickListener {
            if (callChangeListener(option.value)) {
                persistString(option.value)
                updateSummary()
            }
            dismiss()
        }
        return item.root
    }

    private fun currentValue(): String =
        getPersistedString(MPVView.SHIELD_DECODER_FALLBACK_DEFAULT).toShieldDecoderFallback()

    private fun updateSummary() {
        summary = context.getString(shieldFallbackOption(currentValue()).titleRes)
    }
}
