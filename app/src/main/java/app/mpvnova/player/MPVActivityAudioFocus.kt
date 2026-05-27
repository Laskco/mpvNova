package app.mpvnova.player

import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/** Audio focus + dialog pause + controls-overlay autopause. */

/**
 * Request/abandon audio focus + noisy receiver based on playback state.
 * @warning Call from event thread, not UI thread.
 */
internal fun MPVActivity.handleAudioFocus() {
    if ((psc.pause && !psc.cachePause) || !isPlayingAudio) {
        if (becomingNoisyReceiverRegistered)
            unregisterReceiver(becomingNoisyReceiver)
        becomingNoisyReceiverRegistered = false
    } else {
        if (!becomingNoisyReceiverRegistered)
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        becomingNoisyReceiverRegistered = true
        // Re-requests on every unpause — see discussion in #1066.
        if (requestAudioFocus()) {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, "request")
        } else {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "request")
        }
    }
}

internal fun MPVActivity.requestAudioFocus(): Boolean {
    val manager = audioManager
    val req = audioFocusRequest ?:
        with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
        setAudioAttributes(with(AudioAttributesCompat.Builder()) {
            // libmpv's ao_audiotrack may differ — here we always pretend to be music.
            setUsage(AudioAttributesCompat.USAGE_MEDIA)
            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            build()
        })
        setOnAudioFocusChangeListener {
            onAudioFocusChange(it, "callback")
        }
        build()
    }
    val res = manager?.let { AudioManagerCompat.requestAudioFocus(it, req) }
    return if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        audioFocusRequest = req
        true
    } else {
        false
    }
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

internal fun MPVActivity.keepPlaybackForDialog(): StateRestoreCallback {
    val oldValue = mpvGetPropertyString("keep-open")
    mpvSetPropertyBoolean("keep-open", true)
    return {
        oldValue?.also { mpvSetPropertyString("keep-open", it) }
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
//   - autoPauseShieldHi10pEnabled (default on): Shield Hi10p H.264, where SW
//     decode is too close to real-time to share with UI work.
internal fun MPVActivity.shouldAutoPauseForControlsOverlay(): Boolean {
    val shieldHi10pCase = autoPauseShieldHi10pEnabled &&
        isNvidiaShieldDevice() &&
        player.isHi10pH264Video()
    return autoPauseControlsOverlayEnabled || shieldHi10pCase
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
