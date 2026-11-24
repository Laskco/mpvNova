package app.mpvnova.player.preferences

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.Preference
import app.mpvnova.player.R
import app.mpvnova.player.databinding.ConfEditorBinding
import java.io.File
import java.io.IOException

class ConfigEditDialogPreference(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private var configFile: File
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
        val originalContent = try {
            configFile.takeIf(File::exists)?.readText().orEmpty()
        } catch (_: IOException) {
            Toast.makeText(context, R.string.config_editor_read_failed, Toast.LENGTH_LONG).show()
            return
        }
        val binding = ConfEditorBinding.inflate(LayoutInflater.from(context))
        binding.confTitle.text = title
        binding.confMessage.text = dialogMessage
        binding.confMessage.visibility = if (dialogMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.editText.setText(originalContent)
        // Buttons live in the layout (pinned below the bounded editor), matching the app's
        // other dialogs, so a long config can never push them off-screen (issue #23).
        val dialog = AlertDialog.Builder(context)
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
        binding.saveBtn.setOnClickListener {
            val content = binding.editText.text.toString()
            if (content == originalContent || save(content)) dialog.dismiss()
            else Toast.makeText(context, R.string.config_editor_save_failed, Toast.LENGTH_LONG).show()
        }
        dialog.show()
        dialog.styleAsTvPanel()
    }

    private fun save(content: String): Boolean = try {
        if (content.isEmpty()) {
            if (configFile.isDirectory) throw IOException("Configuration path is a directory")
            Os.remove(configFile.path)
        } else {
            val temporary = File.createTempFile(".config-", ".tmp", configFile.parentFile)
            try {
                temporary.writeText(content)
                Os.rename(temporary.path, configFile.path)
            } finally {
                temporary.delete()
            }
        }
        true
    } catch (_: IOException) {
        false
    } catch (error: ErrnoException) {
        content.isEmpty() && error.errno == OsConstants.ENOENT
    }
}
