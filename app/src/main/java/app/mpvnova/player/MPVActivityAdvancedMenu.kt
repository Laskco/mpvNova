package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

/**
 * Contrast / Brightness / Gamma / Saturation slider dialog. Shared by the
 * drawer's Video tab and (historically) the legacy advanced menu.
 */
internal fun MPVActivity.openVideoAdjustmentPicker(
    spec: VideoAdjustmentSpec,
    restoreState: StateRestoreCallback,
): Boolean {
    val previousValue = (
        mpvGetPropertyDouble(spec.property) ?: VIDEO_ADJUSTMENT_DEFAULT_INT.toDouble()
    ).roundToInt().coerceIn(VIDEO_ADJUSTMENT_MIN_INT, VIDEO_ADJUSTMENT_MAX_INT)
    val picker = VideoAdjustmentDialog(
        config = VideoAdjustmentDialogConfig(
            titleRes = spec.titleRes,
            minValue = VIDEO_ADJUSTMENT_MIN_INT,
            maxValue = VIDEO_ADJUSTMENT_MAX_INT,
            defaultValue = VIDEO_ADJUSTMENT_DEFAULT_INT,
            valueFormatRes = R.string.format_fixed_number
        ),
        initialValue = previousValue,
        initialRemember = rememberVideoAdjustment(spec),
        onPreview = { value -> mpvSetPropertyInt(spec.property, value) }
    )
    var accepted = false
    lateinit var dialog: AlertDialog
    dialog = with(AlertDialog.Builder(this)) {
        setView(picker.buildView(
            layoutInflater,
            onOk = {
                accepted = true
                mpvSetPropertyInt(spec.property, picker.value)
                saveVideoAdjustmentChoice(spec, picker.value, picker.rememberValue)
                dialog.dismiss()
            },
            onCancel = { dialog.cancel() }
        ))
        setOnDismissListener {
            // Cancel path: restore the property to its pre-dialog value
            // so the live preview gets reverted.
            if (!accepted) mpvSetPropertyInt(spec.property, previousValue)
            restoreState()
            reopenDrawerIfPending()
        }
        create()
    }
    showWidePlayerDialog(dialog, videoAdjustmentDialogLayout())
    return false
}
