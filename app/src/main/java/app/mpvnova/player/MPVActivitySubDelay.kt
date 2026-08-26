package app.mpvnova.player

import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToLong

internal fun MPVActivity.showSubDelayPicker(
    restoreState: StateRestoreCallback,
    onDismiss: () -> Unit = {},
) {
    val impl = subDelayDialog ?: SubDelayPanelDialog().also {
        subDelayDialog = it
    }
    val syncState = SubDelaySyncState()
    lateinit var dialog: AlertDialog

    fun refresh() {
        impl.update(
            primaryDelayMs = currentPrimarySubDelayMs(),
            secondaryDelayMs = currentSecondarySubDelayMs(),
            rememberedPrimaryDelayMs = savedSubDelayMs,
            rememberedSecondaryDelayMs = savedSecondarySubDelayMs,
            voiceHeardMarked = syncState.voiceHeardAtMs != null,
            textSeenMarked = syncState.textSeenAtMs != null,
        )
    }

    bindSubDelayAdjustmentActions(impl, ::refresh)
    bindSubDelayPersistenceActions(impl, syncState, ::refresh)
    bindSubDelaySyncActions(impl, syncState, ::refresh)
    impl.onClose = { dialog.dismiss() }

    @Suppress("DEPRECATION")
    dialog = with(AlertDialog.Builder(this)) {
        setView(impl.buildView(layoutInflater))
        setOnDismissListener {
            restoreState()
            onDismiss()
            reopenDrawerIfNoParentDialog(dialog)
        }
        create()
    }
    refresh()
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = ADVANCED_SUB_DELAY_DIALOG_WIDTH_FRACTION,
            maxWidthDp = ADVANCED_SUB_DELAY_DIALOG_MAX_WIDTH_DP,
        )
    )
}

private class SubDelaySyncState(
    var voiceHeardAtMs: Long? = null,
    var textSeenAtMs: Long? = null,
)

private fun MPVActivity.bindSubDelayAdjustmentActions(
    impl: SubDelayPanelDialog,
    refresh: () -> Unit,
) {
    impl.onPrimaryAdjust = { deltaMs ->
        setPrimarySubDelayMs((currentPrimarySubDelayMs() + deltaMs).coerceIn(
            secondsToDelayMillis(SUB_DELAY_MIN_SEC),
            secondsToDelayMillis(SUB_DELAY_MAX_SEC),
        ))
        refresh()
    }
    impl.onSecondaryAdjust = secondaryAdjust@ { deltaMs ->
        val secondaryDelayMs = currentSecondarySubDelayMs() ?: return@secondaryAdjust
        setSecondarySubDelayMs((secondaryDelayMs + deltaMs).coerceIn(
            secondsToDelayMillis(SUB_DELAY_MIN_SEC),
            secondsToDelayMillis(SUB_DELAY_MAX_SEC),
        ))
        refresh()
    }
}

private fun MPVActivity.bindSubDelayPersistenceActions(
    impl: SubDelayPanelDialog,
    syncState: SubDelaySyncState,
    refresh: () -> Unit,
) {
    impl.onReset = {
        setPrimarySubDelayMs(0L)
        if (player.secondarySid != -1) setSecondarySubDelayMs(0L)
        syncState.voiceHeardAtMs = null
        syncState.textSeenAtMs = null
        refresh()
    }
    impl.onRemember = {
        saveSubDelayDefaults(currentPrimarySubDelayMs(), currentSecondarySubDelayMs())
        refresh()
    }
    impl.onClearRemembered = {
        clearSubDelayDefaults()
        refresh()
    }
}

private fun MPVActivity.bindSubDelaySyncActions(
    impl: SubDelayPanelDialog,
    syncState: SubDelaySyncState,
    refresh: () -> Unit,
) {
    impl.onVoiceHeard = {
        syncState.voiceHeardAtMs = SystemClock.uptimeMillis()
        syncState.applyIfReady { voiceMs, textMs -> applySubDelaySync(voiceMs, textMs) }
        refresh()
    }
    impl.onTextSeen = {
        syncState.textSeenAtMs = SystemClock.uptimeMillis()
        syncState.applyIfReady { voiceMs, textMs -> applySubDelaySync(voiceMs, textMs) }
        refresh()
    }
}

private fun SubDelaySyncState.applyIfReady(applySync: (Long, Long) -> Boolean) {
    val voiceMs = voiceHeardAtMs
    val textMs = textSeenAtMs
    if (voiceMs != null && textMs != null && applySync(voiceMs, textMs)) {
        voiceHeardAtMs = null
        textSeenAtMs = null
    }
}

private fun MPVActivity.applySubDelaySync(
    voiceHeardAtMs: Long?,
    textSeenAtMs: Long?,
): Boolean {
    if (voiceHeardAtMs == null || textSeenAtMs == null)
        return false
    val adjustedDelayMs = currentPrimarySubDelayMs() + voiceHeardAtMs - textSeenAtMs
    setPrimarySubDelayMs(adjustedDelayMs.coerceIn(
        secondsToDelayMillis(SUB_DELAY_MIN_SEC),
        secondsToDelayMillis(SUB_DELAY_MAX_SEC),
    ))
    return true
}

private fun MPVActivity.currentPrimarySubDelayMs(): Long {
    return secondsToDelayMillis(player.subDelay ?: 0.0)
}

private fun MPVActivity.currentSecondarySubDelayMs(): Long? {
    return if (player.secondarySid != -1) {
        secondsToDelayMillis(player.secondarySubDelay ?: 0.0)
    } else {
        null
    }
}

private fun MPVActivity.setPrimarySubDelayMs(delayMs: Long) {
    player.subDelay = delayMs / MPV_MILLIS_PER_SECOND_DOUBLE
}

private fun MPVActivity.setSecondarySubDelayMs(delayMs: Long) {
    player.secondarySubDelay = delayMs / MPV_MILLIS_PER_SECOND_DOUBLE
}

private fun secondsToDelayMillis(seconds: Double): Long {
    return (seconds * MPV_MILLIS_PER_SECOND_DOUBLE).roundToLong()
}
