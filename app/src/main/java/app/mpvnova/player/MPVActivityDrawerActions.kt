package app.mpvnova.player

/** Flag the drawer to reopen after the next sub-dialog dismisses. */
private fun MPVActivity.dismissDrawerExpectingReopen(dismiss: () -> Unit) {
    drawerReopenPending = true
    dismiss()
}

internal fun MPVActivity.handleDrawerAction(
    action: PlayerDrawerAction,
    dismiss: () -> Unit,
) {
    when (action.group) {
        PlayerDrawerActionGroup.VIDEO -> handleVideoDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.PROCESSING -> handleProcessingDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.AUDIO_SUBTITLE -> handleAudioSubtitleDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.PLAYBACK -> handlePlaybackDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.INTERFACE -> handleInterfaceDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.STATS -> handleStatsDrawerAction(action)
    }
}

private fun MPVActivity.handleProcessingDrawerAction(
    action: PlayerDrawerAction,
    dismiss: () -> Unit,
) {
    dismissDrawerExpectingReopen(dismiss)
    when (action) {
        PlayerDrawerAction.UPSCALING_FILTER -> pickVideoScaler(VideoScalerSetting.UPSCALING)
        PlayerDrawerAction.DOWNSCALING_FILTER -> pickVideoScaler(VideoScalerSetting.DOWNSCALING)
        PlayerDrawerAction.DEBANDING -> pickVideoDebanding()
        PlayerDrawerAction.INTERPOLATION -> pickVideoInterpolation()
        PlayerDrawerAction.TEMPORAL_FILTER -> pickVideoScaler(VideoScalerSetting.TEMPORAL)
        else -> Unit
    }
}

private fun MPVActivity.handleVideoDrawerAction(
    action: PlayerDrawerAction,
    dismiss: () -> Unit,
) {
    when (action) {
        PlayerDrawerAction.DECODER -> {
            dismissDrawerExpectingReopen(dismiss); pickDecoder()
        }
        PlayerDrawerAction.PREFERRED_DECODER -> {
            dismissDrawerExpectingReopen(dismiss); pickPreferredDecoderMode()
        }
        PlayerDrawerAction.SHIELD_FALLBACK -> {
            dismissDrawerExpectingReopen(dismiss); pickShieldDecoderFallback()
        }
        PlayerDrawerAction.ASPECT -> {
            dismissDrawerExpectingReopen(dismiss)
            openAspectMenu { /* aspect dialog owns its restore via pauseForDialog */ }
        }
        PlayerDrawerAction.CONTRAST -> {
            dismissDrawerExpectingReopen(dismiss)
            openVideoAdjustmentPicker(VIDEO_CONTRAST_ADJUSTMENT, pauseForDialog())
        }
        PlayerDrawerAction.BRIGHTNESS -> {
            dismissDrawerExpectingReopen(dismiss); openPlayerBrightnessPicker(pauseForDialog())
        }
        PlayerDrawerAction.GAMMA -> {
            dismissDrawerExpectingReopen(dismiss)
            openVideoAdjustmentPicker(VIDEO_GAMMA_ADJUSTMENT, pauseForDialog())
        }
        PlayerDrawerAction.SATURATION -> {
            dismissDrawerExpectingReopen(dismiss)
            openVideoAdjustmentPicker(VIDEO_SATURATION_ADJUSTMENT, pauseForDialog())
        }
        PlayerDrawerAction.SCREENSHOT -> {
            dismiss(); takeScreenshot()
        }
        else -> Unit
    }
}

