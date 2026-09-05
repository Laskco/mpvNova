package app.mpvnova.player

import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log

/** Audio focus + dialog pause + controls-overlay autopause. */

/**
 * Request/abandon audio focus + noisy receiver based on playback state.
 * @warning Call from event thread, not UI thread.
 */
internal fun MPVActivity.handleAudioFocus() {
    if ((psc.pause && !psc.cachePause) || !isPlayingAudio) {
        setNoisyReceiverRegistered(false)
    } else {
        setNoisyReceiverRegistered(true)
        // Re-requests on every unpause — see discussion in #1066.
        if (requestAudioFocus()) {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, "request")
        } else {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "request")
        }
    }
}

/**
 * Synchronized: handleAudioFocus toggles this from the mpv event thread while
 * onDestroy unregisters from the UI thread; an unguarded flag can double-unregister
 * (IllegalArgumentException) or re-register after destroy.
 */
internal fun MPVActivity.setNoisyReceiverRegistered(register: Boolean) {
    synchronized(becomingNoisyReceiver) {
        if (register == becomingNoisyReceiverRegistered)
            return
        if (register)
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        else
            unregisterReceiver(becomingNoisyReceiver)
        becomingNoisyReceiverRegistered = register
    }
}

internal fun MPVActivity.requestAudioFocus(): Boolean {
    val manager = audioManager ?: return false
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        requestAudioFocusModern(manager)
    } else {
        requestAudioFocusLegacy(manager)
    }
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
}

@RequiresApi(Build.VERSION_CODES.O)
private fun MPVActivity.requestAudioFocusModern(manager: AudioManager): Int {
    val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // libmpv's ao_audiotrack may differ � here we always pretend to be music.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .build()
    val result = manager.requestAudioFocus(request)
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        audioFocusRequest = request
    return result
}

@Suppress("DEPRECATION")
private fun MPVActivity.requestAudioFocusLegacy(manager: AudioManager): Int {
    return manager.requestAudioFocus(
        audioFocusChangeListener,
        STREAM_TYPE,
        AudioManager.AUDIOFOCUS_GAIN
    )
}

internal fun MPVActivity.onAudioFocusChange(type: Int, source: String) {
    Log.v(MPV_ACTIVITY_TAG, "Audio focus changed: $type ($source)")
    if (ignoreAudioFocus || isFinishing)
        return
    when (type) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // Loss can occur on top of ducking — chain the old restore.
            val oldRestore = audioFocusRestore
            val wasPlayerPaused = player.paused ?: false
            player.paused = true
            audioFocusRestore = {
                oldRestore()
                if (!wasPlayerPaused) player.paused = false
            }
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            mpvCommand(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
            audioFocusRestore = {
                val inv = 1f / AUDIO_FOCUS_DUCKING
                mpvCommand(arrayOf("multiply", "volume", inv.toString()))
            }
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
            audioFocusRestore()
            audioFocusRestore = {}
        }
    }
}

// If a dialog held the file open past its end, mpv parks on the last frame and
// won't budge when keep-open flips back, so end it ourselves and hand back to the caller.
internal fun MPVActivity.endPlaybackIfParkedAtEof() {
    if (mpvGetPropertyBoolean("eof-reached") == true) {
        capturePlaybackResultSnapshot(updateCompletion = true)
        mpvCommand(arrayOf("quit"))
    }
}

internal fun MPVActivity.keepPlaybackForDialog(): StateRestoreCallback {
    // Only the outermost dialog records the real baseline; nested ones would capture the forced "yes".
    if (keepOpenDialogDepth == 0)
        keepOpenSavedValue = mpvGetPropertyString("keep-open")
    keepOpenDialogDepth++
    mpvSetPropertyBoolean("keep-open", true)
    var restored = false
    return {
        if (!restored) {
            restored = true
            keepOpenDialogDepth = (keepOpenDialogDepth - 1).coerceAtLeast(0)
            if (keepOpenDialogDepth == 0) {
                keepOpenSavedValue?.also { mpvSetPropertyString("keep-open", it) }
                endPlaybackIfParkedAtEof()
                // Start the screensaver idle countdown fresh from when the UI closed.
                noteScreensaverActivity()
            }
        }
    }
}

internal fun MPVActivity.pauseForDialog(): StateRestoreCallback {
    val useKeepOpen = when (noUIPauseMode) {
        "always" -> true
        "audio-only" -> isPlayingAudioOnly()
        else -> false // "never"
    }
    if (useKeepOpen) {
        // Don't pause, just set keep-open so mpv doesn't exit mid-dialog.
        return keepPlaybackForDialog()
    }

    val wasPlayerPaused = player.paused ?: true
    player.paused = true
    return {
        if (!wasPlayerPaused)
            player.paused = false
    }
}

// Controls-overlay autopause:
//   - autoPauseControlsOverlayEnabled (general): opt-in, any file.
//   - autoPauseHi10pEnabled (default on for Shield): Hi10p H.264, where SW
//     decode is too close to real-time to share with UI work.
internal fun MPVActivity.shouldAutoPauseForControlsOverlay(): Boolean {
    val hi10pCase = autoPauseHi10pEnabled &&
        player.isHi10pH264Video()
    return autoPauseControlsOverlayEnabled || hi10pCase
}

internal fun MPVActivity.maybeAutoPauseForControlsOverlay() {
    val alreadyPausedOrUnknown = player.paused != false
    val shouldPause = !controlsOverlayAutoPaused &&
        shouldAutoPauseForControlsOverlay() &&
        !alreadyPausedOrUnknown
    if (shouldPause) {
        controlsOverlayAutoPaused = true
        mpvSetPropertyBoolean("pause", true)
    }
}
