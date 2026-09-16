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
