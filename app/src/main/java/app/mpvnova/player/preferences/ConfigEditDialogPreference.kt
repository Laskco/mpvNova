package app.mpvnova.player.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.mpvnova.player.R
import app.mpvnova.player.databinding.ConfEditorBinding
import java.io.File

class ConfigEditDialogPreference(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private var configFile: File
    private lateinit var binding: ConfEditorBinding
    private var dialogMessage: String?

    init {
        isPersistent = false

        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.ConfigEditDialog)
        val filename = styledAttrs.getString(R.styleable.ConfigEditDialog_filename)
        dialogMessage = styledAttrs.getString(R.styleable.ConfigEditDialog_dialogMessage)
        configFile = File("${context.filesDir.path}/${filename}")

        styledAttrs.recycle()
    }

    override fun onClick() {
        super.onClick()
        binding = ConfEditorBinding.inflate(LayoutInflater.from(context))
        binding.confTitle.text = title
        binding.confMessage.text = dialogMessage
        binding.confMessage.visibility = if (dialogMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        val originalContent = configFile.takeIf(File::exists)?.readText().orEmpty()
        binding.editText.setText(originalContent)
        // Buttons live in the layout (pinned below the bounded editor), matching the app's
        // other dialogs, so a long config can never push them off-screen (issue #23).
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        binding.editText.doOnTextChanged { text, _, _, _ ->
            val hasUnsavedChanges = text.toString() != originalContent
            binding.unsavedText.isVisible = hasUnsavedChanges
            dialog.setCancelable(!hasUnsavedChanges)
            dialog.setCanceledOnTouchOutside(!hasUnsavedChanges)
        }
        binding.cancelBtn.setOnClickListener { dialog.dismiss() }
        // Clear the editor in place; the empty content is persisted (file deleted) on Save.
        binding.clearBtn.setOnClickListener { binding.editText.setText("") }
        binding.saveBtn.setOnClickListener { save(); dialog.dismiss() }
        dialog.show()
    }

    private fun save() {
        val content = binding.editText.text.toString()
        if (content.isEmpty())
            configFile.delete()
        else
            configFile.writeText(content)
    }
}
