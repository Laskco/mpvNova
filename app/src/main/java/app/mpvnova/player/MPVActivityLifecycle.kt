package app.mpvnova.player

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.util.Log
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.AudioManagerCompat

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

internal fun MPVActivity.setupImmersiveWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    // Drop the PiP icon on devices without the feature (Fire TV, older AOSP).
    if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
        binding.topPiPBtn.visibility = View.GONE
}

internal fun MPVActivity.startPlayerForFile(filepath: String) {
    player.addObserver(mpvEventObserver)
    addMpvLogObserver(mpvLogObserver)
    player.initialize(filesDir.path, cacheDir.path)
    applySavedAudioFilterDefaults()
    applySavedSubFilterDefaults()
    prepareStreamLoading(filepath)
    player.playFile(filepath)
    mediaSession = initMediaSession()
    updateMediaSessionNow()
    BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken
    setupAudioSessionId()
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
    periodicSaveHandler.removeCallbacks(periodicSaveRunnable)
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    eventUiHandler.removeCallbacks(timePosUiRunnable)
    eventUiHandler.removeCallbacks(metadataUiRunnable)
    eventUiHandler.removeCallbacks(mediaSessionUpdateRunnable)
    eventUiHandler.removeCallbacks(shieldFallbackResyncRunnable)
    timePosUiPending = false
    metadataUiPending = false
    mediaSessionUpdatePending = false
}

/** onDestroy: release the media session and abandon audio focus. */
internal fun MPVActivity.releaseMediaAndAudioFocus() {
    BackgroundPlaybackService.mediaToken = null
    mediaSession?.let {
        it.isActive = false
        it.release()
    }
    mediaSession = null
    audioFocusRequest?.let { request ->
        audioManager?.let { manager ->
            AudioManagerCompat.abandonAudioFocusRequest(manager, request)
        }
    }
    audioFocusRequest = null
}

/** onNewIntent: foreground replacement path — refresh resume source + extras. */
internal fun MPVActivity.applyNewIntentReplacement(intent: Intent, filepath: String, nextResumeSource: String?) {
    currentResumeSource = nextResumeSource
    prepareMediaTitleFromIntent(intent, filepath)
    parseIntentExtras(intent.extras)
}
