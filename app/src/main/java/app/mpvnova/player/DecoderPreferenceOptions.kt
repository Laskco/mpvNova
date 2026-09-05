@file:Suppress("MatchingDeclarationName")

package app.mpvnova.player

import android.os.Build
import androidx.annotation.StringRes

internal data class PreferredDecoderModeOption(
    val value: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val enabled: Boolean = true,
)

internal fun preferredDecoderModeOptions(): List<PreferredDecoderModeOption> = buildList {
    add(preferredDecoderModeOption(MPVView.DECODER_MODE_AUTO_SAFE))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        add(preferredDecoderModeOption(MPVView.DECODER_MODE_HW_PLUS))
    }
    add(preferredDecoderModeOption(MPVView.DECODER_MODE_HW))
    add(preferredDecoderModeOption(MPVView.DECODER_MODE_SW))
    add(preferredDecoderModeOption(MPVView.DECODER_MODE_GNEXT))
    if (supportsGpuNextDirect()) {
        add(preferredDecoderModeOption(MPVView.DECODER_MODE_GNEXT_DIRECT))
    }
    add(preferredDecoderModeOption(MPVView.DECODER_MODE_MPV_CONF))
    add(preferredDecoderModeOption(MPVView.DECODER_MODE_SHIELD_H10P))
}

private fun preferredDecoderModeOption(mode: String): PreferredDecoderModeOption =
    PreferredDecoderModeOption(
        value = mode,
        titleRes = decoderModeSettingsTitleRes(mode),
        descriptionRes = decoderModeDescriptionRes(mode),
        enabled = mode != MPVView.DECODER_MODE_GNEXT_DIRECT || canSelectGpuNextDirect(),
    )

@StringRes
internal fun decoderModeSettingsTitleRes(mode: String): Int = when (mode) {
    MPVView.DECODER_MODE_AUTO_SAFE -> R.string.decoder_mode_auto_safe_settings
    MPVView.DECODER_MODE_HW_PLUS -> R.string.decoder_mode_hw_plus_settings
    MPVView.DECODER_MODE_HW -> R.string.decoder_mode_hw_settings
    MPVView.DECODER_MODE_SW -> R.string.decoder_mode_sw_settings
    MPVView.DECODER_MODE_GNEXT -> R.string.decoder_mode_gnext_settings
    MPVView.DECODER_MODE_GNEXT_DIRECT -> R.string.decoder_mode_gnext_direct_settings
    MPVView.DECODER_MODE_SHIELD_H10P -> R.string.decoder_mode_shield_h10p_settings
    MPVView.DECODER_MODE_MPV_CONF -> R.string.decoder_mode_mpv_conf_settings
    else -> R.string.pref_preferred_decoder_mode_summary
}

@StringRes
internal fun decoderModeDescriptionRes(mode: String?): Int = when (mode) {
    MPVView.DECODER_MODE_AUTO_SAFE -> R.string.decoder_mode_auto_safe_description
    MPVView.DECODER_MODE_HW_PLUS -> R.string.decoder_mode_hw_plus_description
    MPVView.DECODER_MODE_HW -> R.string.decoder_mode_hw_description
    MPVView.DECODER_MODE_SW -> R.string.decoder_mode_sw_description
    MPVView.DECODER_MODE_GNEXT -> R.string.decoder_mode_gnext_description
    MPVView.DECODER_MODE_GNEXT_DIRECT -> R.string.decoder_mode_gnext_direct_description
    MPVView.DECODER_MODE_SHIELD_H10P -> R.string.decoder_mode_shield_h10p_description
    MPVView.DECODER_MODE_MPV_CONF -> R.string.decoder_mode_mpv_conf_description
    else -> R.string.pref_preferred_decoder_mode_summary
}

internal fun defaultPreferredDecoderMode(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        MPVView.DECODER_MODE_HW_PLUS
    else
        MPVView.DECODER_MODE_HW
}

internal fun normalizedPreferredDecoderMode(mode: String?): String {
    val options = preferredDecoderModeOptions()
    val fallback = defaultPreferredDecoderMode().takeIf { preferred ->
        options.any { it.value == preferred }
    } ?: options.first().value
    val compatibleMode = if (mode == MPVView.DECODER_MODE_GNEXT_DIRECT && !canSelectGpuNextDirect()) {
        MPVView.DECODER_MODE_GNEXT
    } else {
        mode
    }
    return compatibleMode?.takeIf { candidate -> options.any { it.value == candidate } } ?: fallback
}

internal fun decoderModeCompactLabel(mode: String): String = when (mode) {
    MPVView.DECODER_MODE_AUTO_SAFE -> "AUTO"
    MPVView.DECODER_MODE_HW_PLUS -> "HW+"
    MPVView.DECODER_MODE_HW -> "HW"
    MPVView.DECODER_MODE_SW -> "SW"
    MPVView.DECODER_MODE_GNEXT -> "G-NXT"
    MPVView.DECODER_MODE_GNEXT_DIRECT -> "G+DIR"
    MPVView.DECODER_MODE_SHIELD_H10P -> "G+SW"
    MPVView.DECODER_MODE_MPV_CONF -> "CFG"
    else -> "HW"
}

internal fun supportsGpuNextDirect(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

internal fun canSelectGpuNextDirect(): Boolean =
    supportsGpuNextDirect() && !isNvidiaShieldDevice()
