package app.mpvnova.player

import android.app.Activity
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogSettingsInputBinding
import app.mpvnova.player.preferences.SettingsChoiceItem
import app.mpvnova.player.preferences.showSettingsChoiceDialog
import app.mpvnova.player.preferences.styleAsTvPanel

internal fun Context.networkValueLabel(setting: NetworkSetting): String {
    val values = networkOverrides()
    if (setting == NetworkSetting.PRESET) {
        val preset = NetworkPreset.entries.firstOrNull { it.values == values }
        return getString(preset?.titleRes ?: R.string.network_custom)
    }
    return networkNumberLabel(setting, values[setting] ?: NETWORK_DEFAULT)
}

internal fun Context.networkNumberLabel(setting: NetworkSetting, value: String): String {
    if (value == NETWORK_DEFAULT) return getString(R.string.network_default)
    return when (setting.unit) {
        NetworkUnit.MIB -> getString(R.string.network_mib_value, value)
        NetworkUnit.KIB -> getString(R.string.network_kib_value, value)
        NetworkUnit.SECONDS -> getString(R.string.network_seconds_value, value)
        NetworkUnit.TOGGLE -> getString(if (value == "yes") R.string.status_on else R.string.status_off)
        NetworkUnit.PRESET -> value
    }
}

internal fun Activity.showNetworkSetting(setting: NetworkSetting, onChanged: () -> Unit) {
    if (setting == NetworkSetting.PRESET) {
        showNetworkPresets(onChanged)
        return
    }
    val current = networkOverrides()[setting] ?: NETWORK_DEFAULT
    val items = mutableListOf(SettingsChoiceItem(
        title = getString(R.string.network_default),
        detail = getString(R.string.network_option_default_summary),
        checked = current == NETWORK_DEFAULT,
    ) { saveNetworkValue(setting, NETWORK_DEFAULT, onChanged) })
    val choices = if (setting.unit == NetworkUnit.TOGGLE) listOf("yes", "no") else setting.choices.map(Int::toString)
    choices.forEach { value ->
        val fits = networkValueFits(networkOverrides() + (setting to value), networkMemoryBudgetMiB(this))
        items += SettingsChoiceItem(
            title = networkNumberLabel(setting, value),
            detail = if (fits) null else getString(R.string.network_memory_limit, networkMemoryBudgetMiB(this)),
            checked = current == value,
            enabled = fits,
        ) { saveNetworkValue(setting, value, onChanged) }
    }
    if (setting.unit != NetworkUnit.TOGGLE) {
        items += SettingsChoiceItem(title = getString(R.string.network_custom_value)) {
            showNetworkCustomValue(setting, current, onChanged)
        }
    }
    showSettingsChoiceDialog(getString(setting.titleRes), items, getString(setting.summaryRes))
}

private fun Activity.showNetworkPresets(onChanged: () -> Unit) {
    val current = networkOverrides()
    val budget = networkMemoryBudgetMiB(this)
    val items = NetworkPreset.entries.map { preset ->
        val fits = networkValueFits(preset.values, budget)
        SettingsChoiceItem(
            title = getString(preset.titleRes),
            detail = getString(preset.summaryRes) +
                if (fits) "" else "\n" + getString(R.string.network_memory_limit, budget),
            checked = current == preset.values,
            enabled = fits,
        ) {
            saveNetworkChange(onChanged) {
                writeNetworkPreset(PreferenceManager.getDefaultSharedPreferences(this), preset)
            }
        }
    }
    showSettingsChoiceDialog(getString(R.string.network_preset), items, getString(R.string.network_preset_summary))
}

private fun Activity.saveNetworkValue(setting: NetworkSetting, value: String, onChanged: () -> Unit) {
    saveNetworkChange(onChanged) {
        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        if (value == NETWORK_DEFAULT) editor.remove(setting.key) else editor.putString(setting.key, value)
        editor.apply()
    }
}

private fun Activity.saveNetworkChange(onChanged: () -> Unit, save: () -> Unit) {
    val before = networkOverrides()
    save()
    if (this is MPVActivity) {
        player.applyNetworkSettings(verify = true)
        val after = networkOverrides()
        if (NetworkSetting.entries.any { it.nextStream && before[it] != after[it] }) {
            Toast.makeText(this, R.string.network_next_stream_notice, Toast.LENGTH_LONG).show()
        }
    }
    onChanged()
}

private fun Activity.showNetworkCustomValue(setting: NetworkSetting, current: String, onChanged: () -> Unit) {
    val binding = DialogSettingsInputBinding.inflate(layoutInflater)
    binding.inputTitle.setText(setting.titleRes)
    binding.inputMessage.text = getString(
        R.string.network_value_range,
        networkNumberLabel(setting, setting.minimum.toString()),
        networkNumberLabel(setting, setting.maximum.toString()),
    )
    binding.inputValue.inputType = InputType.TYPE_CLASS_NUMBER
    binding.inputValue.filters = arrayOf(InputFilter.LengthFilter(NETWORK_VALUE_MAX_DIGITS))
    binding.inputValue.setText(current.takeUnless { it == NETWORK_DEFAULT }.orEmpty())
    binding.inputValue.selectAll()
    val dialog = AlertDialog.Builder(this).setView(binding.root).create()
    binding.inputCancelBtn.setOnClickListener { dialog.dismiss() }
    binding.inputOkBtn.setOnClickListener {
        val value = binding.inputValue.text.toString().trim().toIntOrNull()?.toString().orEmpty()
        when {
            !setting.valid(value) -> binding.inputValue.error = binding.inputMessage.text
            !networkValueFits(networkOverrides() + (setting to value), networkMemoryBudgetMiB(this)) ->
                binding.inputValue.error = getString(R.string.network_memory_limit, networkMemoryBudgetMiB(this))
            else -> {
                saveNetworkValue(setting, value, onChanged)
                dialog.dismiss()
            }
        }
    }
    val playerActivity = this as? MPVActivity
    val onInputShown = playerActivity?.preparePlayerTextInput(playerActivity.currentDrawerDialog)
    dialog.show()
    dialog.styleAsTvPanel()
    onInputShown?.invoke(dialog)
    binding.inputValue.requestFocus()
}

private const val NETWORK_VALUE_MAX_DIGITS = 4
