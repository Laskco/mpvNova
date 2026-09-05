package app.mpvnova.player

private const val SHIELD_MPEG2_FALLBACK_PROPERTY = "user-data/mpvnova/shield-mpeg2-fallback"

internal fun MPVView.applyMpeg2SoftwareFallbackSetting(enabled: Boolean) {
    applyShieldMpeg2CodecGuard(enabled)
    mpvSetPropertyString(SHIELD_MPEG2_FALLBACK_PROPERTY, if (enabled) "yes" else "no")
}

private fun MPVView.applyShieldMpeg2CodecGuard(enabled: Boolean) {
    val codecs = shieldMpeg2HwdecCodecs(
        enabled = enabled,
        configuredCodecs = configuredHwdecCodecs,
    )
    setRuntimeOption("hwdec-codecs", codecs)
}

internal fun shieldMpeg2HwdecCodecs(
    enabled: Boolean,
    configuredCodecs: String,
): String {
    return if (!enabled) {
        configuredCodecs
    } else {
        val configured = configuredCodecs.split(',').map(String::trim).filter(String::isNotEmpty)
        if (configured.any { it.equals("all", ignoreCase = true) }) {
            MPV_VIEW_HWDEC_CODECS_WITHOUT_MPEG2
        } else {
            configured
                .filterNot { it.equals("mpeg2video", ignoreCase = true) }
                .joinToString(",")
                .ifEmpty { "none" }
        }
    }
}
