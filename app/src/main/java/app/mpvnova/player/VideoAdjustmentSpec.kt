package app.mpvnova.player

import androidx.annotation.StringRes
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal data class VideoAdjustmentSpec(
    @param:StringRes val titleRes: Int,
    val property: String,
    val rememberKey: String,
    val valueKey: String
)

internal val VIDEO_BRIGHTNESS_ADJUSTMENT = VideoAdjustmentSpec(
    titleRes = R.string.video_brightness,
    property = "brightness",
    rememberKey = "remember_video_brightness",
    valueKey = "video_brightness"
)

internal val VIDEO_CONTRAST_ADJUSTMENT = VideoAdjustmentSpec(
    titleRes = R.string.contrast,
    property = "contrast",
    rememberKey = "remember_video_contrast",
    valueKey = "video_contrast"
)

internal val VIDEO_GAMMA_ADJUSTMENT = VideoAdjustmentSpec(
    titleRes = R.string.gamma,
    property = "gamma",
    rememberKey = "remember_video_gamma",
    valueKey = "video_gamma"
)

internal val VIDEO_SATURATION_ADJUSTMENT = VideoAdjustmentSpec(
    titleRes = R.string.saturation,
    property = "saturation",
    rememberKey = "remember_video_saturation",
    valueKey = "video_saturation"
)

internal val VIDEO_HUE_ADJUSTMENT = VideoAdjustmentSpec(
    titleRes = R.string.video_hue,
    property = "hue",
    rememberKey = "remember_video_hue",
    valueKey = "video_hue"
)

internal val VIDEO_FILTER_ADJUSTMENTS = arrayOf(
    VIDEO_BRIGHTNESS_ADJUSTMENT,
    VIDEO_CONTRAST_ADJUSTMENT,
    VIDEO_GAMMA_ADJUSTMENT,
    VIDEO_SATURATION_ADJUSTMENT,
    VIDEO_HUE_ADJUSTMENT,
)

internal fun MPVActivity.readVideoAdjustmentSettings(
    getRemember: (String) -> Boolean,
    getValue: (String) -> Int
) {
    rememberVideoBrightness = getRemember(VIDEO_BRIGHTNESS_ADJUSTMENT.rememberKey)
    videoBrightnessValue = readVideoAdjustmentValue(VIDEO_BRIGHTNESS_ADJUSTMENT, getValue)
    rememberVideoContrast = getRemember(VIDEO_CONTRAST_ADJUSTMENT.rememberKey)
    videoContrastValue = readVideoAdjustmentValue(VIDEO_CONTRAST_ADJUSTMENT, getValue)
    rememberVideoGamma = getRemember(VIDEO_GAMMA_ADJUSTMENT.rememberKey)
    videoGammaValue = readVideoAdjustmentValue(VIDEO_GAMMA_ADJUSTMENT, getValue)
    rememberVideoSaturation = getRemember(VIDEO_SATURATION_ADJUSTMENT.rememberKey)
    videoSaturationValue = readVideoAdjustmentValue(VIDEO_SATURATION_ADJUSTMENT, getValue)
    rememberVideoHue = getRemember(VIDEO_HUE_ADJUSTMENT.rememberKey)
    videoHueValue = readVideoAdjustmentValue(VIDEO_HUE_ADJUSTMENT, getValue)
}

private fun readVideoAdjustmentValue(
    spec: VideoAdjustmentSpec,
    getValue: (String) -> Int
): Int {
    return getValue(spec.valueKey).coerceVideoAdjustment()
}

internal fun MPVActivity.applyRememberedVideoAdjustments() {
    for (spec in VIDEO_FILTER_ADJUSTMENTS) {
        val value = if (rememberVideoAdjustment(spec)) {
            rememberedVideoAdjustmentValue(spec)
        } else {
            VIDEO_ADJUSTMENT_DEFAULT_INT
        }
        mpvSetPropertyInt(spec.property, value)
    }
}

internal fun MPVActivity.rememberVideoAdjustment(spec: VideoAdjustmentSpec): Boolean {
    return when (spec) {
        VIDEO_BRIGHTNESS_ADJUSTMENT -> rememberVideoBrightness
        VIDEO_CONTRAST_ADJUSTMENT -> rememberVideoContrast
        VIDEO_GAMMA_ADJUSTMENT -> rememberVideoGamma
        VIDEO_SATURATION_ADJUSTMENT -> rememberVideoSaturation
        VIDEO_HUE_ADJUSTMENT -> rememberVideoHue
        else -> false
    }
}

internal fun MPVActivity.rememberedVideoAdjustmentValue(spec: VideoAdjustmentSpec): Int {
    return when (spec) {
        VIDEO_BRIGHTNESS_ADJUSTMENT -> videoBrightnessValue
        VIDEO_CONTRAST_ADJUSTMENT -> videoContrastValue
        VIDEO_GAMMA_ADJUSTMENT -> videoGammaValue
        VIDEO_SATURATION_ADJUSTMENT -> videoSaturationValue
        VIDEO_HUE_ADJUSTMENT -> videoHueValue
        else -> VIDEO_ADJUSTMENT_DEFAULT_INT
    }
}

internal fun MPVActivity.saveVideoAdjustmentChoice(
    spec: VideoAdjustmentSpec,
    value: Int,
    remember: Boolean
) {
    val normalizedValue = value.coerceVideoAdjustment()
    setRememberedVideoAdjustmentState(spec, remember, normalizedValue)

    videoFilterPresetId = VIDEO_FILTER_PRESET_CUSTOM
    getDefaultSharedPreferences(applicationContext).edit().apply {
        putBoolean(spec.rememberKey, remember)
        if (remember) {
            putInt(spec.valueKey, normalizedValue)
        } else {
            remove(spec.valueKey)
        }
        putString(PREF_VIDEO_FILTER_PRESET, VIDEO_FILTER_PRESET_CUSTOM)
    }.apply()
}

internal fun MPVActivity.setRememberedVideoAdjustmentState(
    spec: VideoAdjustmentSpec,
    remember: Boolean,
    value: Int,
) {
    when (spec) {
        VIDEO_BRIGHTNESS_ADJUSTMENT -> {
            rememberVideoBrightness = remember
            videoBrightnessValue = value
        }
        VIDEO_CONTRAST_ADJUSTMENT -> {
            rememberVideoContrast = remember
            videoContrastValue = value
        }
        VIDEO_GAMMA_ADJUSTMENT -> {
            rememberVideoGamma = remember
            videoGammaValue = value
        }
        VIDEO_SATURATION_ADJUSTMENT -> {
            rememberVideoSaturation = remember
            videoSaturationValue = value
        }
        VIDEO_HUE_ADJUSTMENT -> {
            rememberVideoHue = remember
            videoHueValue = value
        }
    }
}

private fun Int.coerceVideoAdjustment(): Int {
    return coerceIn(VIDEO_ADJUSTMENT_MIN_INT, VIDEO_ADJUSTMENT_MAX_INT)
}
