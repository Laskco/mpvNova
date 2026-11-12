package app.mpvnova.player.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import app.mpvnova.player.R
import app.mpvnova.player.databinding.InterpolationPrefBinding

class InterpolationDialogPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    private var entries: Array<String>
    private var entryDefault: String
    private lateinit var binding: InterpolationPrefBinding

    init {
        isPersistent = false

        val styledAttrs = context.obtainStyledAttributes(
            attrs, R.styleable.InterpolationPreferenceDialog
        )
        val res = styledAttrs.getResourceId(
            R.styleable.InterpolationPreferenceDialog_sync_entries, -1
        )
        entries = context.resources.getStringArray(res)
        entryDefault =
            styledAttrs.getString(R.styleable.InterpolationPreferenceDialog_sync_default)
                ?: ""

        styledAttrs.recycle()
    }

    override fun onClick() {
        super.onClick()
        binding = InterpolationPrefBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        binding.dialogTitle.text = title
        setupViews()
        binding.dialogCancel.setOnClickListener { dialog.dismiss() }
        binding.dialogOk.setOnClickListener {
            save()
            dialog.dismiss()
        }
        dialog.show()
        dialog.styleAsTvPanel()
        binding.switch1.requestFocus()
    }

    private fun setupViews() {
        binding.switch1.isChecked = sharedPreferences?.getBoolean("${key}_interpolation", false) ?: false
        binding.videoSync.text = sharedPreferences?.getString("${key}_sync", entryDefault)
            ?.takeIf { it in entries } ?: entryDefault
        binding.videoSync.bindSettingsChoices(R.string.interpolation_video_sync, entries) { value ->
            if (!value.startsWith("display-")) binding.switch1.isChecked = false
        }
        binding.switch1.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !binding.videoSync.text.startsWith("display-")) {
                binding.videoSync.text = entries.first { it.startsWith("display-") }
            }
        }
    }

    private fun save() {
        val e = sharedPreferences?.edit()
        e?.putBoolean("${key}_interpolation", binding.switch1.isChecked)
        e?.putString("${key}_sync", binding.videoSync.text.toString())
        e?.apply()
    }
}
