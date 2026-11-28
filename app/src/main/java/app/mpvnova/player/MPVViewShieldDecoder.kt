package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

// Shield MediaCodec can't decode 10-bit H.264. Both fallback flavors keep the
// G-NEXT renderer but skip MediaCodec so Hi10P starts on lavc software decode.
// DEFAULT keeps the standard G-NEXT tuning; COPY adds the light tuning below.
internal fun MPVView.applyShieldHi10pFallback(fallback: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    applyShieldHi10pFallback(sharedPreferences, fallback)
}

internal fun MPVView.applyShieldHi10pFallback(sharedPreferences: SharedPreferences) {
    applyShieldHi10pFallback(
        sharedPreferences,
        sharedPreferences.getString(
            "shield_decoder_fallback",
            MPVView.SHIELD_DECODER_FALLBACK_DEFAULT
        ).toShieldDecoderFallback()
    )
}

private fun MPVView.applyShieldHi10pFallback(sharedPreferences: SharedPreferences, fallback: String) {
    applyNativeDecoderFallbackPolicy(MPVView.DECODER_MODE_SHIELD_H10P)
    when (fallback.toShieldDecoderFallback()) {
        MPVView.SHIELD_DECODER_FALLBACK_COPY -> applyShieldHi10pCopyFallback(sharedPreferences)
        else -> applyShieldHi10pDefaultFallback(sharedPreferences)
    }
}

private fun MPVView.applyShieldHi10pDefaultFallback(sharedPreferences: SharedPreferences) {
    setRuntimeOption("hwdec", MPV_VIEW_HWDEC_NONE)
    applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU_NEXT)
}

private fun MPVView.applyShieldHi10pCopyFallback(sharedPreferences: SharedPreferences) {
    setRuntimeOption("hwdec", MPV_VIEW_HWDEC_NONE)
    applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU_NEXT)
    setRuntimeOption("vd-lavc-skiploopfilter", "nonref")
    setRuntimeOption("audio-buffer", "1.0")
    // Sharp upscale for gpu-next software decode.
    setRuntimeOption("scale", "ewa_lanczossharp")
}

internal fun MPVView.isAlreadyRunningHi10pDefaultSoftwareFallback(fallback: String): Boolean {
    if (fallback.toShieldDecoderFallback() != MPVView.SHIELD_DECODER_FALLBACK_DEFAULT) {
        return false
    }
    if (!isHi10pH264Video() || !matchesShieldOption("vo", MPV_VIEW_VO_GPU_NEXT)) {
        return false
    }
    return mpvGetPropertyString("hwdec-current")?.trim()?.lowercase() == MPV_VIEW_HWDEC_NONE
}

// New builds request hwdec=no directly. Keep the legacy copy-tuning check so
// an already-running session from the old path still highlights Shield Hi10P.
internal fun MPVView.isShieldH10pFallbackModeActive(): Boolean =
    isNvidiaShieldDevice() && isHi10pFallbackModeActive()

internal fun MPVView.isHi10pFallbackModeActive(): Boolean {
    if (!isHi10pH264Video() ||
        !matchesShieldOption("vo", MPV_VIEW_VO_GPU_NEXT)
    ) {
        return false
    }
    val directSoftware = matchesShieldOption("hwdec", MPV_VIEW_HWDEC_NONE)
    val legacyCopyTuning = matchesShieldOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY) &&
        matchesShieldOption("vd-lavc-skiploopfilter", "nonref") &&
        matchesShieldOption("audio-buffer", "1.0", "1.000000", "1")
    if (directSoftware || legacyCopyTuning) {
        return true
    }
    val fallback = PreferenceManager.getDefaultSharedPreferences(context)
        .getString("shield_decoder_fallback", MPVView.SHIELD_DECODER_FALLBACK_DEFAULT)
        .toShieldDecoderFallback()
    return isAlreadyRunningHi10pDefaultSoftwareFallback(fallback)
}

private fun MPVView.matchesShieldOption(name: String, vararg expected: String): Boolean {
    val value = getOptionString(name).trim().lowercase()
    return expected.any { value == it.lowercase() }
}

// Legacy "g_next_framedrop" maps to COPY now that standard tuning uses mpv's default framedrop=vo.
// Unknown values (including the removed legacy "g_next_sw") map to DEFAULT.
internal fun String?.toShieldDecoderFallback(): String {
    return when (this) {
        MPVView.SHIELD_DECODER_FALLBACK_FRAMEDROP,
        MPVView.SHIELD_DECODER_FALLBACK_COPY -> MPVView.SHIELD_DECODER_FALLBACK_COPY
        else -> MPVView.SHIELD_DECODER_FALLBACK_DEFAULT
    }
}
