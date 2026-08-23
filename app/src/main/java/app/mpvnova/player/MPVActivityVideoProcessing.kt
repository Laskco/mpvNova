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

private val TEMPORAL_FILTER_DETAIL_RES = mapOf(
    "oversample" to R.string.video_filter_temporal_oversample_detail,
    "linear" to R.string.video_filter_temporal_linear_detail,
    "catmull_rom" to R.string.video_filter_temporal_catmull_rom_detail,
    "mitchell" to R.string.video_filter_temporal_mitchell_detail,
    "gaussian" to R.string.video_filter_temporal_gaussian_detail,
    "bicubic" to R.string.video_filter_temporal_bicubic_detail,
)

private val SPATIAL_FILTER_DETAIL_RES = mapOf(
    "bilinear" to R.string.video_filter_bilinear_detail,
    "bicubic_fast" to R.string.video_filter_bicubic_fast_detail,
    "oversample" to R.string.video_filter_oversample_detail,
    "spline16" to R.string.video_filter_spline16_detail,
    "spline36" to R.string.video_filter_spline36_detail,
    "spline64" to R.string.video_filter_spline64_detail,
    "sinc" to R.string.video_filter_sinc_detail,
    "lanczos" to R.string.video_filter_lanczos_detail,
    "ginseng" to R.string.video_filter_ginseng_detail,
    "jinc" to R.string.video_filter_jinc_detail,
    "ewa_lanczos" to R.string.video_filter_ewa_lanczos_detail,
    "ewa_hanning" to R.string.video_filter_ewa_hanning_detail,
    "ewa_ginseng" to R.string.video_filter_ewa_ginseng_detail,
    "ewa_lanczossharp" to R.string.video_filter_ewa_lanczossharp_detail,
    "ewa_lanczos4sharpest" to R.string.video_filter_ewa_lanczos4sharpest_detail,
    "ewa_lanczossoft" to R.string.video_filter_ewa_lanczossoft_detail,
    "haasnsoft" to R.string.video_filter_haasnsoft_detail,
    "bicubic" to R.string.video_filter_bicubic_detail,
    "hermite" to R.string.video_filter_hermite_detail,
    "catmull_rom" to R.string.video_filter_catmull_rom_detail,
    "mitchell" to R.string.video_filter_mitchell_detail,
    "robidoux" to R.string.video_filter_robidoux_detail,
    "robidouxsharp" to R.string.video_filter_robidouxsharp_detail,
    "ewa_robidoux" to R.string.video_filter_ewa_robidoux_detail,
    "ewa_robidouxsharp" to R.string.video_filter_ewa_robidouxsharp_detail,
    "box" to R.string.video_filter_box_detail,
    "nearest" to R.string.video_filter_nearest_detail,
    "triangle" to R.string.video_filter_triangle_detail,
    "gaussian" to R.string.video_filter_gaussian_detail,
    "bartlett" to R.string.video_filter_bartlett_detail,
    "cosine" to R.string.video_filter_cosine_detail,
    "tukey" to R.string.video_filter_tukey_detail,
    "hamming" to R.string.video_filter_hamming_detail,
    "quadric" to R.string.video_filter_quadric_detail,
    "welch" to R.string.video_filter_welch_detail,
    "kaiser" to R.string.video_filter_kaiser_detail,
    "blackman" to R.string.video_filter_blackman_detail,
    "sphinx" to R.string.video_filter_sphinx_detail,
)

internal data class PlayerPanelChoice(
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
            PlayerPanelChoice(
                VIDEO_PROCESSING_DEFAULT_VALUE,
                getString(R.string.video_processing_default),
                getString(R.string.video_processing_default_detail),
            )
        )
        resources.getStringArray(setting.entriesRes).forEach { value ->
            add(
                PlayerPanelChoice(
                    value,
                    value.videoFilterDisplayName(),
                    getString(value.videoFilterDetailRes(setting)),
                )
            )
        }
    }
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
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
        PlayerPanelChoice(
            VIDEO_PROCESSING_DEFAULT_VALUE,
            getString(R.string.video_processing_disabled),
            getString(R.string.video_processing_deband_disabled_detail),
        ),
        PlayerPanelChoice(
            "gradfun",
            getString(R.string.video_processing_deband_cpu),
            getString(R.string.video_processing_deband_cpu_detail),
        ),
        PlayerPanelChoice(
            "gpu",
            getString(R.string.video_processing_deband_gpu),
            getString(R.string.video_processing_deband_gpu_detail),
        ),
    )
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
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
            PlayerPanelChoice(
                VIDEO_PROCESSING_OFF_VALUE,
                getString(R.string.video_processing_disabled),
                getString(R.string.video_processing_interpolation_disabled_detail),
            )
        )
        resources.getStringArray(R.array.video_sync)
            .filter { it.startsWith("display-") }
            .forEach { mode ->
                add(
                    PlayerPanelChoice(
                        mode,
                        mode.videoFilterDisplayName(),
                        getString(mode.interpolationModeDetailRes()),
                    )
                )
            }
    }
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
        titleRes = R.string.pref_video_interpolation_title,
        summaryRes = R.string.pref_video_interpolation_message,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoInterpolation(value) }
}

internal fun MPVActivity.openPlayerChoicePanel(
    @StringRes eyebrowRes: Int,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    choices: List<PlayerPanelChoice>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    val restore = keepPlaybackForDialog()
    val binding = DialogVideoProcessingBinding.inflate(layoutInflater)
    binding.videoProcessingEyebrow.setText(eyebrowRes)
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
            UiFont.applyToViewTree(binding.root)
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
    choice: PlayerPanelChoice,
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
    return replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}

@StringRes
private fun String.videoFilterDetailRes(setting: VideoScalerSetting): Int {
    val details = if (setting == VideoScalerSetting.TEMPORAL) {
        TEMPORAL_FILTER_DETAIL_RES
    } else {
        SPATIAL_FILTER_DETAIL_RES
    }
    val fallback = if (setting == VideoScalerSetting.TEMPORAL) {
        R.string.video_filter_temporal_generic_detail
    } else {
        R.string.video_filter_generic_detail
    }
    return details[this] ?: fallback
}

@StringRes
private fun String.interpolationModeDetailRes(): Int = when (this) {
    "display-resample" -> R.string.video_sync_display_resample_detail
    "display-resample-vdrop" -> R.string.video_sync_display_resample_vdrop_detail
    "display-vdrop" -> R.string.video_sync_display_vdrop_detail
    "display-adrop" -> R.string.video_sync_display_adrop_detail
    else -> R.string.video_processing_interpolation_mode_detail
}
