package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal fun MPVActivity.openPlayerBrightnessPicker(restoreState: StateRestoreCallback): Boolean {
    val previousActive = playerScreenBrightnessActive
    val previousRemember = rememberPlayerScreenBrightness
    val previousPercent = playerScreenBrightnessPercent
    val initialPercent = if (playerScreenBrightnessActive) {
        playerScreenBrightnessPercent
    } else {
        defaultPlayerScreenBrightnessPercent()
    }
    val picker = VideoAdjustmentDialog(
        config = VideoAdjustmentDialogConfig(
            titleRes = R.string.video_brightness,
            minValue = MIN_PLAYER_SCREEN_BRIGHTNESS_PERCENT,
            maxValue = MAX_PLAYER_SCREEN_BRIGHTNESS_PERCENT,
            defaultValue = DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT,
            valueFormatRes = R.string.format_fixed_number
        ),
        initialValue = initialPercent,
        initialRemember = rememberPlayerScreenBrightness,
        onPreview = ::previewPlayerScreenBrightness
    )
    var accepted = false
    lateinit var dialog: AlertDialog
    dialog = with(AlertDialog.Builder(this)) {
        setView(picker.buildView(
            layoutInflater,
            onOk = {
                accepted = true
                commitPlayerScreenBrightness(picker.value, picker.rememberValue)
                dialog.dismiss()
            },
            onCancel = { dialog.cancel() }
        ))
        setOnDismissListener {
            if (!accepted) {
                restorePlayerScreenBrightness(previousActive, previousRemember, previousPercent)
            }
            restoreState()
            reopenDrawerIfPending()
        }
        create()
    }
    showWidePlayerDialog(dialog, videoAdjustmentDialogLayout())
    return false
}

private fun MPVActivity.previewPlayerScreenBrightness(percent: Int) {
    playerScreenBrightnessActive = true
    playerScreenBrightnessPercent = percent
    applyPlayerScreenBrightnessPreference()
}

private fun MPVActivity.commitPlayerScreenBrightness(percent: Int, remember: Boolean) {
    val normalizedPercent = percent.coerceIn(
        MIN_PLAYER_SCREEN_BRIGHTNESS_PERCENT,
        MAX_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    )
    rememberPlayerScreenBrightness = remember
    playerScreenBrightnessPercent = normalizedPercent
    playerScreenBrightnessActive = remember ||
        normalizedPercent < MAX_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    getDefaultSharedPreferences(applicationContext).edit().apply {
        putBoolean("remember_player_screen_brightness", remember)
        remove("player_screen_brightness_initialized")
        if (remember) {
            putInt("player_screen_brightness_percent", normalizedPercent)
        } else {
            remove("player_screen_brightness_percent")
        }
    }.apply()
    applyPlayerScreenBrightnessPreference()
}

private fun MPVActivity.restorePlayerScreenBrightness(
    active: Boolean,
    remember: Boolean,
    percent: Int
) {
    playerScreenBrightnessActive = active
    rememberPlayerScreenBrightness = remember
    playerScreenBrightnessPercent = percent
    applyPlayerScreenBrightnessPreference()
}

internal fun videoAdjustmentDialogLayout(): PlayerDialogLayout {
    return PlayerDialogLayout(
        widthFraction = 0.56f,
        maxWidthDp = 720f,
    )
}
