package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

internal fun MPVActivity.pickVideoFilterPreset() {
    val choices = VideoFilterPreset.entries.map { preset ->
        PlayerPanelChoice(
            value = preset.prefValue,
            title = getString(preset.titleRes),
            detail = getString(preset.detailRes),
        )
    }
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
        titleRes = R.string.video_filter_presets_title,
        summaryRes = R.string.video_filter_presets_summary,
        choices = choices,
        selectedValue = videoFilterPresetId,
    ) { value ->
        VideoFilterPreset.fromPref(value)?.let(::setVideoFilterPreset)
    }
}

internal fun MPVActivity.setVideoFilterPreset(preset: VideoFilterPreset) {
    videoFilterPresetId = preset.prefValue
    VIDEO_FILTER_ADJUSTMENTS.forEach { spec ->
        val value = preset.valueFor(spec)
        setRememberedVideoAdjustmentState(spec, remember = true, value = value)
        mpvSetPropertyInt(spec.property, value)
    }
    writeVideoFilterPreset(
        PreferenceManager.getDefaultSharedPreferences(applicationContext),
        preset,
    )
    refreshDrawerRowsIfVisible(DrawerTab.PROCESSING)
}

internal fun writeVideoFilterPreset(prefs: SharedPreferences, preset: VideoFilterPreset) {
    prefs.edit().apply {
        putString(PREF_VIDEO_FILTER_PRESET, preset.prefValue)
        VIDEO_FILTER_ADJUSTMENTS.forEach { spec ->
            putBoolean(spec.rememberKey, true)
            putInt(spec.valueKey, preset.valueFor(spec))
        }
    }.apply()
}

internal fun MPVActivity.videoFilterPresetLabel(): String {
    val preset = VideoFilterPreset.fromPref(videoFilterPresetId)
    return if (preset != null) getString(preset.titleRes) else getString(R.string.video_filter_preset_custom)
}
