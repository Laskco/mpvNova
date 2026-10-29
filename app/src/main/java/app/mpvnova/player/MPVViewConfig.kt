package app.mpvnova.player

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.File

private const val MPV_CONF_FILENAME = "mpv.conf"

internal fun startupPreferredDecoderMode(sharedPreferences: SharedPreferences): String? {
    if (sharedPreferences.getBoolean("decoder_auto_fallback", true))
        return null
    return sharedPreferences.getString("preferred_decoder_mode", "")
        ?.takeIf { it.isNotBlank() }
}

internal fun MPVView.startupVo(
    sharedPreferences: SharedPreferences,
    startupDecoderMode: String?
): String? {
    return when (startupDecoderMode) {
        MPVView.DECODER_MODE_MPV_CONF -> null
        MPVView.DECODER_MODE_HW_PLUS, MPVView.DECODER_MODE_HW, MPVView.DECODER_MODE_SW -> MPV_VIEW_VO_GPU
        MPVView.DECODER_MODE_GNEXT, MPVView.DECODER_MODE_GNEXT_DIRECT,
        MPVView.DECODER_MODE_SHIELD_H10P -> MPV_VIEW_VO_GPU_NEXT
        else -> defaultVo(sharedPreferences)
    }
}

internal fun MPVView.startupHwdec(
    sharedPreferences: SharedPreferences,
    startupDecoderMode: String?
): String? {
    return when (startupDecoderMode) {
        MPVView.DECODER_MODE_MPV_CONF -> null
        MPVView.DECODER_MODE_AUTO_SAFE -> MPV_VIEW_HWDEC_AUTO_SAFE
        MPVView.DECODER_MODE_HW_PLUS, MPVView.DECODER_MODE_GNEXT_DIRECT -> MPV_VIEW_HWDEC_MEDIACODEC
        MPVView.DECODER_MODE_HW, MPVView.DECODER_MODE_GNEXT -> MPV_VIEW_HWDEC_MEDIACODEC_COPY
        MPVView.DECODER_MODE_SW, MPVView.DECODER_MODE_SHIELD_H10P -> MPV_VIEW_HWDEC_NONE
        else -> defaultHwdec(sharedPreferences)
    }
}

internal fun nativeSoftwareFallback(
    mode: String?,
    autoFallback: Boolean,
    hi10pFallback: Boolean,
    configuredFallback: String,
): String = when (mode) {
    MPVView.DECODER_MODE_SW, MPVView.DECODER_MODE_SHIELD_H10P ->
        configuredFallback.takeUnless { it == "no" } ?: "3"
    MPVView.DECODER_MODE_AUTO_SAFE, MPVView.DECODER_MODE_HW_PLUS, MPVView.DECODER_MODE_HW,
    MPVView.DECODER_MODE_GNEXT, MPVView.DECODER_MODE_GNEXT_DIRECT ->
        if (!autoFallback && !hi10pFallback) "no" else configuredFallback
    else -> configuredFallback
}

internal fun MPVView.applyNativeDecoderFallbackPolicy(mode: String?) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val value = nativeSoftwareFallback(
        mode, prefs.getBoolean("decoder_auto_fallback", true), prefs.hi10pFallbackEnabled(),
        configuredSoftwareFallback,
    )
    if (getOptionString("hwdec-software-fallback") != value)
        setRuntimeOption("hwdec-software-fallback", value)
}

internal fun Context.mpvConfOption(optionName: String): String? {
    val normalizedOption = optionName.trim().lowercase()
    val file = File(filesDir, MPV_CONF_FILENAME)
    return if (normalizedOption.isBlank() || !file.isFile) {
        null
    } else {
        file.findMpvConfOption(normalizedOption)
    }
}

private fun File.findMpvConfOption(normalizedOption: String): String? {
    var inProfile = false
    var value: String? = null
    forEachLine { rawLine ->
        val line = rawLine.stripMpvConfComment().trim()
        if (line.isBlank())
            return@forEachLine
        if (line.startsWith("[") && line.endsWith("]")) {
            inProfile = line != "[default]"
            return@forEachLine
        }
        if (inProfile)
            return@forEachLine

        val cleaned = line.removePrefix("--")
        val (key, parsedValue) = when {
            "=" in cleaned -> cleaned.substringBefore("=") to cleaned.substringAfter("=")
            " " in cleaned -> cleaned.substringBefore(" ") to cleaned.substringAfter(" ")
            else -> return@forEachLine
        }
        if (key.trim().lowercase() == normalizedOption)
            value = parsedValue.trim().takeIf { it.isNotBlank() }
    }
    return value
}

private fun String.stripMpvConfComment(): String {
    val index = indexOf('#')
    return if (index >= 0) substring(0, index) else this
}
