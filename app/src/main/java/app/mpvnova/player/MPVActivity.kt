package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat

typealias ActivityResultCallback = (Int, Intent?) -> Unit
typealias StateRestoreCallback = () -> Unit

class MPVActivity : AppCompatActivity() {
    internal val eventUiHandler = Handler(Looper.getMainLooper())
    internal val fadeHandler = Handler(Looper.getMainLooper())
    internal val stopServiceHandler = Handler(Looper.getMainLooper())
    internal val clockHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveRunnable = object : Runnable {
        override fun run() {
            // Both writes no-op when there's nothing to save.
            savePosition()
            saveResumePosition()
            periodicSaveHandler.postDelayed(this, PERIODIC_SAVE_INTERVAL_MS)
        }
    }
    // 0 = no restore happened. Drives the "Resumed from X:XX" toast.
    internal var pendingResumeToastMs = 0L
    // Start position from intent/resume table — rechecked at FILE_LOADED
    // for near-end positions that slipped through parseIntentExtras.
    internal var pendingStartPositionMs = 0L
    // Don't read Activity.intent for resume saves — onNewIntent can swap
    // files while the Activity stays alive.
    internal var currentResumeSource: String? = null

    internal var activityIsStopped = false

    internal var activityIsForeground = true
    internal var didResumeBackgroundPlayback = false
    internal var userIsOperatingSeekbar = false
    internal var pendingSeekbarSeekMs: Long? = null
    internal var pendingDpadSeekPreviewMs: Long? = null
    internal var lastDisplayedPlaybackSecond = Int.MIN_VALUE
    internal var lastSeekbarProgress = Int.MIN_VALUE
    internal var lastSeekbarUiUpdateMs = 0L
    internal var lastDpadSeekApplyMs = 0L
    internal var lastAppliedSeekMs = Long.MIN_VALUE
    internal var lastClockInfoTick = Long.MIN_VALUE
    internal var lastDisplayedSpeed = Float.NaN
    @DrawableRes
    internal var lastPlayButtonIconRes = 0

    // Coalesce ~60/sec time-pos bursts into one UI hop.
    @Volatile internal var timePosUiPending = false
    internal val timePosUiRunnable = Runnable {
        timePosUiPending = false
        if (!activityIsForeground) return@Runnable
        if (binding.controls.visibility != View.VISIBLE) return@Runnable
        if (!userIsOperatingSeekbar && pendingSeekbarSeekMs == null && pendingDpadSeekPreviewMs == null)
            updatePlaybackTimeline(psc.position)
    }

    // TV/leanback mode — system-bar calls are no-ops but hitch the decoder.
    internal var isTvUiMode = false

    // Coalesce metadata bursts at file-load into one UI refresh.
    @Volatile internal var metadataUiPending = false
    internal val metadataUiRunnable = Runnable {
        metadataUiPending = false
        if (!activityIsForeground) return@Runnable
        updateMetadataDisplay()
    }

    // Coalesce MediaSession writes (each one ships a Parcel via IPC).
    @Volatile internal var mediaSessionUpdatePending = false
    internal val mediaSessionUpdateRunnable = Runnable {
        mediaSessionUpdatePending = false
        updateMediaSessionNow()
    }

    // Shield Hi10p decoder swap: pause, wait for playback-restart, exact-seek to realign.
    internal var pendingShieldFallbackResync = false
    internal var shieldFallbackResumeAfter = false
    internal val shieldFallbackResyncRunnable = Runnable {
        if (!activityIsForeground && !didResumeBackgroundPlayback) return@Runnable
        val pos = mpvGetPropertyDouble("time-pos/full") ?: return@Runnable
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: realigning A/V at $pos")
        mpvCommand(arrayOf("seek", pos.toString(), "absolute+exact"))
        if (shieldFallbackResumeAfter) {
            shieldFallbackResumeAfter = false
            mpvSetPropertyBoolean("pause", false)
        }
    }

    internal var audioManager: AudioManager? = null
    internal var audioFocusRequest: AudioFocusRequestCompat? = null
    internal var audioFocusRestore: () -> Unit = {}

