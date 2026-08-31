package app.mpvnova.player

import android.os.Build
import androidx.annotation.StringRes

internal fun MPVActivity.clampSubFilterState() {
    subScaleLevel = subScaleLevel.coerceIn(0, subScaleSteps.lastIndex)
    subPosLevel = subPosLevel.coerceIn(0, subPosSteps.lastIndex)
    secondaryPosLevel = secondaryPosLevel.coerceIn(0, secondaryPosSteps.lastIndex)
}

internal fun MPVActivity.pickSpeed() {
    val picker = speedPickerDialog ?: SpeedPickerDialog().also {
        speedPickerDialog = it
    }
    val restore = keepPlaybackForDialog()
    genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
        restore()
        reopenDrawerIfPending()
    }
}

internal fun MPVActivity.goIntoPiP() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    // External (trampoline) launches don't support PiP — it traps input behind the caller.
    if (intent.getBooleanExtra(EXTRA_EXTERNAL_PLAYER_RESULT, false)) return
    enterPictureInPictureMode(buildPiPParams())
}

internal fun MPVActivity.isPictureInPictureActionAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature("android.software.picture_in_picture") &&
        !intent.getBooleanExtra(EXTRA_EXTERNAL_PLAYER_RESULT, false)

internal fun MPVActivity.genericPickerDialog(
    picker: PickerDialog,
    @StringRes titleRes: Int,
    property: String,
    restoreState: StateRestoreCallback,
) {
    val pickerView = picker.buildView(layoutInflater)
    picker.number = mpvGetPropertyDouble(property)
    showPlayerPickerDialog(
        titleRes = titleRes,
        contentView = pickerView,
        restoreState = restoreState,
        layout = PlayerDialogLayout(
            widthFraction = 0.46f,
            maxWidthDp = 560f,
        ),
    ) {
        picker.number?.let {
            if (picker.isInteger()) mpvSetPropertyInt(property, it.toInt())
            else mpvSetPropertyDouble(property, it)
        }
    }
}
