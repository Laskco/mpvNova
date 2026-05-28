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
        PlayerDrawerActionGroup.AUDIO_SUBTITLE -> handleAudioSubtitleDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.PLAYBACK -> handlePlaybackDrawerAction(action, dismiss)
        PlayerDrawerActionGroup.STATS -> handleStatsDrawerAction(action)
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
            val picker = DecimalPickerDialog(AUDIO_DELAY_MIN_SEC, AUDIO_DELAY_MAX_SEC)
            genericPickerDialog(picker, R.string.audio_delay, "audio-delay", pauseForDialog())
        }
        PlayerDrawerAction.SUB_TRACK -> {
            dismissDrawerExpectingReopen(dismiss); pickSub()
        }
        PlayerDrawerAction.OPEN_SUB -> {
            openExternalSubFromDrawer(dismiss)
        }
        PlayerDrawerAction.SUB_DELAY -> {
            dismissDrawerExpectingReopen(dismiss)
            showSubDelayPicker(pauseForDialog(), PlayerDialogLayout(
                widthFraction = ADVANCED_SUB_DELAY_DIALOG_WIDTH_FRACTION,
                maxWidthDp = ADVANCED_SUB_DELAY_DIALOG_MAX_WIDTH_DP,
                heightFraction = ADVANCED_SUB_DELAY_DIALOG_HEIGHT_FRACTION,
                maxHeightDp = ADVANCED_SUB_DELAY_DIALOG_MAX_HEIGHT_DP,
            ))
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
        else -> Unit
    }
}

private fun MPVActivity.handleStatsDrawerAction(
    action: PlayerDrawerAction,
) {
    when (action) {
        PlayerDrawerAction.STATS_TOGGLE -> {
            mpvCommand(arrayOf("script-binding", "stats/display-stats-toggle"))
        }
        PlayerDrawerAction.STATS_PAGE_1 -> {
            mpvCommand(arrayOf("script-binding", "stats/display-page-1"))
        }
        PlayerDrawerAction.STATS_PAGE_2 -> {
            mpvCommand(arrayOf("script-binding", "stats/display-page-2"))
        }
        PlayerDrawerAction.STATS_PAGE_3 -> {
            mpvCommand(arrayOf("script-binding", "stats/display-page-3"))
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
