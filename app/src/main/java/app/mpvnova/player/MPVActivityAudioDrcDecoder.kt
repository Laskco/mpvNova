package app.mpvnova.player

internal fun MPVActivity.applyNightModeDecoderDrcScale(preInit: Boolean = false) {
    val decoderScale = if (
        !preInit &&
        nightModeLevel == NIGHT_MODE_DRC_PLUS_LEVEL &&
        currentAudioCodecName().isEac3CodecName()
    ) {
        drcPlusEac3DecoderScale
    } else {
        drcDecoderScaleOff
    }
    if (decoderScale == lastAppliedDecoderDrcScale) return
    if (preInit) {
        mpvSetOptionString("ad-lavc-ac3drc", decoderScale)
    } else {
        setRuntimeOption("ad-lavc-ac3drc", decoderScale)
    }
    lastAppliedDecoderDrcScale = decoderScale
}
