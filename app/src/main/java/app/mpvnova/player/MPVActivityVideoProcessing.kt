package app.mpvnova.player

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogVideoProcessingBinding

private const val VIDEO_PROCESSING_ROW_TITLE_LINES = 2
private const val VIDEO_PROCESSING_ROW_DETAIL_LINES = 3

private data class VideoProcessingChoice(
    val value: String,
    val title: String,
    val detail: String = "",
)

internal fun MPVActivity.pickVideoScaler(setting: VideoScalerSetting) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val selected = prefs.getString(setting.preferenceKey, VIDEO_PROCESSING_DEFAULT_VALUE)
        .orEmpty()
    val choices = buildList {
        add(
            VideoProcessingChoice(
                VIDEO_PROCESSING_DEFAULT_VALUE,
                getString(R.string.video_processing_default),
                getString(R.string.video_processing_default_detail),
            )
        )
        resources.getStringArray(setting.entriesRes).forEach { value ->
            add(VideoProcessingChoice(value, value.videoFilterDisplayName()))
        }
    }
    openVideoProcessingPanel(
        titleRes = setting.titleRes,
        summaryRes = setting.summaryRes,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoScaler(setting, value) }
}

internal fun MPVActivity.pickVideoDebanding() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val selected = prefs.getString("video_debanding", VIDEO_PROCESSING_DEFAULT_VALUE).orEmpty()
    val choices = listOf(
        VideoProcessingChoice(
            VIDEO_PROCESSING_DEFAULT_VALUE,
            getString(R.string.video_processing_disabled),
            getString(R.string.video_processing_deband_disabled_detail),
        ),
        VideoProcessingChoice(
            "gradfun",
            getString(R.string.video_processing_deband_cpu),
            getString(R.string.video_processing_deband_cpu_detail),
        ),
        VideoProcessingChoice(
            "gpu",
            getString(R.string.video_processing_deband_gpu),
            getString(R.string.video_processing_deband_gpu_detail),
        ),
    )
    openVideoProcessingPanel(
        titleRes = R.string.pref_video_debanding_title,
        summaryRes = R.string.pref_video_debanding_summary,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoDebanding(value) }
}

internal fun MPVActivity.pickVideoInterpolation() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val enabled = prefs.getBoolean("video_interpolation", false)
    val sync = prefs.getString("video_sync", getString(R.string.pref_video_interpolation_sync_default))
        ?: getString(R.string.pref_video_interpolation_sync_default)
    val selected = if (enabled && sync.startsWith("display-")) sync else VIDEO_PROCESSING_OFF_VALUE
    val choices = buildList {
        add(
            VideoProcessingChoice(
                VIDEO_PROCESSING_OFF_VALUE,
                getString(R.string.video_processing_disabled),
                getString(R.string.video_processing_interpolation_disabled_detail),
            )
        )
        resources.getStringArray(R.array.video_sync)
            .filter { it.startsWith("display-") }
            .forEach { mode ->
                add(
                    VideoProcessingChoice(
                        mode,
                        mode.videoFilterDisplayName(),
                        getString(R.string.video_processing_interpolation_mode_detail),
                    )
                )
            }
    }
    openVideoProcessingPanel(
        titleRes = R.string.pref_video_interpolation_title,
        summaryRes = R.string.pref_video_interpolation_message,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoInterpolation(value) }
}

private fun MPVActivity.openVideoProcessingPanel(
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    choices: List<VideoProcessingChoice>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    val restore = keepPlaybackForDialog()
    val binding = DialogVideoProcessingBinding.inflate(layoutInflater)
    binding.videoProcessingTitle.setText(titleRes)
    binding.videoProcessingSummary.setText(summaryRes)
    val checks = mutableMapOf<String, ImageView>()
    var selectedRow: View? = null

    choices.forEach { choice ->
        val row = inflateVideoProcessingRow(
            binding.videoProcessingRows,
            choice,
            choice.value == selectedValue,
        )
        val check = row.findViewById<ImageView>(R.id.optionCheck)
        checks[choice.value] = check
        if (choice.value == selectedValue)
            selectedRow = row
        row.setOnClickListener {
            onSelected(choice.value)
            checks.forEach { (value, image) ->
                image.visibility = if (value == choice.value) View.VISIBLE else View.INVISIBLE
            }
        }
        binding.videoProcessingRows.addView(row)
    }

    val dialog = AlertDialog.Builder(this).setView(binding.root).create()
    dialog.setOnDismissListener {
        restore()
        reopenDrawerIfPending()
    }
    binding.videoProcessingDoneBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(dialog, VIDEO_PROCESSING_DIALOG_LAYOUT)
    val focusRow = selectedRow ?: binding.videoProcessingRows.getChildAt(0)
    focusRow.post { focusRow.requestFocus() }
}

private fun MPVActivity.inflateVideoProcessingRow(
    parent: ViewGroup,
    choice: VideoProcessingChoice,
    selected: Boolean,
): View {
    val row = layoutInflater.inflate(R.layout.dialog_setting_option_item, parent, false)
    row.findViewById<TextView>(R.id.optionTitleText).apply {
        text = choice.title
        maxLines = VIDEO_PROCESSING_ROW_TITLE_LINES
    }
    row.findViewById<TextView>(R.id.optionDetailText).apply {
        if (choice.detail.isBlank()) {
            visibility = View.GONE
        } else {
            text = choice.detail
            maxLines = VIDEO_PROCESSING_ROW_DETAIL_LINES
        }
    }
    row.findViewById<ImageView>(R.id.optionCheck).visibility =
        if (selected) View.VISIBLE else View.INVISIBLE
    return row
}

private fun String.videoFilterDisplayName(): String {
    return replace('_', ' ').replaceFirstChar { it.uppercase() }
}
