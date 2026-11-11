package app.mpvnova.player.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import app.mpvnova.player.R
import app.mpvnova.player.databinding.ScalerPrefBinding

class ScalerDialogPreference(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private var entries: Array<String>
    private lateinit var binding: ScalerPrefBinding

    init {
        isPersistent = false

        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.ScalerPreferenceDialog)
        val res = styledAttrs.getResourceId(R.styleable.ScalerPreferenceDialog_entries, -1)
        entries = context.resources.getStringArray(res)

        styledAttrs.recycle()
    }

    override fun onClick() {
        super.onClick()
        binding = ScalerPrefBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        binding.dialogTitle.text = title
        setupViews()
        binding.dialogCancel.setOnClickListener { dialog.dismiss() }
        binding.dialogClear.setOnClickListener {
            clear()
            dialog.dismiss()
        }
        binding.dialogOk.setOnClickListener {
            save()
            dialog.dismiss()
        }
        dialog.show()
        dialog.styleAsTvPanel()
        binding.scaler.requestFocus()
    }

    private fun setupViews() {
        binding.scaler.text = sharedPreferences?.getString(key, "").orEmpty()
        binding.scaler.bindSettingsChoices(R.string.scaler_filter, entries)
        binding.param1.setText(sharedPreferences?.getString("${key}_param1", "").orEmpty())
        binding.param2.setText(sharedPreferences?.getString("${key}_param2", "").orEmpty())
    }

    private fun save() {
        val e = sharedPreferences?.edit()
        e?.putString(key, binding.scaler.text.toString())
        e?.putString("${key}_param1", binding.param1.text.toString())
        e?.putString("${key}_param2", binding.param2.text.toString())
        e?.apply()
    }

    private fun clear() {
        sharedPreferences?.edit()
            ?.remove(key)
            ?.remove("${key}_param1")
            ?.remove("${key}_param2")
            ?.apply()
    }
}
