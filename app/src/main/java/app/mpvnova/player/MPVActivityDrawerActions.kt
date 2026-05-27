package app.mpvnova.player

import android.content.SharedPreferences
import android.widget.TextView
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import app.mpvnova.player.databinding.DialogPlayerDrawerBinding
import app.mpvnova.player.databinding.DrawerPrefRowBinding

// Dim the inactive "Off" chip so the eye can find armed toggles quickly.
private const val PREF_ROW_OFF_ALPHA = 0.55f

/** Wires every action button inside the player drawer. */
internal fun MPVActivity.bindDrawerActionButtons(
    binding: DialogPlayerDrawerBinding,
    dismiss: () -> Unit,
) {
    bindDrawerVideoActions(binding, dismiss)
    bindDrawerAudioAndSubtitleActions(binding, dismiss)
    bindDrawerPlaybackActions(binding, dismiss)
}

/** Flag the drawer to reopen after the next sub-dialog dismisses. */
private fun MPVActivity.dismissDrawerExpectingReopen(dismiss: () -> Unit) {
    drawerReopenPending = true
    dismiss()
}

private fun MPVActivity.bindDrawerVideoActions(
    binding: DialogPlayerDrawerBinding,
    dismiss: () -> Unit,
) {
    binding.drawerDecoderBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); pickDecoder()
    }
    binding.drawerAspectBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        openAspectMenu { /* aspect dialog owns its restore via pauseForDialog */ }
    }
    binding.drawerContrastBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        openVideoAdjustmentPicker(VIDEO_CONTRAST_ADJUSTMENT, pauseForDialog())
    }
    binding.drawerBrightnessBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); openPlayerBrightnessPicker(pauseForDialog())
    }
    binding.drawerGammaBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        openVideoAdjustmentPicker(VIDEO_GAMMA_ADJUSTMENT, pauseForDialog())
    }
    binding.drawerSaturationBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        openVideoAdjustmentPicker(VIDEO_SATURATION_ADJUSTMENT, pauseForDialog())
    }
    binding.drawerScreenshotBtn.setOnClickListener {
        // Fire-and-forget — no sub-dialog to bounce through.
        dismiss(); takeScreenshot()
    }
}

private fun MPVActivity.bindDrawerAudioAndSubtitleActions(
    binding: DialogPlayerDrawerBinding,
    dismiss: () -> Unit,
) {
    // pickAudio/pickSub open the full track + filter dialog.
    binding.drawerAudioTrackBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); pickAudio()
    }
    binding.drawerOpenAudioBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        eventUiHandler.post {
            // File picker is a separate Activity, not a dialog — reopen
            // the drawer from the activity-result callback.
            openFilePickerFor(R.string.open_external_audio) { result, data ->
                addExternalThing("audio-add", result, data)
                reopenDrawerIfPending()
            }
        }
    }
    binding.drawerAudioDelayBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        val picker = DecimalPickerDialog(AUDIO_DELAY_MIN_SEC, AUDIO_DELAY_MAX_SEC)
        genericPickerDialog(picker, R.string.audio_delay, "audio-delay", pauseForDialog())
    }

    binding.drawerSubTrackBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); pickSub()
    }
    binding.drawerOpenSubBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        eventUiHandler.post {
            openFilePickerFor(R.string.open_external_sub) { result, data ->
                addExternalThing("sub-add", result, data)
                reopenDrawerIfPending()
            }
        }
    }
    binding.drawerSubDelayBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss)
        showSubDelayPicker(pauseForDialog(), PlayerDialogLayout(
            widthFraction = ADVANCED_SUB_DELAY_DIALOG_WIDTH_FRACTION,
            maxWidthDp = ADVANCED_SUB_DELAY_DIALOG_MAX_WIDTH_DP,
            heightFraction = ADVANCED_SUB_DELAY_DIALOG_HEIGHT_FRACTION,
            maxHeightDp = ADVANCED_SUB_DELAY_DIALOG_MAX_HEIGHT_DP,
        ))
    }
    binding.drawerSubSeekPrev.setOnClickListener { mpvCommand(arrayOf("sub-seek", "-1")) }
    binding.drawerSubSeekNext.setOnClickListener { mpvCommand(arrayOf("sub-seek", "1")) }
}

