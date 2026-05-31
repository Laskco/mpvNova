package app.mpvnova.player

import android.content.Context
import android.content.SharedPreferences
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
        else -> defaultVo(sharedPreferences)
    }
}

internal fun MPVView.startupHwdec(
    sharedPreferences: SharedPreferences,
    startupDecoderMode: String?
): String? {
    return when (startupDecoderMode) {
        MPVView.DECODER_MODE_MPV_CONF -> null
        else -> defaultHwdec(sharedPreferences)
    }
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
