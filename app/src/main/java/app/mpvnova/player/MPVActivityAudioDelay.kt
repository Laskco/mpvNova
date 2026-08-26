package app.mpvnova.player

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import kotlin.math.roundToLong

internal fun MPVActivity.showAudioDelayPicker(
    restoreState: StateRestoreCallback,
    onDismiss: () -> Unit = {},
) {
    val impl = audioDelayDialog ?: AudioDelayDialog().also {
        audioDelayDialog = it
    }
    lateinit var dialog: AlertDialog
    fun refresh() {
        impl.update(
            currentDelayMs = currentAudioDelayMs(),
            rememberedDelayMs = savedAudioDelayMs,
            bluetoothDelayMs = bluetoothAudioDelayMs,
            bluetoothOutputActive = isBluetoothAudioOutputActive(),
        )
    }
    impl.onAdjust = { deltaMs ->
        setAudioDelayMs((currentAudioDelayMs() + deltaMs).coerceIn(
            secondsToMillis(AUDIO_DELAY_MIN_SEC),
            secondsToMillis(AUDIO_DELAY_MAX_SEC),
        ))
        refresh()
    }
    impl.onReset = {
        setAudioDelayMs(0L)
        appliedBluetoothAudioDelayMs = null
        refresh()
    }
    impl.onRemember = {
        saveAudioDelayDefault(currentAudioDelayMs())
        refresh()
    }
    impl.onClearRemembered = {
        clearAudioDelayDefault()
        refresh()
    }
    impl.onApplyBluetooth = {
        saveBluetoothAudioDelay(currentAudioDelayMs())
        refresh()
    }
    impl.onClearBluetooth = {
        clearBluetoothAudioDelay()
        refresh()
    }
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
            widthFraction = AUDIO_DELAY_DIALOG_WIDTH_FRACTION,
            maxWidthDp = AUDIO_DELAY_DIALOG_MAX_WIDTH_DP,
        )
    )
}

internal fun MPVActivity.registerBluetoothAudioDelayWatcher() {
    if (bluetoothAudioDelayRouteCallback != null)
        return
    val manager = audioManager ?: return
    val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            eventUiHandler.post { applyBluetoothAudioDelayForRoute() }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            eventUiHandler.post { applyBluetoothAudioDelayForRoute() }
        }
    }
    manager.registerAudioDeviceCallback(callback, eventUiHandler)
    bluetoothAudioDelayRouteCallback = callback
    applyBluetoothAudioDelayForRoute()
}

internal fun MPVActivity.unregisterBluetoothAudioDelayWatcher() {
    val manager = audioManager ?: return
    val callback = bluetoothAudioDelayRouteCallback ?: return
    manager.unregisterAudioDeviceCallback(callback)
    bluetoothAudioDelayRouteCallback = null
}

internal fun MPVActivity.applyBluetoothAudioDelayForRoute() {
    val savedDelayMs = bluetoothAudioDelayMs
    if (savedDelayMs == 0L) {
        appliedBluetoothAudioDelayMs = null
        return
    }
    val currentDelayMs = currentAudioDelayMs()
    val appliedDelayMs = appliedBluetoothAudioDelayMs
    if (isBluetoothAudioOutputActive()) {
        if (currentDelayMs == savedAudioDelayMs || currentDelayMs == appliedDelayMs) {
            setAudioDelayMs(savedDelayMs)
            appliedBluetoothAudioDelayMs = savedDelayMs
        }
    } else if (appliedDelayMs != null && currentDelayMs == appliedDelayMs) {
        setAudioDelayMs(savedAudioDelayMs)
        appliedBluetoothAudioDelayMs = null
    }
}

internal fun MPVActivity.saveBluetoothAudioDelay(delayMs: Long) {
    bluetoothAudioDelayMs = delayMs
    appliedBluetoothAudioDelayMs = if (isBluetoothAudioOutputActive()) delayMs else null
    getDefaultSharedPreferences(applicationContext)
        .edit()
        .putLong(PREF_BLUETOOTH_AUDIO_DELAY_MS, delayMs)
        .apply()
    showToast(
        getString(R.string.audio_delay_apply_bt),
        getString(R.string.audio_delay_bt_saved_toast, formatAudioDelayMs(delayMs)),
    )
}

internal fun MPVActivity.clearBluetoothAudioDelay() {
    val appliedDelayMs = appliedBluetoothAudioDelayMs
    if (appliedDelayMs != null && currentAudioDelayMs() == appliedDelayMs)
        setAudioDelayMs(0L)
    bluetoothAudioDelayMs = 0L
    appliedBluetoothAudioDelayMs = null
    getDefaultSharedPreferences(applicationContext)
        .edit()
        .remove(PREF_BLUETOOTH_AUDIO_DELAY_MS)
        .apply()
    showToast(getString(R.string.audio_delay_clear_bt), getString(R.string.audio_delay_bt_cleared))
}

internal fun MPVActivity.currentAudioDelayMs(): Long {
    return secondsToMillis(player.audioDelay ?: 0.0)
}

private fun MPVActivity.setAudioDelayMs(delayMs: Long) {
    player.audioDelay = delayMs / MPV_MILLIS_PER_SECOND_DOUBLE
}

private fun secondsToMillis(seconds: Double): Long {
    return (seconds * MPV_MILLIS_PER_SECOND_DOUBLE).roundToLong()
}

@Suppress("DEPRECATION")
internal fun MPVActivity.isBluetoothAudioOutputActive(): Boolean {
    val manager = audioManager ?: return false
    return manager.isBluetoothA2dpOn ||
        manager.isBluetoothScoOn ||
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.isBluetoothOutput() }
}

private fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
        else -> false
    }
}
