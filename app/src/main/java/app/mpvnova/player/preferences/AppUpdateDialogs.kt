package app.mpvnova.player.preferences

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import app.mpvnova.player.R
import app.mpvnova.player.databinding.DialogAppUpdateBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

internal fun AppUpdateManager.showAvailableUpdateDialog(release: ReleaseInfo) {
    showGlassDialog(
        GlassDialogOptions(
            title = activity.getString(R.string.update_available_title),
            version = activity.getString(R.string.update_new_version, release.tagName),
            releaseTitle = release.displayTitle(),
            notesHeading = activity.getString(R.string.update_notes_heading),
            notes = release.notes.ifBlank { activity.getString(R.string.update_notes_empty) }.cleanMarkdown(),
            primaryText = activity.getString(R.string.update_download),
            ignoreText = activity.getString(R.string.update_ignore),
            onPrimary = { downloadUpdate(release) },
            onIgnore = {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                    .edit()
                    .putString(IGNORED_UPDATE_TAG_KEY, release.tagName)
                    .apply()
            }
        )
    )
}

internal fun AppUpdateManager.showDownloadedUpdateDialog(release: ReleaseInfo, apkFile: File) {
    showGlassDialog(
        GlassDialogOptions(
            title = activity.getString(R.string.update_available_title),
            version = activity.getString(R.string.update_new_version, release.tagName),
            releaseTitle = activity.getString(R.string.update_download_complete_title),
            notesHeading = release.displayTitle(),
            notes = activity.getString(
                R.string.update_download_complete,
                release.tagName,
                release.assetName
            ),
            primaryText = activity.getString(R.string.update_install),
            onPrimary = { installDownloadedApk(release.tagName, apkFile) }
        )
    )
}

internal fun AppUpdateManager.showBusy(message: String) {
    hideBusy()
    busyDialog = showGlassDialog(
        GlassDialogOptions(
            title = activity.getString(R.string.update_available_title),
            releaseTitle = message,
            showClose = false
        )
    ).apply {
        setCancelable(false)
    }
}

internal fun AppUpdateManager.hideBusy() {
    busyDialog?.dismiss()
    busyDialog = null
}

internal fun AppUpdateManager.showError(message: String) {
    showGlassDialog(
        GlassDialogOptions(
            title = activity.getString(R.string.update_error_title),
            notes = message
        )
    )
}

internal fun AppUpdateManager.showGlassDialog(options: GlassDialogOptions): AlertDialog {
    lateinit var dialog: AlertDialog
    val binding = DialogAppUpdateBinding.inflate(activity.layoutInflater)
    binding.updateTitle.text = options.title
    binding.updateVersion.setTextOrGone(options.version)
    binding.updateReleaseTitle.setTextOrGone(options.releaseTitle)
    binding.updateNotesHeading.setTextOrGone(options.notesHeading)
    binding.updateNotes.text = options.notes
    binding.updateNotesScroll.visibility = if (options.notes.isBlank()) View.GONE else View.VISIBLE

    binding.updateCloseButton.visibility = if (options.showClose) View.VISIBLE else View.GONE
    binding.updateCloseButton.setOnClickListener { dialog.dismiss() }

    configurePrimaryButton(binding, options) { dialog.dismiss() }
    configureIgnoreButton(binding, options) { dialog.dismiss() }

    binding.updateActions.visibility =
        if (
            binding.updateCloseButton.visibility == View.VISIBLE ||
            binding.updatePrimaryButton.visibility == View.VISIBLE ||
            binding.updateIgnoreButton.visibility == View.VISIBLE
        ) View.VISIBLE else View.GONE

    dialog = MaterialAlertDialogBuilder(activity)
        .setView(binding.root)
        .create()
    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.dialogFocusTarget()?.requestFocus()
    }
    dialog.show()
    return dialog
}

private fun configurePrimaryButton(binding: DialogAppUpdateBinding, options: GlassDialogOptions, dismiss: () -> Unit) {
    if (options.primaryText == null || options.onPrimary == null) {
        binding.updatePrimaryButton.visibility = View.GONE
    } else {
        binding.updatePrimaryButton.text = options.primaryText
        binding.updatePrimaryButton.setOnClickListener {
            dismiss()
            options.onPrimary()
        }
    }
}

private fun configureIgnoreButton(binding: DialogAppUpdateBinding, options: GlassDialogOptions, dismiss: () -> Unit) {
    if (options.ignoreText == null || options.onIgnore == null) {
        binding.updateIgnoreButton.visibility = View.GONE
    } else {
        binding.updateIgnoreButton.text = options.ignoreText
        binding.updateIgnoreButton.setOnClickListener {
            options.onIgnore()
            dismiss()
        }
    }
}

private fun DialogAppUpdateBinding.dialogFocusTarget(): View? {
    return when {
        updatePrimaryButton.visibility == View.VISIBLE -> updatePrimaryButton
        updateCloseButton.visibility == View.VISIBLE -> updateCloseButton
        else -> null
    }
}

private fun TextView.setTextOrGone(value: String?) {
    text = value.orEmpty()
    visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
}
