package app.mpvnova.player

import android.os.Build
import androidx.annotation.StringRes

internal fun MPVActivity.clampSubFilterState() {
    subScaleLevel = subScaleLevel.coerceIn(0, subScaleSteps.lastIndex)
    subPosLevel = subPosLevel.coerceIn(0, subPosSteps.lastIndex)
    secondaryPosLevel = secondaryPosLevel.coerceIn(0, secondaryPosSteps.lastIndex)
}

internal fun MPVActivity.pickSpeed() {
    val picker = SpeedPickerDialog()
    val restore = keepPlaybackForDialog()
    genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
        restore()
        reopenDrawerIfPending()
    }
}

internal fun MPVActivity.goIntoPiP() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    enterPictureInPictureMode(buildPiPParams())
}

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
            widthFraction = 0.56f,
            maxWidthDp = 620f,
            heightFraction = 0.62f,
            maxHeightDp = 520f,
        ),
    ) {
        picker.number?.let {
            if (picker.isInteger()) mpvSetPropertyInt(property, it.toInt())
            else mpvSetPropertyDouble(property, it)
        }
    }
}
