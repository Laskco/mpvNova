package app.mpvnova.player

internal fun MPVActivity.applyNightModeDecoderDrcScale() {
    val decoderScale = if (
        nightModeLevel == NIGHT_MODE_DRC_PLUS_LEVEL &&
        currentAudioCodecName().isEac3CodecName()
    ) {
        drcPlusEac3DecoderScale
    } else {
        drcDecoderScaleOff
    }
    if (decoderScale == lastAppliedDecoderDrcScale) return
    setRuntimeOption("ad-lavc-ac3drc", decoderScale)
    lastAppliedDecoderDrcScale = decoderScale
}
