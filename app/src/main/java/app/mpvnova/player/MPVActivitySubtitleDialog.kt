package app.mpvnova.player

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToLong

internal fun MPVActivity.configureSubPickerCallbacks(
    impl: MediaPickerDialog,
    dismissDialog: () -> Unit
) {
    impl.onItemClick = { index -> handleSubPickerItemClick(impl, index, dismissDialog) }
    impl.onSubPresetAdjust = { delta ->
        val presetName = cycleSubtitleStylePreset(delta)
        MediaPickerDialog.SubPresetState(
            presetName = presetName,
            subStyleStateText = subtitleStyleToggleText(),
        )
    }
    impl.onSubScaleAdjust = { delta -> adjustSubScale(delta) }
    impl.onSubPosAdjust = { delta -> adjustSubPos(delta) }
    impl.onSecondaryPosAdjust = { delta -> adjustSecondaryPos(delta) }
    impl.onSecondarySubAdjust = { delta -> handleSecondarySubAdjust(impl, delta) }
    impl.onSecondarySubSwap = {
        swapPrimaryAndSecondarySub()
        impl.updateItems(buildSubItems())
    }
    impl.onSubFilterStatesRefresh = { currentSubFilterStates() }
    impl.onPersistSubClick = { togglePersistSubFilters() }
}

private fun MPVActivity.handleSubPickerItemClick(
    impl: MediaPickerDialog,
    index: Int,
    dismissDialog: () -> Unit
) {
    val trackId = impl.items[index].tag as Int
    player.sid = trackId
    saveUserTrackPick("sub", trackId)
    dismissDialog()
    trackSwitchNotification { TrackData(trackId, SubTrackDialog.TRACK_TYPE) }
}

private fun MPVActivity.handleSecondarySubAdjust(
    impl: MediaPickerDialog,
    delta: Int
): MediaPickerDialog.ValueState {
    val state = adjustSecondarySub(delta)
    impl.updateItems(buildSubItems())
    return state
}

private fun MPVActivity.togglePersistSubFilters() {
    persistSubFilters = !persistSubFilters
    if (persistSubFilters) {
        saveUserTrackPick("sub", player.sid)
    }
    writeSettings()
    showToast(
        getString(R.string.pref_persist_sub_filters_title),
        getString(if (persistSubFilters) R.string.status_on else R.string.status_off)
    )
}

internal fun MPVActivity.createSubPickerDialog(
    impl: MediaPickerDialog,
    restore: StateRestoreCallback
): AlertDialog {
    val delayValue = currentSubtitleDelayText()
    return with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater, subPickerOptions(delayValue)))
        setOnDismissListener {
            restore()
            if (!trackPanelChildTransition) reopenDrawerIfPending()
        }
        create()
    }
}

private fun MPVActivity.subPickerOptions(delayValue: String): MediaPickerDialog.Options {
    return MediaPickerDialog.Options(
        title = getString(R.string.dialog_title_subs),
        items = buildSubItems(),
        showDelay = true,
        delayText = delayValue,
        showSubFilters = true,
        initialSubScaleState = currentSubScaleState(),
        initialSubPosState = currentSubPosState(),
        initialSecondaryPosState = currentSecondaryPosState(),
        initialSecondarySubState = currentSecondarySubState(),
        persistSubFiltersOn = persistSubFilters,
        subStyleStateText = subtitleStyleToggleText(),
        showSubPresetCycler = hasSubtitleStylePresets(),
        initialSubPresetName = currentSubtitleStylePresetName(),
    )
}

internal fun MPVActivity.currentSubtitleDelayText(): String {
    val delayMs = ((player.subDelay ?: 0.0) * MPV_MILLIS_PER_SECOND_DOUBLE).roundToLong()
    return formatAudioDelayMs(delayMs)
}

private fun MPVActivity.subtitleStyleToggleText(): String = getString(
    if (customSubStyleEnabled) R.string.status_on else R.string.status_off
)
