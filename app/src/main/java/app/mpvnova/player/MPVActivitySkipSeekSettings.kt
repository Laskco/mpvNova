package app.mpvnova.player

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogCustomSeekStepBinding

private val SKIP_SEGMENTS_MODE_CHOICES = arrayOf(
    SkipSegmentsMode.OFF,
    SkipSegmentsMode.AUTO,
    SkipSegmentsMode.BUTTON,
)

private val SKIP_BUTTON_DISPLAY_CHOICES = arrayOf(
    SkipButtonDisplayMode.SEGMENT,
    SkipButtonDisplayMode.TEN_SECONDS,
    SkipButtonDisplayMode.THIRTY_SECONDS,
)

// Seek-step choices offered in the drawer/settings (seconds).
internal val SEEK_STEP_CHOICES_SEC = intArrayOf(5, 10, 15, 30)

internal const val SEEK_STEP_DEFAULT_SEC = 10
internal const val SEEK_STEP_MIN_SEC = 1
internal const val SEEK_STEP_MAX_SEC = 3600

private const val SEEK_STEP_CUSTOM_ID = "custom"

internal fun MPVActivity.pickSkipMode() {
    val restore = keepPlaybackForDialog()
    lateinit var dialog: AlertDialog
    val items = SKIP_SEGMENTS_MODE_CHOICES.map { mode ->
        OptionPickerDialog.Item(
            id = mode.prefValue,
            title = skipSegmentsModeLabel(mode),
            detail = skipSegmentsModeSummary(mode),
            selected = mode == skipSegmentsMode,
        )
    }
    val picker = OptionPickerDialog(
        eyebrowRes = R.string.option_picker_playback,
        titleRes = R.string.pref_skip_segments_mode_title,
        items = items,
    )
    picker.onCancelClick = { dialog.cancel() }
    picker.onItemPicked = { item ->
        setSkipMode(SkipSegmentsMode.fromPref(item.id))
        dialog.dismiss()
    }
    dialog = optionPickerDialog(picker, restore)
    showOptionPickerDialog(dialog)
}

internal fun MPVActivity.pickSkipButtonDisplay() {
    val restore = keepPlaybackForDialog()
    lateinit var dialog: AlertDialog
    val items = SKIP_BUTTON_DISPLAY_CHOICES.map { mode ->
        OptionPickerDialog.Item(
            id = mode.prefValue,
            title = skipButtonDisplayModeLabel(mode),
            detail = skipButtonDisplayModeSummary(mode),
            selected = mode == skipButtonDisplayMode,
        )
    }
    val picker = OptionPickerDialog(
        eyebrowRes = R.string.option_picker_playback,
        titleRes = R.string.pref_skip_button_display_title,
        items = items,
    )
    picker.onCancelClick = { dialog.cancel() }
    picker.onItemPicked = { item ->
        setSkipButtonDisplayMode(SkipButtonDisplayMode.fromPref(item.id))
        dialog.dismiss()
    }
    dialog = optionPickerDialog(picker, restore)
    showOptionPickerDialog(dialog)
}

internal fun MPVActivity.setSkipMode(mode: SkipSegmentsMode) {
    skipSegmentsMode = mode
    if (mode != SkipSegmentsMode.BUTTON) hideSkipButton()
    PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        .putString("skip_segments_mode", mode.prefValue).apply()
    refreshDrawerRowsIfVisible(DrawerTab.PLAYBACK)
}

internal fun MPVActivity.setSkipButtonDisplayMode(mode: SkipButtonDisplayMode) {
    skipButtonDisplayMode = mode
    autoHiddenSkipSegmentKeys.clear()
    PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        .putString("skip_button_display", mode.prefValue).apply()
    refreshSkipButtonVisibility()
    refreshDrawerRowsIfVisible(DrawerTab.PLAYBACK)
}

