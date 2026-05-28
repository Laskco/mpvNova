package app.mpvnova.player

internal fun MPVActivity.handleDrawerPreferenceChange(
    preference: PlayerDrawerPreference,
    newValue: Boolean,
) {
    when (preference.group) {
        PlayerDrawerPreferenceGroup.AUTOPAUSE -> handleDrawerAutopausePreference(preference, newValue)
        PlayerDrawerPreferenceGroup.INTERFACE -> handleDrawerInterfacePreference(preference, newValue)
        PlayerDrawerPreferenceGroup.VIDEO -> handleDrawerVideoPreference(preference, newValue)
        PlayerDrawerPreferenceGroup.PLAYBACK -> handleDrawerPlaybackPreference(preference, newValue)
    }
}

private fun MPVActivity.handleDrawerAutopausePreference(
    preference: PlayerDrawerPreference,
    newValue: Boolean,
) {
    when (preference) {
        PlayerDrawerPreference.AUTOPAUSE_CONTROLS -> {
            autoPauseControlsOverlayEnabled = newValue
            if (!newValue) controlsOverlayAutoPaused = false
        }
        PlayerDrawerPreference.AUTOPAUSE_SHIELD -> {
            autoPauseShieldHi10pEnabled = newValue
            if (!newValue) controlsOverlayAutoPaused = false
        }
        else -> Unit
    }
}

private fun MPVActivity.handleDrawerInterfacePreference(
    preference: PlayerDrawerPreference,
    newValue: Boolean,
) {
    when (preference) {
        PlayerDrawerPreference.KEEP_CONTROLS_VISIBLE -> keepControlsVisibleWhilePaused = newValue
        PlayerDrawerPreference.SHOW_MEDIA_TITLE -> {
            showMediaTitle = newValue
            refreshUi()
        }
        PlayerDrawerPreference.SHOW_CLOCK -> {
            showClockOverlay = newValue
            refreshUi()
        }
        PlayerDrawerPreference.BOTTOM_CONTROLS -> {
            controlsAtBottom = newValue
            onConfigurationChanged(resources.configuration)
        }
        PlayerDrawerPreference.EXIT_DOUBLE_BACK -> {
            exitWithDoubleBack = newValue
            lastBackPressMs = 0L
        }
        PlayerDrawerPreference.DPAD_UP_JUMPS_TOP -> dpadUpJumpsToTopControls = newValue
        else -> Unit
    }
}

private fun MPVActivity.handleDrawerVideoPreference(
    preference: PlayerDrawerPreference,
    newValue: Boolean,
) {
    when (preference) {
        PlayerDrawerPreference.AUTO_REFRESH_RATE -> {
            autoRefreshRateSwitch = newValue
            if (newValue) {
                maybeApplyContentRefreshRate()
            } else {
                clearContentRefreshRate()
            }
        }
        PlayerDrawerPreference.DECODER_AUTO_FALLBACK -> autoDecoderFallback = newValue
        else -> Unit
    }
}

private fun MPVActivity.handleDrawerPlaybackPreference(
    preference: PlayerDrawerPreference,
    newValue: Boolean,
) {
    when (preference) {
        PlayerDrawerPreference.SAVE_POSITION -> shouldSavePosition = newValue
        PlayerDrawerPreference.PLAYLIST_EXIT_WARNING -> playlistExitWarning = newValue
        else -> Unit
    }
}
