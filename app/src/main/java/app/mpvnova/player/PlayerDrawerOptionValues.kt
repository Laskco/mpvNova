package app.mpvnova.player

internal fun MPVActivity.drawerOptionValue(option: PlayerDrawerOption): String = when (option) {
    PlayerDrawerOption.PREFERRED_DECODER -> {
        val mode = normalizedPreferredDecoderMode(preferredDecoderMode, shieldDecoderModeEnabled)
        decoderModeCompactLabel(mode)
    }
    PlayerDrawerOption.SHIELD_FALLBACK -> shieldFallbackOption(shieldDecoderFallback).compactLabel
    PlayerDrawerOption.UPSCALING_FILTER -> videoScalerDrawerValue(VideoScalerSetting.UPSCALING)
    PlayerDrawerOption.DOWNSCALING_FILTER -> videoScalerDrawerValue(VideoScalerSetting.DOWNSCALING)
    PlayerDrawerOption.DEBANDING -> videoDebandingDrawerValue()
    PlayerDrawerOption.INTERPOLATION -> videoInterpolationDrawerValue()
    PlayerDrawerOption.TEMPORAL_FILTER -> videoScalerDrawerValue(VideoScalerSetting.TEMPORAL)
    PlayerDrawerOption.SKIP_MODE -> skipSegmentsModeLabel(skipSegmentsMode)
    PlayerDrawerOption.SKIP_BUTTON_DISPLAY -> skipButtonDisplayModeCompactLabel(
        skipButtonDisplayMode
    )
    PlayerDrawerOption.SEEK_STEP -> seekStepLabel(seekStepMs)
    PlayerDrawerOption.SCREENSAVER -> screensaverDrawerSummary()
    PlayerDrawerOption.UI_FONT -> UiFont.currentLabel(this)
}

private fun MPVActivity.skipButtonDisplayModeCompactLabel(
    mode: SkipButtonDisplayMode
): String = when (mode) {
    SkipButtonDisplayMode.SEGMENT ->
        getString(R.string.skip_button_display_segment_compact)
    SkipButtonDisplayMode.TEN_SECONDS ->
        getString(R.string.seek_step_seconds_value, SKIP_BUTTON_DISPLAY_TEN_SECONDS)
    SkipButtonDisplayMode.THIRTY_SECONDS ->
        getString(R.string.seek_step_seconds_value, SKIP_BUTTON_DISPLAY_THIRTY_SECONDS)
}