    internal val psc = Utils.PlaybackStateCache()
    internal var mediaSession: MediaSessionCompat? = null

    internal lateinit var binding: PlayerBinding
    internal val lifecycleObserver = MpvActivityLifecycleObserver(this)
    internal val mpvEventObserver = MpvActivityEventObserver(this)
    internal val mpvLogObserver = MpvActivityLogObserver(this)

    internal val player get() = binding.player

    internal val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser)
                return
            val positionMs = millisFromSeekbarProgress(progress)
            scheduleSeekbarSeek(positionMs)
            updatePlaybackTimeline(positionMs, forceTextUpdate = true)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = false
            commitPendingSeekbarSeek()
            showControls() // re-trigger display timeout
        }
    }

    internal val commitSeekbarSeekRunnable = Runnable {
        commitPendingSeekbarSeek()
    }

    internal var becomingNoisyReceiverRegistered = false
    internal val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "noisy")
            }
        }
    }

    internal val fadeRunnable: ControlsFadeRunnable = object : ControlsFadeRunnable() {
        override var hasStarted = false
        private val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) { hasStarted = true }

            override fun onAnimationCancel(animation: Animator) { hasStarted = false }

            override fun onAnimationEnd(animation: Animator) {
                if (hasStarted)
                    hideControls()
                hasStarted = false
            }
        }

        // Accelerate-out so overlay doesn't linger at half-opacity.
        private val accelerate = androidx.interpolator.view.animation.FastOutLinearInInterpolator()

        override fun run() {
            // All overlays share duration + curve + withLayer() → one GPU texture.
            binding.topControls.animate()
                .alpha(0f).setDuration(CONTROLS_FADE_DURATION).setInterpolator(accelerate).withLayer()
            binding.playerTitleOverlay.animate()
                .alpha(0f).setDuration(CONTROLS_FADE_DURATION).setInterpolator(accelerate).withLayer()
            binding.controlsScrim.animate()
                .alpha(0f).setDuration(CONTROLS_FADE_DURATION).setInterpolator(accelerate).withLayer()
            binding.timeInfoPanel.animate()
                .alpha(0f).setDuration(CONTROLS_FADE_DURATION).setInterpolator(accelerate).withLayer()
            binding.statsTextView.animate()
                .alpha(0f).setDuration(CONTROLS_FADE_DURATION).setInterpolator(accelerate).withLayer()
            // Main bar drives the listener so hideControls() fires once.
            binding.controls.animate()
                .alpha(0f)
                .setDuration(CONTROLS_FADE_DURATION)
                .setInterpolator(accelerate)
                .setListener(listener)
                .withLayer()
        }
    }

    internal val playerToastHideRunnable = Runnable {
        binding.playerToast.animate()
            .alpha(0f)
            .setDuration(PLAYER_TOAST_FADE_OUT_MS)
            .withLayer()
            .withEndAction { binding.playerToast.visibility = View.GONE }
    }

    internal val stopServiceRunnable = Runnable {
        val intent = Intent(this, BackgroundPlaybackService::class.java)
        applicationContext.stopService(intent)
    }

    internal val clockRunnable = object : Runnable {
        override fun run() {
            updateClockInfo()
            val now = System.currentTimeMillis()
            val delay = CLOCK_TICK_INTERVAL_MS - (now % CLOCK_TICK_INTERVAL_MS)
            clockHandler.postDelayed(this, delay.coerceAtLeast(MIN_CLOCK_TICK_DELAY_MS))
        }
    }

    internal var statsFPS = false
    internal var statsLuaMode = 0

    internal var backgroundPlayMode = ""
    internal var noUIPauseMode = ""

    internal var shouldSavePosition = false

    internal var controlsAtBottom = true
    internal var showMediaTitle = false
    internal var showClockOverlay = true
    internal var controlsDisplayTimeoutMs = DEFAULT_CONTROLS_DISPLAY_TIMEOUT
    internal var keepControlsVisibleWhilePaused = false
    internal var exitWithDoubleBack = false
    internal var lastBackPressMs = 0L
    internal var autoRefreshRateSwitch = false
    internal var dpadUpJumpsToTopControls = false
    // Drawer state: remembered tab, reopen-after-subdialog flag, cached binding.
    internal var lastDrawerTab: DrawerTab = DrawerTab.VIDEO
    internal var drawerReopenPending = false
    internal var drawerBinding: app.mpvnova.player.databinding.DialogPlayerDrawerBinding? = null
    internal var drawerHandlersBound = false
    internal var currentDrawerDialog: androidx.appcompat.app.AlertDialog? = null
    internal var remoteNextChapterKeyCode: Int? = null
    internal var playerScreenBrightnessActive = false
    internal var rememberPlayerScreenBrightness = false
    internal var playerScreenBrightnessPercent = DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    internal var rememberVideoContrast = false
    internal var videoContrastValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoGamma = false
    internal var videoGammaValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoSaturation = false
    internal var videoSaturationValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var useTimeRemaining = false
    internal var pendingItemTitle: String? = null
    internal var pendingFileName: String? = null
    internal var currentItemTitle: String? = null
    internal var currentVideoTitle: String? = null
    internal var cachedActiveFilterColor: Int? = null

    internal var ignoreAudioFocus = false
    internal var playlistExitWarning = true
    internal var newIntentReplace = false

    internal var persistAudioFilters = false
    internal var persistSubFilters = false
    // subScaleSteps index; default=1.0 at index 3
    internal var subScaleLevel = DEFAULT_SUB_SCALE_INDEX
    // subPosSteps index; default=100% at index 25 (the array spans -25%..125%)
    internal var subPosLevel = DEFAULT_SUB_POSITION_INDEX
    // secondaryPosSteps index; default=0% at index 5
    internal var secondaryPosLevel = DEFAULT_SECONDARY_SUB_POSITION_INDEX
    internal var sessionDecoderMode: String? = null
    internal var autoDecoderFallback = true
    internal var shieldDecoderModeEnabled = true
    internal var shieldDecoderFallback = MPVView.SHIELD_DECODER_FALLBACK_COPY
    internal var preferredDecoderMode = ""
    // Autopause: pause while controls overlay is visible. Shield variant
    // defaults on (Hi10p SW can't share CPU with the UI).
    // controlsOverlayAutoPaused = we paused (vs user) → safe to auto-resume.
    internal var autoPauseControlsOverlayEnabled = false
    internal var autoPauseShieldHi10pEnabled = true
    internal var controlsOverlayAutoPaused = false
    internal var audioNormUnderrunHintShown = false
    internal var gpuNextRenderFallbackStage = 0
    internal var gpuNextCopyRetryConfirmed = false
    internal var gpuNextCopyRetryDisplayedFrame = false
    // Sustained-error window for gpu-next — a single transient libplacebo
    // log line must not trip the renderer fallback (that rebuilds the VO
    // mid-playback and desyncs A/V/subs).
    internal var gpuNextErrorWindowStartMs = 0L
    internal var gpuNextErrorWindowCount = 0


    internal var playbackHasStarted = false
    // True once mpv reports END_FILE. Stremio's mpv parser treats OK without
    // position/duration extras as completed playback.
    internal var eofWasReached = false
    internal var onloadCommands = mutableListOf<Array<String>>()
    internal var streamOpenLoading = false
    internal var streamCacheLoading = false
    internal var cachedChapters: List<MPVView.Chapter> = emptyList()
    internal var pendingChapterSeekTime: Double? = null
    internal val clearPendingChapterSeek = Runnable { pendingChapterSeekTime = null }

    // Activity lifetime

    override fun onCreate(icicle: Bundle?) {
        AppearanceTheme.applyPlayer(this)
        super.onCreate(icicle)
        lifecycle.addObserver(lifecycleObserver)

        // Launched directly from a file browser → re-run MainActivity's one-time setup.
        Utils.copyAssets(this)
        createBackgroundPlaybackNotificationChannel(this)

        setupRootView()
        initListeners()
        readSettings()
        applyPlayerScreenBrightnessPreference()
        onConfigurationChanged(resources.configuration)
        setupImmersiveWindow()

        // Drop stale resume entries before adding ours.
        pruneResumeTable()
        // Both saves no-op when there's nothing to persist — safe to arm immediately.
        periodicSaveHandler.postDelayed(periodicSaveRunnable, PERIODIC_SAVE_INTERVAL_MS)

        val filepath = parsePathFromIntent(intent)
        currentResumeSource = resumeSourceFromIntent(intent, filepath)
        prepareMediaTitleFromIntent(intent, filepath)
        if (intent.action == Intent.ACTION_VIEW) {
            parseIntentExtras(intent.extras)
        }
        addAutomaticSubtitleOptions(filepath)

        if (filepath == null) {
            Log.e(MPV_ACTIVITY_TAG, "No file given, exiting")
            showToast(getString(R.string.error_no_file))
            finishWithResult(RESULT_CANCELED)
            return
        }
        startPlayerForFile(filepath)
    }


    override fun onDestroy() {
        Log.v(MPV_ACTIVITY_TAG, "Exiting.")
        activityIsForeground = false
        cancelAllScheduledWork()
        if (becomingNoisyReceiverRegistered) {
            unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
        }
        releaseMediaAndAudioFocus()
        stopServiceRunnable.run()
        player.removeObserver(mpvEventObserver)
        removeMpvLogObserver(mpvLogObserver)
        player.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        Log.v(MPV_ACTIVITY_TAG, "onNewIntent($intent)")
        super.onNewIntent(intent)
        if (intent != null)
            setIntent(intent)
        pendingResumeToastMs = 0L

        val filepath = intent?.let { parsePathFromIntent(it) }
        if (filepath == null) {
            return
        }
        resetPlaybackResultState()
        val nextResumeSource = resumeSourceFromIntent(intent, filepath)
        val willReplaceCurrentFile = activityIsForeground || !didResumeBackgroundPlayback || this.newIntentReplace
        if (willReplaceCurrentFile) {
            applyNewIntentReplacement(intent, filepath, nextResumeSource)
        } else {
            onloadCommands.clear()
        }
        addAutomaticSubtitleOptions(filepath)

        if (!activityIsForeground && didResumeBackgroundPlayback) {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            prepareStreamLoading(filepath)
            if (this.newIntentReplace) {
                mpvCommand(arrayOf("loadfile", filepath, "replace"))
                showToast(getString(R.string.notice_file_play))
            } else {
                mpvCommand(arrayOf("loadfile", filepath, "append"))
                showToast(getString(R.string.notice_file_appended))
            }
            moveTaskToBack(true)
        } else {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            prepareStreamLoading(filepath)
            mpvCommand(arrayOf("loadfile", filepath))
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInPictureInPictureMode) {
                Log.v(MPV_ACTIVITY_TAG, "Playback continuing in picture-in-picture")
                super.onPause()
                return
            }
        }

        onPauseImpl()
    }

    internal fun onPauseImpl() {
        val fmt = mpvGetPropertyString("video-format")
        val shouldBackground = shouldBackground()
        if (shouldBackground && !fmt.isNullOrEmpty())
            BackgroundPlaybackService.thumbnail = mpvGrabThumbnail(THUMB_SIZE)
        else
            BackgroundPlaybackService.thumbnail = null
        // Flush synchronously — handler queue gets purged below.
        updateMediaSessionNow()

        activityIsForeground = false
        eventUiHandler.removeCallbacksAndMessages(null)
        timePosUiPending = false
        metadataUiPending = false
        mediaSessionUpdatePending = false
        if (isFinishing) {
            savePosition()
            saveResumePosition()
            // Shut mpv down so further property changes are ignored.
            mpvCommand(arrayOf("stop"))
        } else if (!shouldBackground) {
            player.paused = true
        }
        writeSettings()
        super.onPause()

        didResumeBackgroundPlayback = shouldBackground
        if (shouldBackground) {
            Log.v(MPV_ACTIVITY_TAG, "Resuming playback in background")
            stopServiceHandler.removeCallbacks(stopServiceRunnable)
            val serviceIntent = Intent(this, BackgroundPlaybackService::class.java)
            if (!tryStartForegroundService(serviceIntent)) {
                didResumeBackgroundPlayback = false
                player.paused = true
            }
        }
    }

    override fun onResume() {
        // Never left foreground → skip reinit.
        if (activityIsForeground) {
            super.onResume()
            return
        }

        hideControls()
        readSettings()
        applyPlayerScreenBrightnessPreference()

        activityIsForeground = true
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, BACKGROUND_SERVICE_STOP_DELAY_MS)

        refreshUi()

        super.onResume()
    }

    // UI

    /** dpad navigation */
    internal var btnSelected = -1
    internal val dpadControlsScratch = ArrayList<View>(DPAD_CONTROLS_SCRATCH_CAPACITY)
    internal var pendingDpadLongClickView: View? = null
    internal var pendingDpadLongClickRunnable: Runnable? = null
    internal var dpadLongClickPerformed = false

    internal var mightWantToToggleControls = false

    /** true if we're actually outputting any audio (includes the mute state, but not pausing) */
    internal var isPlayingAudio = false

    internal var useAudioUI = false

    internal var clockFormatter: SimpleDateFormat? = null
    internal var clockFormatterIs24: Boolean? = null

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        // Built-in handlers first; forward the rest to libmpv.
        val handled = interceptDpad(ev) ||
            interceptRemoteNextChapterButton(ev) ||
            (ev.action == KeyEvent.ACTION_DOWN && interceptKeyDown(ev)) ||
            player.onKey(ev)
        return handled || super.dispatchKeyEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        binding.controls.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (!controlsAtBottom) {
                Utils.convertDp(this@MPVActivity, FLOATING_CONTROLS_BOTTOM_MARGIN_DP)
            } else {
                0
            }
            leftMargin = if (!controlsAtBottom) {
                Utils.convertDp(
                    this@MPVActivity,
                    FLOATING_CONTROLS_SIDE_MARGIN_LANDSCAPE_DP
                )
            } else {
                0
            }
            rightMargin = leftMargin
        }
    }

    // Audio filter levels. DRC is mutually exclusive with audio-norm so the
    // UI matches the active chain. Preset arrays live in AudioFilterPresets.kt.
    internal var voiceBoostLevel = 0
    internal var volumeBoostDb = 0
    internal var nightModeLevel = 0
    internal var audioNormLevel = 0
    internal var downmixLevel = 0

    // Subtitle filter state. subPosSteps spans -25..125% in 5% steps so the
    // user can click past edges without focus bouncing (mpv soft-clamps).
    internal val subScaleSteps = SUB_SCALE_STEPS
    internal val subPosSteps = SUB_POSITION_STEPS
    internal val secondaryPosSteps = subPosSteps

    internal var pendingActivityResultCallback: ActivityResultCallback? = null
    internal val filePickerResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingActivityResultCallback?.invoke(it.resultCode, it.data)
            pendingActivityResultCallback = null
        }
    internal val documentResultLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val result = uri?.let { Intent().putExtra("path", it.toString()) }
            pendingActivityResultCallback?.invoke(
                if (uri != null) RESULT_OK else RESULT_CANCELED,
                result
            )
            pendingActivityResultCallback = null
        }

    internal val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPause() {
            player.paused = true
        }
        override fun onPlay() {
            player.paused = false
        }
        override fun onSeekTo(pos: Long) {
            player.timePos = (pos / MPV_MILLIS_PER_SECOND_DOUBLE)
        }
        override fun onSkipToNext() = playlistNext()
        override fun onSkipToPrevious() = playlistPrev()
        override fun onSetRepeatMode(repeatMode: Int) {
            mpvSetPropertyString("loop-playlist",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no")
            mpvSetPropertyString("loop-file",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no")
        }
        override fun onSetShuffleMode(shuffleMode: Int) {
            player.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
        }
    }
}
