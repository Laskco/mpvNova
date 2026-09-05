package app.mpvnova.player

import android.content.Context
import android.content.res.Configuration
import android.media.AudioFocusRequest
import android.os.Build
import androidx.annotation.RequiresApi
import android.media.AudioManager
import android.util.Log
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Lifecycle-stage helpers for [MPVActivity] — keeps the override bodies short. */

internal fun MPVActivity.setupRootView() {
    binding = app.mpvnova.player.databinding.PlayerBinding.inflate(layoutInflater)
    setContentView(binding.root)
    // Block a11y subtree walks for the bottom controls only — setSelected
    // fires TYPE_VIEW_SELECTED there on every dpad press and Shield's TV
    // launcher a11y service does a main-thread walk per event.
    binding.controls.importantForAccessibility =
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    isTvUiMode = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    hideControls()
}

@Suppress("DEPRECATION")
internal fun MPVActivity.suppressPlayerActivityTransition() {
    window.setWindowAnimations(0)
    overridePendingTransition(0, 0)
}

internal fun MPVActivity.setupImmersiveWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    // Drop the PiP icon on devices without the feature (Fire TV, older AOSP), and for external
    // launches where PiP conflicts with the caller's trampoline handoff and traps remote input.
    if (!isPictureInPictureActionAvailable())
        binding.topPiPBtn.visibility = View.GONE
}

internal fun MPVActivity.startPlayerForFile(filepath: String) {
    val initialized = player.initialize(filesDir.path, cacheDir.path) {
        player.addObserver(mpvEventObserver)
        addMpvLogObserver(mpvLogObserver)
        applyInitialAudioFilterDefaults()
    }
    if (!initialized) {
        Log.e(MPV_ACTIVITY_TAG, "Another screen already owns the libmpv runtime")
        showToast(getString(R.string.error_player_already_active))
        finishWithResult(RESULT_CANCELED)
        return
    }
    applySavedSubFilterDefaults()
    applySavedDelayDefaults()
    prepareStreamLoading(filepath)
    prepareDecoderForFileLoad(filepath)
    player.playFile(filepath)
    mediaSession = initMediaSession()
    updateMediaSessionNow()
    with(BackgroundPlaybackService) {
        mediaToken = mediaSession?.sessionToken
        thumbnailChanged = { updateMediaSessionNow() }
    }
    setupAudioSessionId()
    registerBluetoothAudioDelayWatcher()
    volumeControlStream = STREAM_TYPE
}

private fun MPVActivity.setupAudioSessionId() {
    val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager = manager
    val audioSessionId = manager.generateAudioSessionId()
    if (audioSessionId != AudioManager.ERROR)
        player.setAudioSessionId(audioSessionId)
    else
        Log.w(MPV_ACTIVITY_TAG, "AudioManager.generateAudioSessionId() returned error")
}

/** onDestroy: drop scheduled callbacks + clear coalescing flags. */
internal fun MPVActivity.cancelAllScheduledWork() {
    cancelPendingDpadLongClick()
    binding.controls.removeCallbacks(subtitleControlsPositionRunnable)
    periodicSaveHandler.removeCallbacks(periodicSaveRunnable)
    eventUiHandler.removeCallbacksAndMessages(null)
    fadeHandler.removeCallbacksAndMessages(null)
    // Self-reposting — if controls are visible at destroy it would otherwise
    // tick (and pin the activity) for the rest of the process lifetime.
    clockHandler.removeCallbacks(clockRunnable)
    stopServiceHandler.removeCallbacks(stopServiceRunnable)
    timePosUiPending = false
    metadataUiPending = false
    mediaSessionUpdatePending = false
}

/** onDestroy: release the media session and abandon audio focus. */
internal fun MPVActivity.releaseMediaAndAudioFocus() {
    if (MpvRuntimeOwnership.isOwnedBy(player)) {
        with(BackgroundPlaybackService) {
            mediaToken = null
            thumbnailChanged = null
            thumbnail = null
        }
    }
    mediaSession?.let {
        it.isActive = false
        it.release()
    }
    mediaSession = null
    audioManager?.let { manager -> abandonAudioFocus(manager) }
    unregisterBluetoothAudioDelayWatcher()
    audioFocusRequest = null
}

private fun MPVActivity.abandonAudioFocus(manager: AudioManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { abandonAudioFocusModern(manager, it) }
    } else {
        abandonAudioFocusLegacy(manager)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun abandonAudioFocusModern(manager: AudioManager, request: AudioFocusRequest) {
    manager.abandonAudioFocusRequest(request)
}

@Suppress("DEPRECATION")
private fun MPVActivity.abandonAudioFocusLegacy(manager: AudioManager) {
    manager.abandonAudioFocus(audioFocusChangeListener)
}
