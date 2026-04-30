package app.mpvnova.player.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.EditText
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import app.mpvnova.player.R
import app.mpvnova.player.databinding.ScalerPrefBinding

class ScalerDialogPreference(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private var entries: Array<String>
    private lateinit var binding: ScalerPrefBinding

    private lateinit var s: MaterialAutoCompleteTextView
    private lateinit var e1: EditText
    private lateinit var e2: EditText

    init {
        isPersistent = false

        // read list of scalers from specified resource
        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.ScalerPreferenceDialog)
        val res = styledAttrs.getResourceId(R.styleable.ScalerPreferenceDialog_entries, -1)
        entries = context.resources.getStringArray(res)

        styledAttrs.recycle()
    }

    override fun onClick() {
        super.onClick()
        val dialog = MaterialAlertDialogBuilder(context)
        binding = ScalerPrefBinding.inflate(LayoutInflater.from(context))
        dialog.setView(binding.root)
        dialog.setTitle(title)
        setupViews()
        dialog.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        dialog.setNeutralButton(R.string.dialog_clear) { _, _ -> clear() }
        dialog.setPositiveButton(R.string.dialog_ok) { _, _ -> save() }
        dialog.create().show()
    }

    private fun setupViews() {
        s = binding.scaler as MaterialAutoCompleteTextView
        e1 = binding.param1
        e2 = binding.param2

        // populate Spinner and set selected item
        s.setSimpleItems(entries)

        val va = sharedPreferences?.getString(key, "") ?: ""
        s.setText(va, false)

        // populate EditText's
        e1.setText(sharedPreferences?.getString("${key}_param1", "") ?: "")
        e2.setText(sharedPreferences?.getString("${key}_param2", "") ?: "")
    }

    private fun save() {
        val e = sharedPreferences?.edit()
        e?.putString(key, s.text.toString())
        e?.putString("${key}_param1", e1.text.toString())
        e?.putString("${key}_param2", e2.text.toString())
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