private fun MPVActivity.handleAudioSubtitleDrawerAction(
    action: PlayerDrawerAction,
    dismiss: () -> Unit,
) {
    when (action) {
        PlayerDrawerAction.AUDIO_TRACK -> {
            dismissDrawerExpectingReopen(dismiss); pickAudio()
        }
        PlayerDrawerAction.OPEN_AUDIO -> {
            openExternalAudioFromDrawer(dismiss)
        }
        PlayerDrawerAction.AUDIO_DELAY -> {
            dismissDrawerExpectingReopen(dismiss)
            showAudioDelayPicker(pauseForDialog())
        }
        PlayerDrawerAction.SUB_TRACK -> {
            dismissDrawerExpectingReopen(dismiss); pickSub()
        }
        PlayerDrawerAction.OPEN_SUB -> {
            openExternalSubFromDrawer(dismiss)
        }
        PlayerDrawerAction.SUB_DELAY -> {
            dismissDrawerExpectingReopen(dismiss)
            showSubDelayPicker(pauseForDialog())
        }
        PlayerDrawerAction.SUB_SEEK_PREV -> mpvCommand(arrayOf("sub-seek", "-1"))
        PlayerDrawerAction.SUB_SEEK_NEXT -> mpvCommand(arrayOf("sub-seek", "1"))
        else -> Unit
    }
}

private fun MPVActivity.handlePlaybackDrawerAction(
    action: PlayerDrawerAction,
    dismiss: () -> Unit,
) {
    when (action) {
        PlayerDrawerAction.BACKGROUND_PLAY -> {
            backgroundPlayMode = "always"
            player.paused = false
            moveTaskToBack(true)
            dismiss()
        }
        PlayerDrawerAction.PLAYLIST -> {
            dismissDrawerExpectingReopen(dismiss); openPlaylistMenu(pauseForDialog())
        }
        PlayerDrawerAction.PLAYBACK_SPEED -> {
            dismissDrawerExpectingReopen(dismiss); pickSpeed()
        }
        PlayerDrawerAction.CHAPTER_PICKER -> {
            dismissDrawerExpectingReopen(dismiss); showChapterPickerDialog()
        }
        PlayerDrawerAction.CHAPTER_PREV -> {
            seekChapterRelative(-1); dismiss()
        }
        PlayerDrawerAction.CHAPTER_NEXT -> {
            seekChapterRelative(1); dismiss()
        }
        PlayerDrawerAction.SKIP_MODE -> {
            dismissDrawerExpectingReopen(dismiss); pickSkipMode()
        }
        PlayerDrawerAction.SKIP_BUTTON_DISPLAY -> {
            dismissDrawerExpectingReopen(dismiss); pickSkipButtonDisplay()
        }
        PlayerDrawerAction.SEEK_STEP -> {
            dismissDrawerExpectingReopen(dismiss); pickSeekStep()
        }
        else -> Unit
    }
}

private fun MPVActivity.handleInterfaceDrawerAction(
    action: PlayerDrawerAction,
    dismiss: () -> Unit,
) {
    dismissDrawerExpectingReopen(dismiss)
    when (action) {
        PlayerDrawerAction.UI_FONT -> pickUiFont()
        PlayerDrawerAction.TITLE_STYLE -> openPlayerTitleStylePanel()
        PlayerDrawerAction.SCREENSAVER -> openScreensaverPanel()
        else -> Unit
    }
}

private fun MPVActivity.handleStatsDrawerAction(
    action: PlayerDrawerAction,
) {
    when (action) {
        PlayerDrawerAction.STATS_TOGGLE -> {
            toggleStatsFromButton()
        }
        PlayerDrawerAction.STATS_PAGE_1 -> {
            toggleStatsPage(STATS_PAGE_FIRST)
        }
        PlayerDrawerAction.STATS_PAGE_2 -> {
            toggleStatsPage(STATS_PAGE_FIRST + 1)
        }
        PlayerDrawerAction.STATS_PAGE_3 -> {
            toggleStatsPage(STATS_PAGE_LAST)
        }
        else -> Unit
    }
}

private fun MPVActivity.openExternalAudioFromDrawer(dismiss: () -> Unit) {
    dismissDrawerExpectingReopen(dismiss)
    eventUiHandler.post {
        openFilePickerFor(R.string.open_external_audio) { result, data ->
            addExternalThing("audio-add", result, data)
            reopenDrawerIfPending()
        }
    }
}

private fun MPVActivity.openExternalSubFromDrawer(dismiss: () -> Unit) {
    dismissDrawerExpectingReopen(dismiss)
    eventUiHandler.post {
        openFilePickerFor(R.string.open_external_sub) { result, data ->
            addExternalThing("sub-add", result, data)
            reopenDrawerIfPending()
        }
    }
}