private fun MPVActivity.bindDrawerPlaybackActions(
    binding: DialogPlayerDrawerBinding,
    dismiss: () -> Unit,
) {
    binding.drawerBackgroundBtn.setOnClickListener {
        backgroundPlayMode = "always"
        player.paused = false
        moveTaskToBack(true)
        dismiss()
    }
    binding.drawerPlaylistBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); openPlaylistMenu(pauseForDialog())
    }
    binding.drawerPlaybackSpeedBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); pickSpeed()
    }
    binding.drawerChapterBtn.setOnClickListener {
        dismissDrawerExpectingReopen(dismiss); showChapterPickerDialog()
    }
    binding.drawerChapterPrev.setOnClickListener { seekChapterRelative(-1); dismiss() }
    binding.drawerChapterNext.setOnClickListener { seekChapterRelative(1); dismiss() }
    binding.drawerStatsBtn.setOnClickListener {
        mpvCommand(arrayOf("script-binding", "stats/display-stats-toggle"))
    }
    binding.drawerStatsBtn1.setOnClickListener {
        mpvCommand(arrayOf("script-binding", "stats/display-page-1"))
    }
    binding.drawerStatsBtn2.setOnClickListener {
        mpvCommand(arrayOf("script-binding", "stats/display-page-2"))
    }
    binding.drawerStatsBtn3.setOnClickListener {
        mpvCommand(arrayOf("script-binding", "stats/display-page-3"))
    }
}

internal fun MPVActivity.bindDrawerPrefRows(binding: DialogPlayerDrawerBinding) {
    val prefs = getDefaultSharedPreferences(applicationContext)
    bindDrawerAutopausePrefRows(binding, prefs)
    bindDrawerInterfacePrefRows(binding, prefs)
}

private fun MPVActivity.bindDrawerAutopausePrefRows(
    binding: DialogPlayerDrawerBinding,
    prefs: SharedPreferences,
) {
    bindBooleanPrefRow(
        row = binding.drawerPrefAutopauseControls,
        titleRes = R.string.pref_autopause_controls_overlay_title,
        summaryRes = R.string.pref_autopause_controls_overlay_summary,
        prefs = prefs,
        key = "autopause_controls_overlay",
        defaultValue = false,
    ) { newValue ->
        autoPauseControlsOverlayEnabled = newValue
        // Don't leave a stale auto-paused flag from a prior overlay-open.
        if (!newValue) controlsOverlayAutoPaused = false
    }
}

