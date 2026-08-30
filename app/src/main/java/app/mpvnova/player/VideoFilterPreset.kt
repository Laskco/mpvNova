package app.mpvnova.player

import androidx.annotation.StringRes

internal const val PREF_VIDEO_FILTER_PRESET = "video_filter_preset"
internal const val VIDEO_FILTER_PRESET_CUSTOM = "custom"

internal enum class VideoFilterPreset(
    val prefValue: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val detailRes: Int,
    val brightness: Int,
    val saturation: Int,
    val contrast: Int,
    val gamma: Int,
    val hue: Int,
) {
    NONE(
        "none", R.string.video_filter_preset_none, R.string.video_filter_preset_none_detail,
        brightness = 0, saturation = 0, contrast = 0, gamma = 0, hue = 0,
    ),
    VIVID(
        "vivid", R.string.video_filter_preset_vivid, R.string.video_filter_preset_vivid_detail,
        brightness = 5, saturation = 25, contrast = 15, gamma = 0, hue = 0,
    ),
    WARM_TONE(
        "warm_tone", R.string.video_filter_preset_warm, R.string.video_filter_preset_warm_detail,
        brightness = 5, saturation = 10, contrast = 5, gamma = 5, hue = 15,
    ),
    COOL_TONE(
        "cool_tone", R.string.video_filter_preset_cool, R.string.video_filter_preset_cool_detail,
        brightness = 0, saturation = 5, contrast = 10, gamma = 0, hue = -15,
    ),
    SOFT_PASTEL(
        "soft_pastel", R.string.video_filter_preset_pastel, R.string.video_filter_preset_pastel_detail,
        brightness = 10, saturation = -15, contrast = -10, gamma = 5, hue = 0,
    ),
    CINEMATIC(
        "cinematic", R.string.video_filter_preset_cinematic,
        R.string.video_filter_preset_cinematic_detail,
        brightness = -5, saturation = -10, contrast = 20, gamma = -5, hue = 5,
    ),
    DRAMATIC(
        "dramatic", R.string.video_filter_preset_dramatic, R.string.video_filter_preset_dramatic_detail,
        brightness = -10, saturation = 15, contrast = 30, gamma = -10, hue = 0,
    ),
    NIGHT_MODE(
        "night_mode", R.string.video_filter_preset_night, R.string.video_filter_preset_night_detail,
        brightness = -20, saturation = -5, contrast = 5, gamma = -10, hue = 0,
    ),
    NOSTALGIC(
        "nostalgic", R.string.video_filter_preset_nostalgic,
        R.string.video_filter_preset_nostalgic_detail,
        brightness = 5, saturation = -20, contrast = 10, gamma = 0, hue = 20,
    ),
    GHIBLI_STYLE(
        "ghibli_style", R.string.video_filter_preset_ghibli, R.string.video_filter_preset_ghibli_detail,
        brightness = 8, saturation = 15, contrast = -5, gamma = 5, hue = 5,
    ),
    NEON_POP(
        "neon_pop", R.string.video_filter_preset_neon, R.string.video_filter_preset_neon_detail,
        brightness = 5, saturation = 40, contrast = 20, gamma = 0, hue = 0,
    ),
    DEEP_BLACK(
        "deep_black", R.string.video_filter_preset_deep_black,
        R.string.video_filter_preset_deep_black_detail,
        brightness = -15, saturation = 5, contrast = 25, gamma = -15, hue = 0,
    );

    fun valueFor(spec: VideoAdjustmentSpec): Int = when (spec) {
        VIDEO_BRIGHTNESS_ADJUSTMENT -> brightness
        VIDEO_CONTRAST_ADJUSTMENT -> contrast
        VIDEO_GAMMA_ADJUSTMENT -> gamma
        VIDEO_SATURATION_ADJUSTMENT -> saturation
        VIDEO_HUE_ADJUSTMENT -> hue
        else -> VIDEO_ADJUSTMENT_DEFAULT_INT
    }

    companion object {
        fun fromPref(value: String?): VideoFilterPreset? = entries.find { it.prefValue == value }
    }
}
