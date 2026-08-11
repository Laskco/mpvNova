package app.mpvnova.player

import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager

internal const val VIDEO_PROCESSING_DEFAULT_VALUE = ""
internal const val VIDEO_PROCESSING_OFF_VALUE = "off"
private const val MPVNOVA_DEBAND_FILTER_LABEL = "@mpvnova-deband"
private const val MPVNOVA_DEBAND_FILTER = "$MPVNOVA_DEBAND_FILTER_LABEL:gradfun=radius=12"
private const val SCALER_PARAMETER_COUNT = 2

internal enum class VideoScalerSetting(
    val preferenceKey: String,
    val mpvProperty: String,
    @param:ArrayRes val entriesRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val profileDefault: String,
) {
    UPSCALING(
        preferenceKey = "video_scale",
        mpvProperty = "scale",
        entriesRes = R.array.scaler_list,
        titleRes = R.string.pref_video_upscale_title,
        summaryRes = R.string.pref_video_upscale_summary,
        profileDefault = "bilinear",
    ),
    DOWNSCALING(
        preferenceKey = "video_downscale",
        mpvProperty = "dscale",
        entriesRes = R.array.scaler_list,
        titleRes = R.string.pref_video_downscale_title,
        summaryRes = R.string.pref_video_downscale_summary,
        profileDefault = "bilinear",
    ),
    TEMPORAL(
        preferenceKey = "video_tscale",
        mpvProperty = "tscale",
        entriesRes = R.array.temporal_scaler_list,
        titleRes = R.string.pref_video_tscale_title,
        summaryRes = R.string.pref_video_tscale_summary,
        profileDefault = "oversample",
    ),
}

internal fun MPVActivity.videoScalerDrawerValue(setting: VideoScalerSetting): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    return prefs.getString(setting.preferenceKey, VIDEO_PROCESSING_DEFAULT_VALUE)
        .orEmpty()
        .ifBlank { getString(R.string.video_processing_default) }
}

internal fun MPVActivity.videoDebandingDrawerValue(): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    return when (prefs.getString("video_debanding", VIDEO_PROCESSING_DEFAULT_VALUE).orEmpty()) {
        "gradfun" -> getString(R.string.video_processing_deband_cpu)
        "gpu" -> getString(R.string.video_processing_deband_gpu)
        else -> getString(R.string.video_processing_disabled)
    }
}

internal fun MPVActivity.videoInterpolationDrawerValue(): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    if (!prefs.getBoolean("video_interpolation", false))
        return getString(R.string.video_processing_disabled)
    return prefs.getString("video_sync", getString(R.string.pref_video_interpolation_sync_default))
        ?: getString(R.string.pref_video_interpolation_sync_default)
}

internal fun MPVActivity.setVideoScaler(setting: VideoScalerSetting, value: String) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    prefs.edit().apply {
        if (value.isBlank()) {
            remove(setting.preferenceKey)
            remove("${setting.preferenceKey}_param1")
            remove("${setting.preferenceKey}_param2")
        } else {
            putString(setting.preferenceKey, value)
        }
    }.apply()

    val runtimeValue = value.ifBlank {
        applicationContext.mpvConfOption(setting.mpvProperty) ?: setting.profileDefault
    }
    setRuntimeOption(setting.mpvProperty, runtimeValue)
    applyScalerParameters(setting, prefs)
    refreshDrawerRowsIfVisible(DrawerTab.PROCESSING)
}

private fun MPVActivity.applyScalerParameters(
    setting: VideoScalerSetting,
    prefs: SharedPreferences,
) {
    for (index in 1..SCALER_PARAMETER_COUNT) {
        val preferenceValue = prefs.getString("${setting.preferenceKey}_param$index", "").orEmpty()
        val property = "${setting.mpvProperty}-param$index"
        val runtimeValue = preferenceValue.ifBlank {
            applicationContext.mpvConfOption(property) ?: "default"
        }
        setRuntimeOption(property, runtimeValue)
    }
}

internal fun MPVActivity.setVideoDebanding(value: String) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    prefs.edit().apply {
        if (value.isBlank()) remove("video_debanding") else putString("video_debanding", value)
    }.apply()

    mpvCommand(arrayOf("vf", "remove", MPVNOVA_DEBAND_FILTER_LABEL))
    setRuntimeOption("deband", if (value == "gpu") "yes" else "no")
    if (value == "gradfun")
        mpvCommand(arrayOf("vf", "add", MPVNOVA_DEBAND_FILTER))
    refreshDrawerRowsIfVisible(DrawerTab.PROCESSING)
}

internal fun MPVActivity.setVideoInterpolation(value: String) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val enabled = value != VIDEO_PROCESSING_OFF_VALUE
    prefs.edit().apply {
        putBoolean("video_interpolation", enabled)
        if (enabled) putString("video_sync", value)
    }.apply()

    if (enabled)
        setRuntimeOption("video-sync", value)
    setRuntimeOption("interpolation", if (enabled) "yes" else "no")
    refreshDrawerRowsIfVisible(DrawerTab.PROCESSING)
}

internal fun MPVView.applyFastDecodePreference(enabled: Boolean) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val shieldFallback = prefs.getString(
        "shield_decoder_fallback",
        MPVView.SHIELD_DECODER_FALLBACK_DEFAULT,
    ).toShieldDecoderFallback()
    val preservesShieldCopyTuning = isShieldH10pFallbackModeActive() &&
        shieldFallback != MPVView.SHIELD_DECODER_FALLBACK_DEFAULT

    setRuntimeOption("vd-lavc-fast", if (enabled) "yes" else "no")
    setRuntimeOption(
        "vd-lavc-skiploopfilter",
        when {
            preservesShieldCopyTuning -> "nonref"
            enabled -> "nonkey"
            else -> "default"
        },
    )
}
