package app.mpvnova.player

@Suppress("CyclomaticComplexMethod")
internal fun MPVActivity.drawerOptionValue(option: PlayerDrawerOption): String = when (option) {
    PlayerDrawerOption.VIDEO_EDGE_CLEANUP -> videoEdgeCleanupLabel()
    PlayerDrawerOption.PREFERRED_DECODER -> {
        val mode = normalizedPreferredDecoderMode(preferredDecoderMode)
        decoderModeCompactLabel(mode)
    }
    PlayerDrawerOption.SHIELD_FALLBACK -> shieldFallbackOption(shieldDecoderFallback).compactLabel
    PlayerDrawerOption.FILTER_PRESETS -> videoFilterPresetLabel()
    PlayerDrawerOption.UPSCALING_FILTER -> videoScalerDrawerValue(VideoScalerSetting.UPSCALING)
    PlayerDrawerOption.DOWNSCALING_FILTER -> videoScalerDrawerValue(VideoScalerSetting.DOWNSCALING)
    PlayerDrawerOption.DEBANDING -> videoDebandingDrawerValue()
    PlayerDrawerOption.INTERPOLATION -> videoInterpolationDrawerValue()
    PlayerDrawerOption.TEMPORAL_FILTER -> videoScalerDrawerValue(VideoScalerSetting.TEMPORAL)
    PlayerDrawerOption.SHADERS -> {
        val shaders = UserShaderManager.shaders(this)
        if (UserShaderManager.isEnabled(this)) {
            getString(R.string.shader_status, shaders.count { it.enabled }, shaders.size)
        } else {
            getString(R.string.shader_status_manager_off, shaders.size)
        }
    }
    PlayerDrawerOption.SKIP_MODE -> skipSegmentsModeLabel(skipSegmentsMode)
    PlayerDrawerOption.SKIP_BUTTON_DISPLAY -> skipButtonDisplayModeCompactLabel(
        skipButtonDisplayMode
    )
    PlayerDrawerOption.SEEK_STEP -> seekStepLabel(seekStepMs)
    PlayerDrawerOption.SCREENSAVER -> screensaverDrawerSummary()
    PlayerDrawerOption.UI_FONT -> UiFont.currentLabel(this)
    PlayerDrawerOption.APPEARANCE_COLORS -> appearanceColorChoices
        .firstOrNull { it.value == AppearanceTheme.currentValue(this) }
        ?.let { getString(it.labelRes) }
        ?: getString(appearanceColorChoices.first().labelRes)
    PlayerDrawerOption.TITLE_STYLE -> getString(R.string.player_title_style_drawer_value)
    PlayerDrawerOption.PLAYER_UI_STYLE -> getString(R.string.player_ui_customization_drawer_value)
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
