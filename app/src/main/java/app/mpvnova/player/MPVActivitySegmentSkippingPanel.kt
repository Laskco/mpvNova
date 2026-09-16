package app.mpvnova.player

import android.view.View
import androidx.appcompat.app.AlertDialog
import app.mpvnova.player.databinding.DialogSegmentSkippingBinding
import app.mpvnova.player.databinding.DialogSettingOptionItemBinding

internal fun MPVActivity.openSegmentSkippingPanel() {
    val restore = keepPlaybackForDialog()
    val view = DialogSegmentSkippingBinding.inflate(layoutInflater)
    for (kind in SkipSegmentKind.entries) {
        val row = DialogSettingOptionItemBinding.inflate(layoutInflater, view.segmentRows, false)
        row.optionTitleText.setText(kind.titleRes)
        row.optionTitleText.maxLines = 2
        row.optionCheck.visibility = View.GONE
        fun updateValue() {
            row.optionDetailText.text = skipSegmentsModeLabel(segmentSkipModes.getValue(kind))
        }
        updateValue()
        row.root.setOnClickListener {
            pickSkipMode(kind) {
                updateValue()
                row.root.requestFocus()
            }
        }
        view.segmentRows.addView(row.root)
    }
    val durationRow = DialogSettingOptionItemBinding.inflate(layoutInflater, view.segmentRows, false)
    durationRow.optionTitleText.setText(R.string.pref_skip_button_display_title)
    durationRow.optionTitleText.maxLines = 2
    durationRow.optionDetailText.text = skipButtonDisplayModeLabel(skipButtonDisplayMode)
    durationRow.optionCheck.visibility = View.GONE
    durationRow.root.setOnClickListener {
        pickSkipButtonDisplay {
            durationRow.optionDetailText.text = skipButtonDisplayModeLabel(skipButtonDisplayMode)
            durationRow.root.requestFocus()
        }
    }
    view.segmentRows.addView(durationRow.root)
    val dialog = AlertDialog.Builder(this).setView(view.root).create()
    dialog.setOnDismissListener {
        restore()
        reopenDrawerIfPending()
    }
    view.segmentDoneBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(dialog, SCREENSAVER_DIALOG_LAYOUT)
    view.segmentRows.post { view.segmentRows.getChildAt(0)?.requestFocus() }
}