internal fun MPVActivity.pickSeekStep() {
    val restore = keepPlaybackForDialog()
    lateinit var dialog: AlertDialog
    val currentSec = (seekStepMs / MILLIS_PER_SECOND_LONG).toInt()
    val currentIsPreset = currentSec in SEEK_STEP_CHOICES_SEC
    var openingCustomSeekStep = false

    val presetItems = SEEK_STEP_CHOICES_SEC.map { seconds ->
        OptionPickerDialog.Item(
            id = seconds.toString(),
            title = getString(R.string.seek_step_seconds_value, seconds),
            detail = getString(R.string.seek_step_preset_summary),
            selected = currentSec == seconds,
        )
    }
    val customItem = OptionPickerDialog.Item(
        id = SEEK_STEP_CUSTOM_ID,
        title = getString(R.string.pref_seek_step_custom_entry),
        detail = if (currentIsPreset) {
            getString(R.string.seek_step_custom_summary)
        } else {
            getString(R.string.seek_step_custom_summary) + " " + seekStepLabel(seekStepMs)
        },
        selected = !currentIsPreset,
    )
    val picker = OptionPickerDialog(
        eyebrowRes = R.string.option_picker_playback,
        titleRes = R.string.pref_seek_step_title,
        items = presetItems + customItem,
    )
    picker.onCancelClick = { dialog.cancel() }
    picker.onItemPicked = { item ->
        if (item.id == SEEK_STEP_CUSTOM_ID) {
            openingCustomSeekStep = true
            dialog.dismiss()
            eventUiHandler.post { pickCustomSeekStep() }
        } else {
            setSeekStepSec(item.id.toInt())
            dialog.dismiss()
        }
    }
    dialog = optionPickerDialog(picker, restore) {
        if (!openingCustomSeekStep) reopenDrawerIfPending()
    }
    showOptionPickerDialog(dialog)
}

internal fun MPVActivity.setSeekStepSec(nextSec: Int) {
    val seconds = nextSec.coerceIn(SEEK_STEP_MIN_SEC, SEEK_STEP_MAX_SEC)
    seekStepMs = seconds.toLong() * MILLIS_PER_SECOND_LONG
    PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        .putString("seek_step_seconds", seconds.toString()).apply()
    refreshDrawerRowsIfVisible(DrawerTab.PLAYBACK)
}

// Keep ungrouped ASCII input so the toIntOrNull parser below round-trips the value.
@SuppressLint("SetTextI18n")
private fun MPVActivity.pickCustomSeekStep() {
    val restore = keepPlaybackForDialog()
    val binding = DialogCustomSeekStepBinding.inflate(layoutInflater)
    binding.customSeekStepInput.setText((seekStepMs / MILLIS_PER_SECOND_LONG).toString())
    binding.customSeekStepInput.setSelection(binding.customSeekStepInput.text.length)
    showPlayerPickerDialog(
        titleRes = R.string.pref_seek_step_custom_title,
        contentView = binding.root,
        restoreState = restore,
        layout = PlayerDialogLayout(
            widthFraction = 0.42f,
            maxWidthDp = 520f,
        ),
    ) {
        val seconds = binding.customSeekStepInput.text.toString()
            .toIntOrNull()
            ?.coerceIn(SEEK_STEP_MIN_SEC, SEEK_STEP_MAX_SEC)
            ?: (seekStepMs / MILLIS_PER_SECOND_LONG).toInt()
        setSeekStepSec(seconds)
    }
    binding.customSeekStepInput.requestFocus()
}

/** Live fast-seek toggle: prefer restoring the pre-toggle mpv.conf/runtime value when possible. */
internal fun MPVActivity.applyFastSeek(enabled: Boolean) {
    val wasEnabled = fastSeekEnabled
    fastSeekEnabled = enabled
    if (enabled) {
        if (!wasEnabled) fastSeekRestoreValue = mpvGetPropertyString("hr-seek")
        mpvSetPropertyString("hr-seek", "no")
    } else {
        mpvSetPropertyString("hr-seek", fastSeekRestoreValue ?: "default")
        fastSeekRestoreValue = null
    }
}
