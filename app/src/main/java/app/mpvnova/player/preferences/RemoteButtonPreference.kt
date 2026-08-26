package app.mpvnova.player.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import app.mpvnova.player.R
import app.mpvnova.player.REMOTE_BUTTON_DISABLED
import app.mpvnova.player.remoteButtonCanBeAssigned
import app.mpvnova.player.remoteButtonDisplayName
import app.mpvnova.player.remoteButtonLetsCaptureDialogHandle
import app.mpvnova.player.databinding.DialogRemoteButtonCaptureBinding

class RemoteButtonPreference(
    context: Context,
    attrs: AttributeSet?
) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    override fun onClick() {
        showCaptureDialog()
    }

    private fun showCaptureDialog() {
        val binding = DialogRemoteButtonCaptureBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        binding.captureTitle.text = title
        binding.captureCancel.setOnClickListener { dialog.dismiss() }
        binding.captureClear.setOnClickListener {
            persistRemoteButton(REMOTE_BUTTON_DISABLED)
            dialog.dismiss()
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            handleCapturedKey(dialog, keyCode, event)
        }
        dialog.show()
        dialog.styleAsTvPanel()
        binding.captureCancel.requestFocus()
    }

    private fun handleCapturedKey(dialog: AlertDialog, keyCode: Int, event: KeyEvent): Boolean {
        if (remoteButtonLetsCaptureDialogHandle(keyCode))
            return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (!remoteButtonCanBeAssigned(keyCode)) {
                Toast.makeText(
                    context,
                    R.string.pref_remote_next_chapter_reserved_button,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                persistRemoteButton(keyCode.toString())
                dialog.dismiss()
            }
        }
        return true
    }

    private fun persistRemoteButton(value: String) {
        if (!callChangeListener(value))
            return
        persistString(value)
        updateSummary()
    }

    private fun updateSummary() {
        summary = remoteButtonDisplayName(
            context,
            getPersistedString(REMOTE_BUTTON_DISABLED)
        )
    }
}