@Suppress("LongMethod")
private fun MPVActivity.bindDrawerInterfacePrefRows(
    binding: DialogPlayerDrawerBinding,
    prefs: SharedPreferences,
) {
    bindBooleanPrefRow(
        row = binding.drawerPrefAutopauseShield,
        titleRes = R.string.pref_autopause_shield_hi10p_title,
        summaryRes = R.string.pref_autopause_shield_hi10p_summary,
        prefs = prefs,
        key = "autopause_shield_hi10p",
        defaultValue = true,
    ) { newValue ->
        autoPauseShieldHi10pEnabled = newValue
        if (!newValue) controlsOverlayAutoPaused = false
    }

    bindBooleanPrefRow(
        row = binding.drawerPrefKeepControlsVisible,
        titleRes = R.string.pref_keep_controls_visible_paused_title,
        summaryRes = R.string.pref_keep_controls_visible_paused_summary,
        prefs = prefs,
        key = "keep_controls_visible_paused",
        defaultValue = false,
    ) { newValue -> keepControlsVisibleWhilePaused = newValue }

    bindBooleanPrefRow(
        row = binding.drawerPrefShowMediaTitle,
        titleRes = R.string.pref_display_media_title_title,
        summaryRes = R.string.pref_display_media_title_summary,
        prefs = prefs,
        key = "display_media_title",
        defaultValue = true,
    ) { newValue ->
        showMediaTitle = newValue
        refreshUi()
    }

    bindBooleanPrefRow(
        row = binding.drawerPrefShowClock,
        titleRes = R.string.pref_display_clock_overlay_title,
        summaryRes = R.string.pref_display_clock_overlay_summary,
        prefs = prefs,
        key = "display_clock_overlay",
        defaultValue = true,
    ) { newValue ->
        showClockOverlay = newValue
        refreshUi()
    }

    bindBooleanPrefRow(
        row = binding.drawerPrefBottomControls,
        titleRes = R.string.pref_bottom_controls_title,
        summaryRes = R.string.pref_bottom_controls_summary,
        prefs = prefs,
        key = "bottom_controls",
        defaultValue = true,
    ) { newValue ->
        controlsAtBottom = newValue
        // Apply the margin change immediately without waiting for rotation.
        onConfigurationChanged(resources.configuration)
    }

    bindBooleanPrefRow(
        row = binding.drawerPrefExitDoubleBack,
        titleRes = R.string.pref_exit_with_double_back_title,
        summaryRes = R.string.pref_exit_with_double_back_summary,
        prefs = prefs,
        key = "exit_with_double_back",
        defaultValue = false,
    ) { newValue ->
        exitWithDoubleBack = newValue
        // Reset the double-back window when the toggle flips so a stray
        // press right before turning it on doesn't half-arm the exit.
        lastBackPressMs = 0L
    }

    bindBooleanPrefRow(
        row = binding.drawerPrefDpadUpJumpsTop,
        titleRes = R.string.pref_dpad_up_jumps_top_title,
        summaryRes = R.string.pref_dpad_up_jumps_top_summary,
        prefs = prefs,
        key = "dpad_up_jumps_to_top_controls",
        defaultValue = false,
    ) { newValue -> dpadUpJumpsToTopControls = newValue }

    bindBooleanPrefRow(
        row = binding.drawerPrefAutoRefreshRate,
        titleRes = R.string.pref_auto_refresh_rate_title,
        summaryRes = R.string.pref_auto_refresh_rate_summary,
        prefs = prefs,
        key = "auto_refresh_rate_switch",
        defaultValue = false,
    ) { newValue ->
        autoRefreshRateSwitch = newValue
        if (newValue) {
            // Apply immediately when the user flips this on mid-playback —
            // otherwise the next FILE_LOADED event is the only trigger
            // and they'd have to restart the file to see anything happen.
            maybeApplyContentRefreshRate()
        } else {
            // Toggling off should revert the display to its default mode.
            // Leaving preferredDisplayModeId set means the panel would
            // stay parked on whatever rate matched the last file (e.g.
            // 23.976Hz) until activity recreate.
            clearContentRefreshRate()
        }
    }

    bindBooleanPrefRow(
        row = binding.drawerPrefDecoderAutoFallback,
        titleRes = R.string.pref_decoder_auto_fallback_title,
        summaryRes = R.string.pref_decoder_auto_fallback_summary,
        prefs = prefs,
        key = "decoder_auto_fallback",
        defaultValue = true,
    ) { newValue -> autoDecoderFallback = newValue }

    bindBooleanPrefRow(
        row = binding.drawerPrefSavePosition,
        titleRes = R.string.pref_save_position_title,
        summaryRes = R.string.pref_save_position_summary,
        prefs = prefs,
        key = "save_position",
        defaultValue = true,
    ) { newValue -> shouldSavePosition = newValue }

    bindBooleanPrefRow(
        row = binding.drawerPrefPlaylistExitWarning,
        titleRes = R.string.pref_playlist_exit_warning_title,
        summaryRes = R.string.pref_playlist_exit_warning_summary,
        prefs = prefs,
        key = "playlist_exit_warning",
        defaultValue = true,
    ) { newValue -> playlistExitWarning = newValue }
}

private fun bindBooleanPrefRow(
    row: DrawerPrefRowBinding,
    titleRes: Int,
    summaryRes: Int,
    prefs: SharedPreferences,
    key: String,
    defaultValue: Boolean,
    onChange: (Boolean) -> Unit,
) {
    row.prefRowTitle.setText(titleRes)
    row.prefRowSummary.setText(summaryRes)
    var current = prefs.getBoolean(key, defaultValue)
    refreshPrefRowValue(row.prefRowValue, current)
    row.root.setOnClickListener {
        current = !current
        prefs.edit().putBoolean(key, current).apply()
        refreshPrefRowValue(row.prefRowValue, current)
        onChange(current)
    }
}

private fun refreshPrefRowValue(valueView: TextView, on: Boolean) {
    valueView.setText(if (on) R.string.status_on else R.string.status_off)
    valueView.alpha = if (on) 1f else PREF_ROW_OFF_ALPHA
}
