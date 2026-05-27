package app.mpvnova.player

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import java.util.Locale

internal fun MPVActivity.configureSubPickerCallbacks(
    impl: MediaPickerDialog,
    dismissDialog: () -> Unit
) {
    impl.onItemClick = { index -> handleSubPickerItemClick(impl, index, dismissDialog) }
    impl.onDelayClick = {
        dismissDialog()
        openSubDelayDialog()
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
    val delayValue = String.format(Locale.US, "%.2f s", player.subDelay ?: 0.0)
    return with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater, subPickerOptions(delayValue)))
        setOnDismissListener { restore(); reopenDrawerIfPending() }
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
    )
}
