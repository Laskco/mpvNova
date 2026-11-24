package app.mpvnova.player

import android.content.SharedPreferences
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import app.mpvnova.player.databinding.DialogVideoAdjustmentBinding
import java.math.BigDecimal

private const val VERTICAL_SCALE_PROPERTY = "video-scale-y"
private const val VERTICAL_SCALE_STEP = 0.01
private const val VERTICAL_SCALE_MIN = 0.01
private const val VERTICAL_SCALE_MAX = 10000.0
private const val PREF_REMEMBER_VERTICAL_SCALE = "remember_vertical_scale"
private const val PREF_VERTICAL_SCALE = "vertical_scale"

internal fun MPVActivity.applyRememberedVerticalScale() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    if (!prefs.getBoolean(PREF_REMEMBER_VERTICAL_SCALE, false)) return
    val value = prefs.getString(PREF_VERTICAL_SCALE, null)?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 0.0..VERTICAL_SCALE_MAX } ?: return
    applyVerticalScale(value)
}

private fun applyVerticalScale(value: Double) {
    // The app owns persistence for its scale changes. Leave all other native
    // resume options intact, and restore the original option list on file change.
    val resumeOptions = mpvGetPropertyString("options/watch-later-options")?.split(',')
    if (resumeOptions != null && VERTICAL_SCALE_PROPERTY in resumeOptions) {
        val remaining = resumeOptions.filterNot { it == VERTICAL_SCALE_PROPERTY }
        mpvSetPropertyString("file-local-options/watch-later-options", remaining.joinToString(","))
    }
    mpvSetPropertyDouble("file-local-options/$VERTICAL_SCALE_PROPERTY", value)
}

// mpv stores this option as a float. Its shortest decimal keeps custom values
// without rounding them to the UI step or retaining float expansion noise.
private fun currentVerticalScale(): Double = mpvGetPropertyDouble(VERTICAL_SCALE_PROPERTY)
    ?.takeIf { it.isFinite() }
    ?.toFloat()?.toString()?.toDouble() ?: 1.0

private fun SharedPreferences.saveVerticalScaleChoice(remember: Boolean, value: Double = currentVerticalScale()) {
    edit().apply {
        putBoolean(PREF_REMEMBER_VERTICAL_SCALE, remember)
        if (remember) putString(PREF_VERTICAL_SCALE, value.toString())
        else remove(PREF_VERTICAL_SCALE)
    }.apply()
}

internal fun MPVActivity.showVerticalScaleDialog() {
    val controls = DialogVideoAdjustmentBinding.inflate(layoutInflater)
    val restore = keepPlaybackForDialog()
    val prefs = getDefaultSharedPreferences(applicationContext)
    var remembered = prefs.getBoolean(PREF_REMEMBER_VERTICAL_SCALE, false)
    controls.adjustmentTitle.setText(R.string.vertical_scale)
    controls.adjustmentSliderHeader.isVisible = false
    controls.adjustmentSeekBar.isVisible = false
    controls.adjustmentStepControls.isVisible = true
    controls.rememberRow.isVisible = true
    controls.rememberCheck.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    controls.cancelBtn.setText(R.string.dialog_reset)
    controls.okBtn.setText(R.string.dialog_done)
    fun refresh(value: Double = currentVerticalScale()) {
        controls.adjustmentStepValue.setTextIfChanged(getString(R.string.vertical_scale_value, value))
    }
    fun refreshRememberState() {
        controls.rememberRow.isActivated = remembered
        controls.rememberCheck.setVisibilityIfChanged(if (remembered) View.VISIBLE else View.INVISIBLE)
        ViewCompat.setStateDescription(
            controls.rememberRow,
            getString(if (remembered) R.string.status_on else R.string.status_off),
        )
    }
    fun apply(value: Double) {
        applyVerticalScale(value)
        prefs.saveVerticalScaleChoice(remembered, value)
        refresh(value)
    }
    fun adjust(direction: Int) {
        val current = currentVerticalScale()
        if (direction < 0 && current <= VERTICAL_SCALE_MIN) return
        if (direction > 0 && current >= VERTICAL_SCALE_MAX) return
        val value = BigDecimal.valueOf(current)
            .add(BigDecimal.valueOf(direction * VERTICAL_SCALE_STEP)).toDouble()
        apply(value.coerceIn(VERTICAL_SCALE_MIN, VERTICAL_SCALE_MAX))
    }
    controls.decreaseBtn.setOnClickListener { adjust(-1) }
    controls.increaseBtn.setOnClickListener { adjust(1) }
    controls.cancelBtn.setOnClickListener { apply(1.0) }
    controls.rememberRow.setOnClickListener {
        remembered = !remembered
        prefs.saveVerticalScaleChoice(remembered)
        refreshRememberState()
    }
    val dialog = AlertDialog.Builder(this)
        .setView(controls.root)
        .setOnDismissListener {
            restore()
            reopenDrawerIfPending()
        }
        .create()
    controls.okBtn.setOnClickListener { dialog.dismiss() }
    refresh()
    refreshRememberState()
    showWidePlayerDialog(dialog, videoAdjustmentDialogLayout().copy(maxWidthDp = 450f))
    controls.increaseBtn.requestFocus()
}
